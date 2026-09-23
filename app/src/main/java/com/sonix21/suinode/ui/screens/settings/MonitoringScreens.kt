package com.sonix21.suinode.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.*
import com.sonix21.suinode.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun MonitoringScreen(nav: NavController) {
    val context = LocalContext.current
    val store = remember { PanelStore(context.applicationContext) }
    val runner = rememberRunner()
    val panels by Panels.list.collectAsState()
    var config by remember { mutableStateOf<MonitorConfig?>(null) }
    var state by remember { mutableStateOf(JSONObject()) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permissionTick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun refresh() = runner.go {
        withContext(Dispatchers.IO) { store.monitorConfig() to store.monitorState() }.let { (c, s) -> config = c; state = s }
    }
    LaunchedEffect(Unit) { refresh() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val c = config
    if (c == null) { RecordLoadingPage("Monitoring", nav, runner) { refresh() }; return }
    fun openSettings(action: String, packageUri: Boolean = false) {
        runCatching { context.startActivity(Intent(action).apply { if (packageUri) data = Uri.parse("package:${context.packageName}") }) }
            .onFailure { ToastBus.show("This settings page is unavailable. Open your device's app settings manually.") }
    }
    PageScaffold("Monitoring & alerts", nav, busy = runner.busy, draftValue = { config?.toJson() }, primaryAction = {
        HeaderPrimaryAction("Save monitoring settings") { runner.go {
            withContext(Dispatchers.IO) { store.saveMonitorConfig(c) }
            MonitoringScheduler.configure(context, c)
            DraftRegistry.saved()
            ToastBus.show(if (c.enabled) "Read-only background monitoring enabled" else "Background monitoring disabled")
        } }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchRow("Background monitoring", c.enabled, { config = c.copy(enabled = it) },
                    subtitle = "Opt in to read-only APIv2 checks while the app is closed. Save with the tick.")
                Text("Background checks use credentials from the device vault, including while the app's UI lock is closed. Android 15+ device-lock key restrictions can defer checks until unlock. No client, core or panel settings are changed.", color = LocalGlass.current.textFaint)
                if (c.enabled) {
                    MultiSelectField("Panels (empty means all saved panels)", c.panelIds, panels.map { Opt(it.name, it.id) },
                        { config = c.copy(panelIds = it) }, searchable = true)
                    SelectField("Check interval", c.intervalMinutes, listOf(15L, 30L, 60L, 180L, 360L, 720L, 1440L).map { Opt("$it minutes", it) },
                        clearable = false, onChange = { it?.let { config = c.copy(intervalMinutes = it) } })
                    SwitchRow("Expiry alerts", c.expiry, { config = c.copy(expiry = it) })
                    if (c.expiry) NumberField("Warn before expiry", c.expiryHours, suffix = "hours", onChange = { config = c.copy(expiryHours = (it ?: 72).coerceIn(1, 2160)) })
                    SwitchRow("Traffic quota alerts", c.quota, { config = c.copy(quota = it) })
                    if (c.quota) NumberField("Warn at quota used", c.quotaPercent.toLong(), suffix = "%", onChange = { config = c.copy(quotaPercent = (it ?: 90).coerceIn(1, 100).toInt()) })
                    SwitchRow("Include disabled clients", c.includeDisabled, { config = c.copy(includeDisabled = it) }, subtitle = "Includes clients the panel automatically disabled after expiry/quota exhaustion")
                    SwitchRow("Panel unavailable alerts", c.offline, { config = c.copy(offline = it) })
                    if (c.offline) NumberField("Consecutive failed checks", c.failedChecks.toLong(), onChange = { config = c.copy(failedChecks = (it ?: 3).coerceIn(1, 10).toInt()) })
                    SwitchRow("Core stopped alerts", c.coreStopped, { config = c.copy(coreStopped = it) })
                    SwitchRow("Recovery notifications", c.recovery, { config = c.copy(recovery = it) })
                    NumberField("Repeat unresolved alerts", c.repeatHours, suffix = "hours · 0 = once", onChange = { config = c.copy(repeatHours = (it ?: 24).coerceIn(0, 168)) })
                    SwitchRow("Quiet hours", c.quiet, { config = c.copy(quiet = it) })
                    if (c.quiet) {
                        NumberField("Quiet from", c.quietStart.toLong(), suffix = "hour (0–23)", onChange = { config = c.copy(quietStart = (it ?: 22).coerceIn(0, 23).toInt()) })
                        NumberField("Quiet until", c.quietEnd.toLong(), suffix = "hour (0–23)", onChange = { config = c.copy(quietEnd = (it ?: 8).coerceIn(0, 23).toInt()) })
                        Text("Uses this phone's local time. Equal hours disable the quiet window. Active alerts are delivered on a later check after quiet hours.", color = LocalGlass.current.textFaint)
                    }
                    SwitchRow("Show names in notifications", c.showNames, { config = c.copy(showNames = it) }, subtitle = "Off keeps panel/client names inside the protected app")
                }
                NumberField("Keep alert history", c.retentionDays.toLong(), suffix = "days (max 500 events)", onChange = { config = c.copy(retentionDays = (it ?: 30).coerceIn(1, 90).toInt()) })
                GhostButton("Alert history") { nav.push(Route.AlertHistory) }
                panels.forEach { panel -> GhostButton("VPS traffic quota · ${panel.name}") { nav.push(Route.VpsQuota(panel.id)) } }
                panels.forEach { panel -> GhostButton("VPS renewal reminder · ${panel.name}") { nav.push(Route.VpsRenewal(panel.id)) } }
            } }
            GlassCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Permissions & background reliability")
                permissionTick
                Text(if (MonitoringScheduler.deliveryAllowed(context)) "Notifications permitted" else "Notifications are blocked or not yet permitted", color = LocalGlass.current.text)
                GhostButton("Enable notifications") {
                    MonitoringScheduler.createChannel(context)
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
                }
                GhostButton("Notification settings") {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                        .onFailure { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }
                }
                GhostButton("Battery optimization settings") { openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
                GhostButton("App & background settings") { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }
                Text("Checks are approximate, not real-time. Doze, battery saver, no network and OEM restrictions can delay them. For ${Build.MANUFACTURER}, review background battery limits and any Auto-start setting. Force-stopping the app pauses work until you reopen it. No notification-listener or exact-alarm access is requested.", color = LocalGlass.current.textFaint)
                Text("Last check: " + state.optLong("lastCheck").takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString() }.orEmpty().ifBlank { "Not run yet" }, color = LocalGlass.current.text)
                val checks = state.optJSONObject("checks")
                panels.forEach { p -> checks?.optJSONObject(p.id)?.let { Text("${p.name}: ${it.optString("note")}", color = LocalGlass.current.textFaint) } }
                GhostButton("Check now (saved settings)", enabled = MonitoringScheduler.enabled(context)) { MonitoringScheduler.checkNow(context); ToastBus.show("Read-only check queued; Android decides when it can run") }
                GhostButton("Refresh check status") { refresh() }
            } }
        }
    }
}

@Composable
fun AlertHistoryScreen(nav: NavController) {
    val context = LocalContext.current
    val runner = rememberRunner()
    val store = remember { PanelStore(context.applicationContext) }
    var state by remember { mutableStateOf(JSONObject()) }
    var clear by remember { mutableStateOf(false) }
    fun refresh() = runner.go { state = withContext(Dispatchers.IO) { store.monitorState() } }
    LaunchedEffect(Unit) { refresh() }
    if (clear) ConfirmDialog("Clear alert history", "Remove this device's encrypted alert history? Active alert deduplication is retained. The panel is not changed.",
        onConfirm = { runner.go { state = withContext(Dispatchers.IO) { store.updateMonitorState { it.put("history", JSONArray()) }; store.monitorState() } } }, onDismiss = { clear = false })
    PageScaffold("Alert history", nav, busy = runner.busy) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Refresh") { refresh() }
                GhostButton("Clear history") { clear = true }
            }
            val active = state.optJSONObject("active") ?: JSONObject()
            val history = state.optJSONArray("history")?.objList().orEmpty().asReversed()
            if (history.isEmpty()) Text("No alerts recorded. Monitoring is optional and starts only after you enable and save it.", color = LocalGlass.current.textFaint)
            history.forEach { record -> GlassCard { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(record.optString("title"), color = LocalGlass.current.text)
                Text(record.optString("detail"), color = LocalGlass.current.textFaint)
                val current = active.optJSONObject(record.optString("key"))
                val stillActive = !record.optBoolean("resolved") && current?.optLong("time") == record.optLong("time")
                Text((if (stillActive) "Active" else "Resolved / previous event") + " · " + java.time.Instant.ofEpochSecond(record.optLong("time")).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(), color = LocalGlass.current.teal)
            } } }
        }
    }
}
