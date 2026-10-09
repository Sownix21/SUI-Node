package com.sonix21.suinode.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.APP
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.*
import com.sonix21.suinode.ui.screens.shared.DialSection
import org.json.JSONObject

@Composable
fun CoreBasicsScreen(nav: NavController) {
    val session = useSession(); val data by session.data.collectAsState(); val runner = rememberRunner(); var cfg by remember(session) { mutableStateOf(data.config.deepCopy()) }
    val log = J(cfg.optJSONObject("log") ?: JSONObject().also { cfg.put("log", it) })
    val g = LocalGlass.current
    var confirmCoreRestart by remember { mutableStateOf(false) }
    if (confirmCoreRestart) ConfirmDialog("Restart core", "Restart sing-box? Active connections may be interrupted.",
        onConfirm = { runner.go {
            val e = session.client.postEmpty("restartSb")
            if (!e.success) throw Exception(e.msg)
            ToastBus.show("Core restarted")
        } }, onDismiss = { confirmCoreRestart = false })
    fun save() = runner.go { val r = session.save("config", "set", cfg); if (r.isFailure) throw Exception(r.exceptionOrNull()?.message); ToastBus.show("Core settings saved") }
    PageScaffold("Core settings", nav, subtitle = "sing-box log, NTP and experimental APIs", busy = runner.busy,
        draftValue = { cfg },
        actions = { IconGhostButton(Icons.Filled.RestartAlt, { confirmCoreRestart = true }, contentDesc = "Restart core") },
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("Save core settings") { save() }
        }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(contentPadding = 14.dp) { SectionHeader("Logging"); Spacer(Modifier.height(8.dp)); Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SwitchRow("Disable logging", log.bool("disabled"), { log.setBool("disabled", it, true) })
                if (!log.bool("disabled")) {
                    SelectField("Level", log.optStringOrNull("level"), listOf("trace","debug","info","warn","error","fatal","panic").map { Opt(it,it) }, onChange = { log["level"] = it })
                    GlassTextField("Output", log.str("output"), { log.setStr("output", it) }, hint = "stdout or file path")
                    SwitchRow("Timestamp", log.bool("timestamp"), { log.setBool("timestamp", it, true) })
                }
            } }

            val ntpEnabled = cfg.optJSONObject("ntp")?.optBoolean("enabled") == true
            GlassCard(contentPadding = 14.dp) { SwitchRow("NTP", ntpEnabled, { on -> if (on) cfg.put("ntp", jo("enabled" to true, "server" to "time.apple.com", "server_port" to 123)) else cfg.remove("ntp") }); if (ntpEnabled) {
                val ntp = J(cfg.getJSONObject("ntp")); Spacer(Modifier.height(8.dp)); Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    GlassTextField("Server", ntp.str("server"), { ntp.setStr("server", it, false) }); NumberField("Port", ntp.long("server_port",123), onChange = { ntp["server_port"] = it ?: 123 }); DurationField("Interval", ntp.optStringOrNull("interval"), 'm', onChange = { ntp["interval"] = it })
                    DialSection(ntp, session.outboundTags(data), dnsTags = session.dnsServerTags(data))
                }
            } }

            val exp = J(cfg.optJSONObject("experimental") ?: JSONObject().also { cfg.put("experimental", it) })
            ExperimentalSection(exp, session.outboundTags(data))
            AdvancedJsonCard(cfg) { cfg = it }
        }
    }
}

@Composable
private fun ExperimentalSection(exp: J, outboundTags: List<String>) {
    GlassCard(contentPadding = 14.dp) { SectionHeader("Experimental"); Spacer(Modifier.height(8.dp)); Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchRow("Cache file", exp.has("cache_file"), { if (it) exp["cache_file"] = jo("enabled" to true) else exp.remove("cache_file") })
        if (exp.has("cache_file")) { val c = exp.ensureObj("cache_file"); SwitchRow("Enabled", c.bool("enabled"), { c["enabled"] = it }); GlassTextField("Path", c.str("path"), { c.setStr("path",it) }); GlassTextField("Cache ID", c.str("cache_id"), { c.setStr("cache_id",it) }); SwitchRow("Store FakeIP", c.bool("store_fakeip"), { c.setBool("store_fakeip",it,true) }) }
        SwitchRow("Clash API", exp.has("clash_api"), { if (it) exp["clash_api"] = JSONObject() else exp.remove("clash_api") })
        if (exp.has("clash_api")) { val c = exp.ensureObj("clash_api"); GlassTextField("External controller", c.str("external_controller"), { c.setStr("external_controller",it) }); GlassTextField("External UI", c.str("external_ui"), { c.setStr("external_ui",it) }); GlassTextField("UI download URL", c.str("external_ui_download_url"), { c.setStr("external_ui_download_url",it) }); SelectField("UI download detour", c.optStringOrNull("external_ui_download_detour"), outboundTags.map { Opt(it,it) }, onChange = { c["external_ui_download_detour"] = it }); GlassTextField("Secret", c.str("secret"), { c.setStr("secret",it) }, obscure = true); GlassTextField("Default mode", c.str("default_mode"), { c.setStr("default_mode",it) }); SwitchRow("Allow private network", c.bool("access_control_allow_private_network"), { c.setBool("access_control_allow_private_network",it,true) }) }
        SwitchRow("V2Ray API", exp.has("v2ray_api"), { if (it) exp["v2ray_api"] = jo("listen" to "127.0.0.1:8080", "stats" to jo("enabled" to true, "inbounds" to org.json.JSONArray(), "outbounds" to org.json.JSONArray(), "users" to org.json.JSONArray())) else exp.remove("v2ray_api") })
        if (exp.has("v2ray_api")) { val v = exp.ensureObj("v2ray_api"); GlassTextField("Listen", v.str("listen"), { v.setStr("listen",it) }); val s = v.ensureObj("stats"); SwitchRow("Traffic stats", s.bool("enabled"), { s["enabled"] = it }) }
    } }
}

@Composable
fun PanelSettingsScreen(nav: NavController) {
    val session = useSession(); val runner = rememberRunner(); var settings by remember { mutableStateOf<JSONObject?>(null) }; var tab by remember { mutableIntStateOf(0) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var confirmRestart by remember { mutableStateOf(false) }
    LaunchedEffect(loadAttempt) { runner.go {
        val e = session.client.get("settings")
        if (!e.success) throw Exception(e.msg)
        settings = e.objObj()?.deepCopy() ?: throw java.io.IOException("The panel returned invalid settings.")
    } }
    if (settings == null) {
        com.sonix21.suinode.ui.screens.RecordLoadingPage("Panel settings", nav, runner) { loadAttempt++ }
        return
    }
    if (confirmRestart) ConfirmDialog("Restart panel", "Restart the panel? Management will be briefly unavailable.",
        onConfirm = { runner.go {
            val e = session.client.postEmpty("restartApp")
            if (!e.success) throw Exception(e.msg)
            ToastBus.show("Panel restart requested")
        } }, onDismiss = { confirmRestart = false })
    val o = settings
    PageScaffold("Panel settings", nav, subtitle = "Changes may alter the panel address", busy = runner.busy,
        draftValue = { settings },
        actions = { IconGhostButton(Icons.Filled.RestartAlt, { confirmRestart = true }, contentDesc = "Restart panel") },
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("Save settings") { runner.go { val r = session.save("settings", "set", requireNotNull(o).also(SubscriptionConfig::validateSettings)); if (r.isFailure) throw Exception(r.exceptionOrNull()?.message); ToastBus.show("Settings saved. Listener changes may require a panel restart.") } }
        }) {
        if (o == null) return@PageScaffold
        val j = J(o)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MaintenanceControls(session)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Web","Subscription","JSON sub","Clash sub").forEachIndexed { i, t -> GhostButton(t, tint = if (tab == i) LocalGlass.current.teal else null) { tab = i } } }
            GlassCard(contentPadding = 14.dp) { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { when(tab) {
                0 -> { GlassTextField("Listen address", j.str("webListen"), { j.setStr("webListen",it,false) }); NumberField("Web port", j.str("webPort").toLongOrNull() ?: 2095, onChange = { j["webPort"] = (it ?: 2095).toString() }); GlassTextField("Web path", j.str("webPath"), { j.setStr("webPath",it,false) }); GlassTextField("Domain", j.str("webDomain"), { j.setStr("webDomain",it,false) }); GlassTextField("TLS key file", j.str("webKeyFile"), { j.setStr("webKeyFile",it,false) }); GlassTextField("TLS certificate file", j.str("webCertFile"), { j.setStr("webCertFile",it,false) }); GlassTextField("Public web URI", j.str("webURI"), { j.setStr("webURI",it,false) }); StringNumber(j,"sessionMaxAge","Session max age (minutes)",0); StringNumber(j,"trafficAge","Traffic retention (days)",30); StringNumber(j,"statsBucketSeconds","Stats bucket (seconds)",60); GlassTextField("Time zone", j.str("timeLocation"), { j.setStr("timeLocation",it,false) }); GlassTextField("Global reset cron", j.str("globalReset"), { j.setStr("globalReset",it,false) }) }
                1 -> { SwitchRow("Encode subscription", j.str("subEncode") == "true", { j["subEncode"] = it.toString() }); SwitchRow("Show subscription info", j.str("subShowInfo") == "true", { j["subShowInfo"] = it.toString() }); GlassTextField("Listen", j.str("subListen"), { j.setStr("subListen",it,false) }); StringNumber(j,"subPort","Port",2096); GlassTextField("Path", j.str("subPath"), { j.setStr("subPath",it,false) }); GlassTextField("Domain", j.str("subDomain"), { j.setStr("subDomain",it,false) }); GlassTextField("TLS key", j.str("subKeyFile"), { j.setStr("subKeyFile",it,false) }); GlassTextField("TLS certificate", j.str("subCertFile"), { j.setStr("subCertFile",it,false) }); StringNumber(j,"subUpdates","Update interval",12); PublicSubscriptionFields(j, session.data.value.subURI) }
                2 -> SubscriptionFields(o, clash = false)
                else -> SubscriptionFields(o, clash = true)
            } } }
            if (tab == 0) Text(UiLocale.text("Global reset: leave empty or use off to disable. Example: 0 0 1 * * resets monthly in the panel time zone. s-ui 1.6.4 validates the schedule and applies changes without restarting; the first reset occurs at the next scheduled boundary. Resets re-enable all clients."), color = LocalGlass.current.textFaint)
            AdvancedJsonCard(o) { settings = it }
        }
    }
}

@Composable private fun StringNumber(j: J, key: String, label: String, def: Long) = NumberField(label, j.str(key).toLongOrNull() ?: def, onChange = { j[key] = (it ?: def).toString() })

@Composable
fun AppSettingsScreen(nav: NavController, requestThemeRefresh: () -> Unit) {
    val activity = androidx.activity.compose.LocalActivity.current as? androidx.fragment.app.FragmentActivity
    var theme by remember { mutableStateOf(APP.prefs.theme) }; var lang by remember { mutableStateOf(APP.prefs.lang) }; var refresh by remember { mutableStateOf(APP.prefs.refreshIntervalSec.toLong()) }
    var screenProtection by remember { mutableStateOf(APP.prefs.blockScreenCapture) }
    var reviewChanges by remember { mutableStateOf(APP.prefs.reviewChanges) }
    var recoverSaves by remember { mutableStateOf(APP.prefs.recoverSaves) }
    var wallpaperColors by remember { mutableStateOf(APP.prefs.dynamicColors) }
    PageScaffold("App settings", nav, subtitle = "Local preferences for this device") {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(contentPadding = 14.dp) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Appearance")
                if (android.os.Build.VERSION.SDK_INT >= 31) SwitchRow("Wallpaper colors", wallpaperColors, { wallpaperColors = it; APP.prefs.dynamicColors = it; requestThemeRefresh() }, subtitle = "Use your Android wallpaper palette")
                SelectField("Theme", theme, AppThemeMode.entries.map { Opt(it.label,it) }, clearable = false, onChange = { v -> if (v != null) { theme = v; APP.prefs.theme = v; requestThemeRefresh() } })
                SelectField("Language", lang, listOf(Opt("English",Lang.EN),Opt("فارسی",Lang.FA)), clearable = false, onChange = { v -> if (v != null) { lang = v; APP.prefs.lang = v; requestThemeRefresh() } })
                AccentSelector(wallpaperColors) { wallpaperColors = APP.prefs.dynamicColors; requestThemeRefresh() }
            } }
            GlassCard(contentPadding = 14.dp) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Behavior")
                SwitchRow("Review panel changes before saving", reviewChanges, { reviewChanges=it;APP.prefs.reviewChanges=it }, subtitle="Recommended: inspect a redacted change list before APIv2 saves")
                SwitchRow("Recover uncertain saves", recoverSaves, { recoverSaves=it;APP.prefs.recoverSaves=it }, subtitle="Recommended: record pending saves and verify with GET after interruptions. Existing pending saves must still be resolved.")
                SwitchRow("Block screenshots & recent-app previews", screenProtection, {
                    screenProtection = it
                    APP.prefs.blockScreenCapture = it
                    (activity as? com.sonix21.suinode.MainActivity)?.applyScreenPrivacy()
                    requestThemeRefresh()
                }, subtitle = "Recommended for privacy. Turning this off permits screen capture of sensitive panel data.")
                NumberField("Refresh interval", refresh, suffix = "seconds", onChange = { refresh = (it ?: 4).coerceIn(2,60); APP.prefs.refreshIntervalSec = refresh.toInt() })
            } }
            val protection by com.sonix21.suinode.data.Panels.protection.collectAsState()
            AppPinSettings(onChanged = requestThemeRefresh)
            GlassCard(contentPadding = 14.dp) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Credential security")
                Text("AES-256-GCM · $protection", color = LocalGlass.current.text, fontSize = 12.sp)
                Text("Device-bound vault; excluded from cloud backup and device transfer. Screenshot protection and automatic-lock timing are configurable above. Document transfers wait for authentication if the app locks while choosing a file. Use verified HTTPS for secure transport.", color = LocalGlass.current.textFaint, fontSize = 11.sp)
            } }
            AboutSection()
        }
    }
}
