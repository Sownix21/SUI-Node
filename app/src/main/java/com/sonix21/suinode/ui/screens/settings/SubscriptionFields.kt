package com.sonix21.suinode.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.screens.shared.CsvField
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SubscriptionFields(settings: JSONObject, clash: Boolean) {
    // JSONObject mutates in place; observe the edit signal inside this restart scope.
    JsonEditSignal.revision
    val key = if (clash) "subClashExt" else "subJsonExt"
    val raw = settings.optString(key)
    val parsed = remember(raw, clash) { runCatching { SubscriptionConfig.parse(raw, clash) } }
    var editor by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("") }
    var sourceError by remember { mutableStateOf<String?>(null) }
    val o = parsed.getOrNull()
    fun commit() {
        if (o != null) {
            settings.put(key, SubscriptionConfig.encode(o, clash))
            JsonEditSignal.bump()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (clash) "Clash subscription" else "JSON subscription", color = LocalGlass.current.text, style = MaterialTheme.typography.titleMedium)
        Text("These options customize generated subscriptions, not the panel's own core.",
            color = LocalGlass.current.textFaint, style = MaterialTheme.typography.bodySmall)
        if (o == null) Text("This extension cannot be read safely. Open the advanced editor to correct it; your original text has been kept.",
            color = LocalGlass.current.err)
        else if (clash) ClashSubscriptionFields(o, settings, ::commit)
        else JsonSubscriptionFields(o, ::commit)
        GhostButton(if (clash) "Advanced YAML editor" else "Advanced JSON editor") {
            source = raw; sourceError = null; editor = true
        }
    }
    if (editor) ModalBottomSheet(onDismissRequest = { editor = false }, containerColor = LocalGlass.current.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(if (clash) "Clash extension · YAML" else "Subscription extension · JSON")
            Text("Apply updates the local draft only. Use the page tick to save to the panel.",
                color = LocalGlass.current.textFaint)
            GlassTextField("Extension", source, { source = it; sourceError = null },
                singleLine = false, minLines = 8, maxLines = 18)
            sourceError?.let { Text(it, color = LocalGlass.current.err) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Cancel") { editor = false }
                PrimaryButton("Apply to draft") {
                    try {
                        SubscriptionConfig.parse(source, clash)
                        settings.put(key, source)
                        JsonEditSignal.bump()
                        editor = false
                    } catch (_: Exception) {
                        sourceError = if (clash) "Enter one valid YAML mapping with unique string keys, without custom tags or excessively nested/expanded values."
                            else "Enter a valid JSON object."
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonSubscriptionFields(o: JSONObject, changed: () -> Unit) {
    JsonEditSignal.revision
    val defaults = SubscriptionConfig.selectedDefaults(o)
    MultiSelectField("Default rules", defaults,
        listOf(Opt("Sniff", "0"), Opt("Clash Direct", "1"), Opt("Clash Global", "2")),
        { SubscriptionConfig.defaults(o, it); changed() })
    SelectField("Final route", o.optStringOrNull("final"),
        listOf(Opt("Proxy", "proxy"), Opt("Direct", "direct")),
        onChange = { o.setOrRemove("final", it); changed() })
    val catalog = SubscriptionConfig.catalog.map { it.optString("tag") }
    val custom = o.optJSONArray("rule_set")?.objList().orEmpty().map { it.optString("tag") }
    val referenced = o.optJSONArray("rules")?.objList().orEmpty().flatMap { it.optJSONArray("rule_set")?.strList().orEmpty() }
    val choices = (catalog + custom + referenced).distinct()
    listOf("proxy" to "Route to proxy", "direct" to "Route to direct", "reject" to "Block").forEach { (target, label) ->
        MultiSelectField(label, SubscriptionConfig.routeTags(o, target), choices.map { Opt(it, it) },
            { SubscriptionConfig.setRouteTags(o, target, it); changed() })
    }
    HorizontalDivider(color = LocalGlass.current.textFaint.copy(alpha = .15f))
    SectionHeader("Other options")
    SwitchRow("Logging", o.has("log"), { SubscriptionConfig.jsonOption(o, "log", it); changed() })
    o.optJSONObject("log")?.let { log ->
        SelectField("Log level", log.optString("level", "info"),
            listOf("trace", "debug", "info", "warn", "error", "fatal", "panic").map { Opt(it, it) },
            clearable = false, onChange = { log.put("level", it); changed() })
        SwitchRow("Timestamp", log.optBoolean("timestamp"), { log.put("timestamp", it); changed() })
    }
    SwitchRow("DNS", o.has("dns"), { SubscriptionConfig.jsonOption(o, "dns", it); changed() })
    o.optJSONObject("dns")?.let { dns ->
        val tags = dns.optJSONArray("servers")?.objList().orEmpty().map { it.optString("tag") }.filter { it.isNotBlank() }
        SelectField("Final DNS", dns.optStringOrNull("final"), tags.map { Opt(it, it) },
            clearable = false, onChange = { dns.put("final", it); changed() })
        SubscriptionDns(dns, "proxy-dns", "Global DNS", changed)
        SubscriptionDns(dns, "direct-dns", "Direct DNS", changed)
        SelectField("Default domain resolver", o.optStringOrNull("default_domain_resolver"), tags.map { Opt(it, it) },
            onChange = { o.setOrRemove("default_domain_resolver", it); changed() })
        val selected = SubscriptionConfig.routeTags(o, "direct", dns = true)
        val dnsChoices = (catalog.filter { it.startsWith("geosite-") } + custom + selected).distinct()
        MultiSelectField("Use direct DNS for", selected, dnsChoices.map { Opt(it, it) },
            { SubscriptionConfig.setRouteTags(o, "direct", it, dns = true); changed() })
    }
    SwitchRow("Subscription inbounds", o.has("inbounds"), { SubscriptionConfig.jsonOption(o, "inbounds", it); changed() })
    if (o.has("inbounds")) {
        val tun = o.optJSONArray("inbounds")?.objList()?.firstOrNull { it.optString("type") == "tun" }
        if (tun == null) Text("Custom inbounds are preserved. Use the advanced editor for this configuration.",
            color = LocalGlass.current.textFaint)
        else {
            CsvField("TUN addresses", tun.optJSONArray("address")?.strList().orEmpty(), { tun.put("address", jarr(it)); changed() })
            NumberField("MTU", tun.optLong("mtu", 9000), onChange = { tun.setOrRemove("mtu", it); changed() })
            CsvField("Excluded Android packages", tun.optJSONArray("exclude_package")?.strList().orEmpty(),
                { tun.put("exclude_package", jarr(it)); changed() })
            SwitchRow("Platform HTTP proxy", tun.optJSONObject("platform")?.has("http_proxy") == true, { enabled ->
                val platform = tun.optJSONObject("platform") ?: JSONObject()
                if (enabled) {
                    if (!platform.has("http_proxy")) platform.put("http_proxy", jo("enabled" to true, "server" to "127.0.0.1", "server_port" to 2080))
                    tun.put("platform", platform)
                } else {
                    platform.remove("http_proxy")
                    if (platform.length() == 0) tun.remove("platform") else tun.put("platform", platform)
                }
                changed()
            })
        }
    }
    SwitchRow("Experimental", o.has("experimental"), { SubscriptionConfig.jsonOption(o, "experimental", it); changed() },
        subtitle = "Clash API and cache-file defaults for subscription clients")
}

@Composable
private fun SubscriptionDns(dns: JSONObject, tag: String, label: String, changed: () -> Unit) {
    val server = dns.optJSONArray("servers")?.objList()?.lastOrNull { it.optString("tag") == tag }
    SectionHeader(label)
    if (server == null) {
        GhostButton("Add " + label) {
            val servers = dns.optJSONArray("servers") ?: JSONArray().also { dns.put("servers", it) }
            servers.put((SubscriptionConfig.jsonDefault("dns") as JSONObject).getJSONArray("servers")
                .objList().first { it.optString("tag") == tag }.deepCopy())
            changed()
        }
        return
    }
    val type = server.optString("type", "local")
    SelectField("Type", type, (listOf("udp", "tcp", "local", "tls", "quic", "h3") + type).distinct().map { Opt(it, it) },
        clearable = false, onChange = {
            server.put("type", it)
            if (it == "local") { server.remove("server"); server.remove("server_port") }
            changed()
        })
    if (type != "local") {
        GlassTextField("Server address", server.optString("server"), { server.put("server", it); changed() })
        NumberField("Server port", server.optLong("server_port").takeIf { it > 0 },
            onChange = { server.setOrRemove("server_port", it); changed() })
    }
}

@Composable
private fun ClashSubscriptionFields(o: JSONObject, settings: JSONObject, changed: () -> Unit) {
    JsonEditSignal.revision
    fun toggle(key: String, enabled: Boolean) { SubscriptionConfig.clashOption(o, key, enabled); changed() }
    SwitchRow("Mixed port", o.has("mixed-port"), { toggle("mixed-port", it) })
    if (o.has("mixed-port")) {
        NumberField("Mixed port number", o.optLong("mixed-port"), onChange = { o.put("mixed-port", it ?: 7890); changed() })
        SwitchRow("Allow LAN access", o.optBoolean("allow-lan"), { o.put("allow-lan", it); changed() })
    }
    SwitchRow("TUN", o.optJSONObject("tun")?.optBoolean("enable") == true, { toggle("tun", it) })
    SwitchRow("External controller", o.has("external-controller"), { toggle("external-controller", it) })
    if (o.has("external-controller"))
        GlassTextField("Controller address", o.optString("external-controller"), { o.put("external-controller", it); changed() })
    SwitchRow("Logging", o.has("log-level"), { toggle("log-level", it) })
    if (o.has("log-level"))
        SelectField("Log level", o.optString("log-level"), listOf("debug", "info", "warning", "error").map { Opt(it, it) },
            clearable = false, onChange = { o.put("log-level", it); changed() })
    SwitchRow("DNS", o.optJSONObject("dns")?.optBoolean("enable") == true, { toggle("dns", it) })
    SwitchRow("Rules", o.has("rules"), { toggle("rules", it) })
    if (o.has("rules")) {
        val known = SubscriptionConfig.clashRules
        MultiSelectField("Routing presets", o.optJSONArray("rules")?.strList().orEmpty().filter { r -> known.any { it.second == r } }.toSet(),
            known.map { Opt(it.first, it.second) }, { SubscriptionConfig.setClashRules(o, it); changed() })
        Text("MATCH,Proxy remains the fallback. Custom rules are retained.", color = LocalGlass.current.textFaint,
            style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider(color = LocalGlass.current.textFaint.copy(alpha = .15f))
    listOf("subClashNoDefGrp" to "No default group", "subClashSprtAll" to "Separate all configurations",
        "subClashUdp" to "UDP").forEach { (key, label) ->
        SwitchRow(label, settings.optString(key) == "true", { settings.put(key, it.toString()); JsonEditSignal.bump() })
    }
}
