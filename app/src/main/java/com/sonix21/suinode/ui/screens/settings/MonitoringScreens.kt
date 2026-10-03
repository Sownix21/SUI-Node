package com.sonix21.suinode.ui.screens.settings

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        withContext(Dispatchers.IO) { store.monitorConfig() to store.monitorState() }.let { (c, s) ->
            if (config == null) config = c
            state = s
        }
    }
    LaunchedEffect(Unit) { refresh() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val c = config
    if (c == null) { RecordLoadingPage("Monitoring", nav, runner) { refresh() }; return }
    var expiryUnit by remember { mutableStateOf(if (c.expiryHours % 24 == 0L) "days" else "hours") }
    fun openSettings(action: String, packageUri: Boolean = false) {
        runCatching { context.startActivity(Intent(action).apply { if (packageUri) data = "package:${context.packageName}".toUri() }) }
            .onFailure { ToastBus.show("This settings page is unavailable. Open your device's app settings manually.") }
    }
    PageScaffold("Monitoring & alerts", nav, busy = runner.busy, draftValue = { config?.toJson() }, primaryAction = {
        HeaderPrimaryAction("Save monitoring settings") { runner.go {
            withContext(Dispatchers.IO) { store.saveMonitorConfig(c) }
            withContext(Dispatchers.IO) { MonitoringScheduler.configure(context, c) }
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
                    SelectField("Check interval", c.intervalMinutes, listOf(15L, 30L, 60L, 120L, 180L, 240L, 360L, 480L, 720L, 1440L).map {
                        Opt(if (it < 60) "Every $it minutes" else if (it == 60L) "Every hour" else "Every ${it / 60} hours", it)
                    },
                        clearable = false, onChange = { it?.let { config = c.copy(intervalMinutes = it) } })
                    SwitchRow("Expiry alerts", c.expiry, { config = c.copy(expiry = it) })
                    if (c.expiry) {
                        SelectField("Expiry warning unit", expiryUnit, listOf(Opt("Days", "days"), Opt("Hours", "hours")), clearable = false,
                            onChange = { unit -> if (unit != null) {
                                expiryUnit = unit
                                if (unit == "days") config = c.copy(expiryHours = ((c.expiryHours + 23) / 24).coerceIn(1, 90) * 24)
                            } })
                        NumberField("Warn when remaining time is at most", if (expiryUnit == "days") c.expiryHours / 24 else c.expiryHours,
                            suffix = expiryUnit, onChange = { value ->
                                config = c.copy(expiryHours = if (expiryUnit == "days") (value ?: 3).coerceIn(1, 90) * 24 else (value ?: 72).coerceIn(1, 2160))
                            })
                    }
                    SwitchRow("Traffic quota alerts", c.quota, { config = c.copy(quota = it) })
                    if (c.quota) NumberField("Warn at quota used", c.quotaPercent.toLong(), suffix = "%", onChange = { config = c.copy(quotaPercent = (it ?: 90).coerceIn(1, 100).toInt()) })
                    SwitchRow("Include disabled clients", c.includeDisabled, { config = c.copy(includeDisabled = it) }, subtitle = "Includes clients the panel automatically disabled after expiry/quota exhaustion")
                    SwitchRow("Panel unavailable alerts", c.offline, { config = c.copy(offline = it) })
                    if (c.offline) NumberField("Consecutive failed checks", c.failedChecks.toLong(), onChange = { config = c.copy(failedChecks = (it ?: 3).coerceIn(1, 10).toInt()) })
                    SwitchRow("Core stopped alerts", c.coreStopped, { config = c.copy(coreStopped = it) })
                    SwitchRow("Recovery notifications", c.recovery, { config = c.copy(recovery = it) })
                    NumberField("Repeat unresolved alerts", c.repeatHours, suffix = "hours · 0 = once", onChange = { config = c.copy(repeatHours = (it ?: 24).coerceIn(0, 168)) })
                    Text("New alerts are detected at the check interval. Repeat controls reminders for conditions already reported; 0 means notify once until the condition resolves or worsens. A renewed client can still be inside your warning window, but an extension does not trigger another immediate warning.", color = LocalGlass.current.textFaint)
                    SwitchRow("Quiet hours", c.quiet, { config = c.copy(quiet = it) })
                    if (c.quiet) {
                        com.sonix21.suinode.ui.screens.shared.ClockField("Quiet from", c.quietStart, hoursOnly = true) { hour, _ -> config = c.copy(quietStart = hour) }
                        com.sonix21.suinode.ui.screens.shared.ClockField("Quiet until", c.quietEnd, hoursOnly = true) { hour, _ -> config = c.copy(quietEnd = hour) }
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
        val active = state.optJSONObject("active") ?: JSONObject()
        val history = state.optJSONArray("history")?.objList().orEmpty().asReversed()
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Refresh") { refresh() }
                GhostButton("Clear history") { clear = true }
            } }
            if (history.isEmpty()) item { Text("No alerts recorded. Monitoring is optional and starts only after you enable and save it.", color = LocalGlass.current.textFaint) }
            items(history) { record -> GlassCard { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(record.optString("title"), color = LocalGlass.current.text)
                Text(record.optString("detail"), color = LocalGlass.current.textFaint)
                val current = active.optJSONObject(record.optString("key"))
                val stillActive = !record.optBoolean("resolved") && current?.optLong("time") == record.optLong("time")
                Text((if (stillActive) "Active" else "Resolved / previous event") + " · " + java.time.Instant.ofEpochSecond(record.optLong("time")).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(), color = LocalGlass.current.teal)
            } } }
        }
    }
}
