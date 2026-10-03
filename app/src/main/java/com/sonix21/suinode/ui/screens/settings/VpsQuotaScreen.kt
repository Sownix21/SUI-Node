package com.sonix21.suinode.ui.screens.settings

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
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal

@Composable
fun VpsQuotaScreen(nav: NavController, panelId: String) {
    val context = LocalContext.current
    val store = remember { PanelStore(context.applicationContext) }
    val panel = Panels.list.collectAsState().value.firstOrNull { it.id == panelId }
    val runner = rememberRunner()
    val zones = remember { java.time.ZoneId.getAvailableZoneIds().sorted().map { Opt(it, it) } }
    var loaded by remember { mutableStateOf<VpsQuotaConfig?>(null) }
    var usage by remember { mutableStateOf(JSONObject()) }
    fun load() = runner.go {
        withContext(Dispatchers.IO) { store.vpsConfig(panelId) to store.monitorState().optJSONObject("vps")?.optJSONObject(panelId) }
            .let { loaded = it.first; usage = it.second ?: JSONObject() }
    }
    LaunchedEffect(panelId) { load() }
    val original = loaded
    if (original == null) { RecordLoadingPage("VPS traffic quota", nav, runner, ::load); return }
    var c by remember(panelId) { mutableStateOf(original) }
    fun gb(value: Long) = BigDecimal.valueOf(value).movePointLeft(9).stripTrailingZeros().toPlainString()
    var receiveLimit by remember { mutableStateOf(gb(c.receiveLimit)) }
    var sendLimit by remember { mutableStateOf(gb(c.sendLimit)) }
    var totalLimit by remember { mutableStateOf(gb(c.totalLimit)) }
    var correct by remember { mutableStateOf(false) }
    var initialReceive by remember { mutableStateOf(gb(if (usage.optString("baseline") == c.baseline) usage.optLong("usedReceive") else c.initialReceive)) }
    var initialSend by remember { mutableStateOf(gb(if (usage.optString("baseline") == c.baseline) usage.optLong("usedSend") else c.initialSend)) }
    PageScaffold("VPS traffic quota", nav, subtitle = panel?.name, busy = runner.busy,
        draftValue = { jo("config" to c.toJson(), "receive" to receiveLimit, "send" to sendLimit, "total" to totalLimit,
            "correct" to correct, "initialReceive" to initialReceive, "initialSend" to initialSend) },
        primaryAction = { HeaderPrimaryAction("Save local VPS quota") { runner.go {
            val correcting = c.enabled && c.startMode == "now" && correct
            val scheduledStart = c.enabled && c.startMode == "next_cycle" &&
                (!original.enabled || original.startMode != c.startMode || VpsQuota.calendarChanged(original, c) || c.startAt == 0L)
            val restartNow = c.enabled && c.startMode == "now" && original.startMode != "now"
            val next = if (!c.enabled) original.copy(enabled = false) else c.copy(
                receiveLimit = if (c.enabled && c.mode in setOf("receive", "separate")) VpsQuota.parseGb(receiveLimit) else c.receiveLimit,
                sendLimit = if (c.enabled && c.mode in setOf("send", "separate")) VpsQuota.parseGb(sendLimit) else c.sendLimit,
                totalLimit = if (c.enabled && c.mode == "combined") VpsQuota.parseGb(totalLimit) else c.totalLimit,
                initialReceive = if (scheduledStart) 0 else if (correcting) VpsQuota.parseGb(initialReceive) else if (restartNow && usage.optString("baseline") == c.baseline) usage.optLong("usedReceive") else c.initialReceive,
                initialSend = if (scheduledStart) 0 else if (correcting) VpsQuota.parseGb(initialSend) else if (restartNow && usage.optString("baseline") == c.baseline) usage.optLong("usedSend") else c.initialSend,
                startAt = if (scheduledStart) VpsQuota.nextReset(System.currentTimeMillis()/1000, c) else if (restartNow) 0L else c.startAt,
                baseline = if (correcting || scheduledStart || restartNow) java.util.UUID.randomUUID().toString() else c.baseline)
            VpsQuota.validateCalendarChange(original, next, usage.has("lastCheck"), correcting || scheduledStart)
            next.validate()
            withContext(Dispatchers.IO) { store.saveVpsConfig(panelId, next) }
            MonitoringScheduler.checkNow(context)
            DraftRegistry.saved()
            ToastBus.show("Local quota saved. Enable Monitoring & alerts to receive background warnings.")
            nav.pop()
        } } }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            runner.error?.let { Text(it, color = LocalGlass.current.err) }
            SwitchRow("Track this VPS quota", c.enabled, { c = c.copy(enabled = it) })
            if (c.enabled) {
                SelectField("Provider counts", c.mode, listOf(Opt("Receive + send combined", "combined"), Opt("Receive only (send is free)", "receive"),
                    Opt("Send only (receive is free)", "send"), Opt("Separate receive and send limits", "separate")), clearable = false, onChange = { c = c.copy(mode = it ?: c.mode) })
                Text("Directions are from the VPS: receive = downloaded by the server; send = uploaded by the server. Amounts use decimal GB: 5 TB = 5000 GB; 1 TiB ≈ 1099.511627776 GB.", color = LocalGlass.current.textFaint)
                if (c.mode == "combined") GlassTextField("Combined limit (GB)", totalLimit, { totalLimit = it })
                if (c.mode in setOf("receive", "separate")) GlassTextField("Receive limit (GB)", receiveLimit, { receiveLimit = it })
                if (c.mode in setOf("send", "separate")) GlassTextField("Send limit (GB)", sendLimit, { sendLimit = it })
                NumberField("Monthly reset day", c.resetDay.toLong(), suffix = "1–31", onChange = { c = c.copy(resetDay = (it ?: 1).coerceIn(1, 31).toInt()) })
                Text("Days 29–31 use the month's last day when needed. Set the provider's reset time in the billing timezone.", color = LocalGlass.current.textFaint)
                com.sonix21.suinode.ui.screens.shared.ClockField("Billing reset time", c.resetHour, c.resetMinute) { hour, minute ->
                    c = c.copy(resetHour = hour, resetMinute = minute)
                }
                SelectField("Billing timezone", c.zone, zones, clearable = false, onChange = { c = c.copy(zone = it ?: c.zone) })
                Text("Next reset: " + java.time.Instant.ofEpochSecond(VpsQuota.nextReset(System.currentTimeMillis() / 1000, c))
                    .atZone(java.time.ZoneId.of(c.zone)).toLocalDateTime().toString() + " · ${c.zone}", color = LocalGlass.current.textFaint)
                SelectField("Start calculating usage", c.startMode, listOf(Opt("Now / continue current tracking", "now"), Opt("At the next billing reset", "next_cycle")), clearable = false,
                    onChange = { if (it != null) c = c.copy(startMode = it) })
                Text("Now starts with the first successful sample and any already-used traffic you enter. Next reset waits until the saved reset date/time and begins from zero. Android scheduling can delay the first sample; exact traffic during that gap cannot be reconstructed.", color = LocalGlass.current.textFaint)
                if (c.startMode == "next_cycle" && c.startAt > 0 && original.enabled && !VpsQuota.calendarChanged(original, c)) {
                    Text("Saved tracking start: " + java.time.Instant.ofEpochSecond(c.startAt).atZone(java.time.ZoneId.of(c.zone)).toLocalDateTime().toString() + " · ${c.zone}", color = LocalGlass.current.textFaint)
                }
                if (usage.has("lastCheck") && VpsQuota.calendarChanged(original, c)) Text("Billing calendar changed. Correct already-used traffic below, or choose the next billing reset to start a new baseline.", color = LocalGlass.current.err)
                NumberField("Warn at usage", c.threshold.toLong(), suffix = "%", onChange = { c = c.copy(threshold = (it ?: 90).coerceIn(1, 100).toInt()) })
                if (c.startMode == "now") SwitchRow("Set / correct already-used traffic", correct, { correct = it }, subtitle = "Enter this cycle's usage from the provider dashboard; starts a new measurement baseline")
                if (correct && c.startMode == "now") {
                    GlassTextField("Already received this cycle (GB)", initialReceive, { initialReceive = it })
                    GlassTextField("Already sent this cycle (GB)", initialSend, { initialSend = it })
                }
            }
            GlassCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Observed usage · estimate")
                Text("Receive: ${Fmt.size(usage.optLong("usedReceive"))}\nSend: ${Fmt.size(usage.optLong("usedSend"))}", color = LocalGlass.current.text)
                Text(usage.optString("note").ifBlank { "No saved sample yet" }, color = LocalGlass.current.textFaint)
                Text("Last sample: " + usage.optLong("lastCheck").takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it).toString() }.orEmpty().ifBlank { "Never" }, color = LocalGlass.current.textFaint)
                GhostButton("Read usage now (saved settings)", enabled = panel != null) { runner.go {
                    val saved = withContext(Dispatchers.IO) { store.vpsConfig(panelId) }
                    check(saved.enabled) { "Save an enabled quota first" }
                    val api = SuiClient(requireNotNull(panel))
                    try {
                        val response = api.get("status", mapOf("r" to "net,sys"))
                        check(response.success) { response.msg }
                        val status = requireNotNull(response.objObj())
                        val net = requireNotNull(status.optJSONObject("net"))
                        check(net.opt("recv") is Number && net.opt("sent") is Number) { "Network counters unavailable" }
                        usage = withContext(Dispatchers.IO) { store.updateMonitorState { state ->
                            val all = state.optJSONObject("vps") ?: JSONObject().also { state.put("vps", it) }
                            VpsQuota.sample(all.optJSONObject(panelId), saved, System.currentTimeMillis()/1000,
                                net.optLong("recv"), net.optLong("sent"), status.optJSONObject("sys")?.optLong("bootTime") ?: 0).also { all.put(panelId, it) }
                        } }
                    } finally { api.logout() }
                } }
            } }
            Text("This is a local estimate from APIv2 server network counters, not provider billing data. Counters may include internal/virtual interfaces. Restarts and samples spanning a billing reset can lose unobserved traffic. Use your provider dashboard as the authority and correct the baseline when needed. Alerts follow saved Monitoring settings, selected panels, quiet hours and Android background limits. No VPS or panel counters are reset.", color = LocalGlass.current.textFaint)
        }
    }
}
