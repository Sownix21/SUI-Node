package com.sonix21.suinode.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.sonix21.suinode.MainActivity
import com.sonix21.suinode.R
import com.sonix21.suinode.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object MonitoringScheduler {
    private const val WORK = "sui-node-monitor"
    private const val ONCE = "sui-node-monitor-now"
    private fun prefs(context: Context) = context.getSharedPreferences("monitor_schedule", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun configure(context: Context, config: MonitorConfig) {
        check(prefs(context).edit().putBoolean("enabled", config.enabled).putLong("minutes", config.intervalMinutes).commit())
        reconcile(context)
    }
    fun reconcile(context: Context) {
        val work = WorkManager.getInstance(context)
        if (!enabled(context)) {
            work.cancelUniqueWork(WORK); work.cancelUniqueWork(ONCE)
            return
        }
        val request = PeriodicWorkRequestBuilder<MonitorWorker>(prefs(context).getLong("minutes", 60).coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    fun checkNow(context: Context) {
        if (!enabled(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MonitorWorker>().setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()).build())
    }
    fun deliveryAllowed(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Panel and client alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Client expiry/quota, VPS traffic/renewal, availability and core-status alerts"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            })
    }
    fun notify(context: Context, alerts: List<JSONObject>, showNames: Boolean) {
        if (alerts.isEmpty() || !deliveryAllowed(context)) return
        val pending = PendingIntent.getActivity(context, 1001, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (alerts.size == 1) alerts[0].optString("title") else "${alerts.size} S-UI Node alerts"
        val detail = if (showNames) alerts.take(5).joinToString("\n") { it.optString("title") + ": " + it.optString("detail") }
            else "Open S-UI Node to review your private alert history."
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("S-UI Node").setContentText("Monitoring update").build()
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title).setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public).setCategory(NotificationCompat.CATEGORY_STATUS).build()
        try { NotificationManagerCompat.from(context).notify(1001, notification) } catch (_: SecurityException) { }
    }
    private const val CHANNEL = "sui_node_monitoring_v1"
}

class MonitorWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Serialize periodic/manual work without keeping credentials in WorkManager's database.
        monitorMutex.lock()
        try {
            if (!MonitoringScheduler.enabled(applicationContext)) return@withContext Result.success()
            val store = PanelStore(applicationContext)
            val config = store.monitorConfig()
            if (!config.enabled) return@withContext Result.success()
            val priorChecks = store.monitorState().optJSONObject("checks")
            val panels = store.load().filter { config.panelIds.isEmpty() || it.id in config.panelIds }
                .sortedBy { priorChecks?.optJSONObject(it.id)?.optLong("time") ?: 0L }
            store.updateMonitorState { AlertEngine.pruneScope(it, panels.map { p -> p.id }.toSet(), System.currentTimeMillis() / 1000, config) }
            // Oldest checks first so a large/slow fleet cannot starve panels at the end.
            val deadline = android.os.SystemClock.elapsedRealtime() + 7 * 60_000L
            MonitoringScheduler.createChannel(applicationContext)
            val notifications = mutableListOf<JSONObject>()
            val connectivity = applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
            val networkAvailable = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            for (panel in panels) {
                if (isStopped || !MonitoringScheduler.enabled(applicationContext) || android.os.SystemClock.elapsedRealtime() >= deadline) break
                val now = System.currentTimeMillis() / 1000
                val api = SuiClient(panel)
                val candidates = mutableListOf<AlertCandidate>()
                val known = mutableSetOf<String>()
                var connected = false
                var rejectedToken = false
                val vpsConfig = store.vpsConfig(panel.id)
                val renewal = store.vpsRenewal(panel.id)
                var vpsStatus: JSONObject? = null
                var note = "Checked"
                try {
                    if (!networkAvailable) throw java.io.IOException("Device offline")
                    val list = api.fullClients()
                    connected = true
                    candidates += ClientHealth.alerts(panel.id, panel.name, list, now, config)
                    known += setOf("expiry", "quota", "offline", "auth")
                } catch (e: CancellationException) { api.logout(); throw e }
                catch (_: RejectedApiToken) { rejectedToken = true; note = ApiTokenError.MESSAGE }
                catch (_: Exception) { note = if (networkAvailable) "Client data unavailable" else "Waiting for this device's network" }
                try {
                    if (networkAvailable && (config.coreStopped || vpsConfig.enabled)) {
                        val status = api.get("status", mapOf("r" to if (vpsConfig.enabled) "sbd,net,sys" else "sbd"))
                        if (!status.success && ApiTokenError.matches(status.msg)) throw RejectedApiToken()
                        if (status.success) {
                            if (!connected) note = "Server checked; client data unavailable"
                            connected = true; known += setOf("offline", "auth")
                        }
                        if (status.success && vpsConfig.enabled) vpsStatus = status.objObj()
                        val running = status.takeIf { it.success }?.objObj()?.optJSONObject("sbd")?.opt("running") as? Boolean
                        if (running != null) {
                            known += "core"
                            if (Panel161.coreStoppedUnexpectedly(requireNotNull(status.objObj()?.optJSONObject("sbd"))) && config.coreStopped) candidates += AlertCandidate("${panel.id}:core", panel.id, "core", "VPN core stopped",
                                "${panel.name} · panel reachable, core reports stopped", "stopped", 2)
                            if (status.objObj()?.optJSONObject("sbd")?.optBoolean("maintenance") == true) note = "Core stopped intentionally for maintenance"
                        } else if (config.coreStopped) note = if (connected) "Panel checked; core status unavailable" else "Client and core data unavailable"
                    } else if (!config.coreStopped) known += "core"
                } catch (e: CancellationException) { throw e }
                catch (_: RejectedApiToken) { rejectedToken = true; note = ApiTokenError.MESSAGE }
                catch (_: Exception) { note = if (connected) "Clients checked; core status unavailable" else "APIv2 unavailable from this device" }
                finally { api.logout() }
                store.updateMonitorState { state ->
                    val checks = state.optJSONObject("checks") ?: JSONObject().also { state.put("checks", it) }
                    val previousFailures = checks.optJSONObject(panel.id)?.optInt("failures") ?: 0
                    val failures = if (connected) 0 else previousFailures + if (networkAvailable) 1 else 0
                    checks.put(panel.id, jo("time" to now, "failures" to failures.coerceAtMost(100), "note" to note))
                    renewal.alert(panel.id, panel.name, now)?.let { candidates += it }
                    known += "billing"
                    if (rejectedToken) {
                        known += "auth"
                        candidates += AlertCandidate("${panel.id}:auth", panel.id, "auth", "Panel token invalid or expired",
                            "${panel.name} · renew the API token in the web panel and update this profile", "rejected", 2)
                    }
                    val net = vpsStatus?.optJSONObject("net")
                    if (vpsConfig.enabled && net?.opt("recv") is Number && net.opt("sent") is Number) {
                        val usageMap = state.optJSONObject("vps") ?: JSONObject().also { state.put("vps", it) }
                        val usage = VpsQuota.sample(usageMap.optJSONObject(panel.id), vpsConfig, now,
                            net.optLong("recv"), net.optLong("sent"), vpsStatus?.optJSONObject("sys")?.optLong("bootTime") ?: 0)
                        usageMap.put(panel.id, usage)
                        candidates += VpsQuota.alerts(panel.id, panel.name, usage, vpsConfig)
                        known += "vps"
                    } else if (!vpsConfig.enabled) known += "vps"
                    if (networkAvailable && !connected && !rejectedToken && config.offline && failures >= config.failedChecks) {
                        known += "offline"
                        candidates += AlertCandidate("${panel.id}:offline", panel.id, "offline", "Panel unavailable",
                            "${panel.name} · $failures consecutive failed checks from this device", "offline", 2)
                    }
                    if (!config.offline) known += "offline"
                    val delivery = !config.isQuiet(now) && MonitoringScheduler.deliveryAllowed(applicationContext)
                    notifications += AlertEngine.update(state, panel.id, candidates, known, now, config, delivery)
                    state.put("lastCheck", now)
                }
            }
            if (MonitoringScheduler.enabled(applicationContext)) MonitoringScheduler.notify(applicationContext, notifications, config.showNames)
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
        finally { monitorMutex.unlock() }
    }
    private companion object { val monitorMutex = kotlinx.coroutines.sync.Mutex() }
}
