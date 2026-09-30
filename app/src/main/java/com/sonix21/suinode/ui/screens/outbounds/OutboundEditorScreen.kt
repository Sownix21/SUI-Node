package com.sonix21.suinode.ui.screens.outbounds

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.Rand
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.data.Panels
import com.sonix21.suinode.ui.glass.DurationField
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.MultiSelectField
import com.sonix21.suinode.ui.glass.NumberField
import com.sonix21.suinode.ui.glass.Opt
import com.sonix21.suinode.ui.glass.PrimaryButton
import com.sonix21.suinode.ui.glass.SectionHeader
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.RecordLoadingPage
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.inbounds.SS_METHODS
import com.sonix21.suinode.ui.screens.inbounds.genSsPassword
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.shared.DialSection
import com.sonix21.suinode.ui.screens.shared.HeadersCard
import com.sonix21.suinode.ui.screens.shared.MuxDirection
import com.sonix21.suinode.ui.screens.shared.MuxSection
import com.sonix21.suinode.ui.screens.shared.NetworkField
import com.sonix21.suinode.ui.screens.shared.OutTlsEditor
import com.sonix21.suinode.ui.screens.shared.TransportSection
import com.sonix21.suinode.ui.screens.shared.UotField
import com.sonix21.suinode.ui.screens.useSession
import org.json.JSONObject

val OUT_TYPES = listOf(
    "direct", "socks", "http", "shadowsocks", "snell", "vmess", "trojan", "naive", "hysteria",
    "shadowtls", "vless", "tuic", "hysteria2", "anytls", "tor", "ssh", "selector", "urltest",
    "bridge",
)

private val NO_DIAL = setOf("selector", "urltest")
private val NO_SERVER = setOf("direct", "selector", "urltest", "tor")
private val OUT_TRANSPORT_AVAILABLE = setOf("vmess", "trojan", "vless")
private val OUT_MUX_AVAILABLE = setOf("shadowsocks", "vmess", "trojan", "vless")
private val OUT_TLS_AVAILABLE = setOf("http", "vmess", "trojan", "naive", "hysteria", "shadowtls", "vless", "tuic", "hysteria2", "anytls")

fun createDefaultOutbound(type: String): JSONObject = (when (type) {
    "direct" -> jo()
    "socks" -> jo("version" to "5")
    "http" -> jo("tls" to JSONObject())
    "shadowsocks" -> jo("method" to "none", "multiplex" to JSONObject())
    "snell" -> jo("version" to 6, "psk" to "")
    "vmess" -> jo("tls" to JSONObject(), "multiplex" to JSONObject(), "transport" to JSONObject(),
        "security" to "auto", "global_padding" to false)
    "trojan" -> jo("tls" to JSONObject(), "multiplex" to JSONObject(), "transport" to JSONObject())
    "naive" -> jo("tls" to jo("enabled" to true))
    "hysteria" -> jo("up_mbps" to 100L, "down_mbps" to 100L, "tls" to jo("enabled" to true))
    "shadowtls" -> jo("version" to 3, "tls" to jo("enabled" to true))
    "vless" -> jo("tls" to JSONObject(), "multiplex" to JSONObject(), "transport" to JSONObject())
    "tuic" -> jo("congestion_control" to "cubic", "tls" to jo("enabled" to true))
    "hysteria2" -> jo("tls" to jo("enabled" to true))
    "anytls" -> jo(
        "tls" to jo("enabled" to true),
        "idle_session_check_interval" to "30s",
        "idle_session_timeout" to "30s",
        "min_idle_session" to 0,
    )
    "tor" -> jo("executable_path" to "./tor", "data_directory" to "\$HOME/.cache/tor", "torrc" to jo("ClientOnly" to "1"))
    "ssh" -> jo()
    "selector" -> jo()
    "urltest" -> jo()
    "bridge" -> jo()
    else -> jo()
}).apply { put("type", type) }

@Composable
fun OutboundEditorScreen(nav: NavController, id: Int) {
    val g = LocalGlass.current
    val session = useSession()
    val runner = rememberRunner()
    val data by session.data.collectAsState()

    var obj by remember { mutableStateOf<JSONObject?>(null) }

    var loadAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(id, loadAttempt) {
        if (id > 0) {
            runner.go { obj = session.fetchRecord("outbounds", id.toLong()) }
        } else {
            val o = createDefaultOutbound("direct")
            o.put("id", 0L)
            o.put("tag", "direct-" + Rand.seq(3))
            obj = o
        }
    }

    val o = obj
    if (o == null) {
        RecordLoadingPage("Outbound", nav, runner) { loadAttempt++ }
        return
    }
    val j = J(o)
    val type = j.str("type")

    fun changeType(newType: String) {
        val fresh = createDefaultOutbound(newType)
        fresh.put("id", j.long("id"))
        fresh.put("tag", if (j.long("id") > 0) j.str("tag") else "$newType-" + Rand.seq(3))
        // preserve common address fields
        j.o.optStringOrNull("server")?.let { fresh.put("server", it) }
        if (j.long("server_port") > 0) fresh.put("server_port", j.long("server_port"))
        obj = fresh
    }

    PageScaffold(
        title = if (id <= 0) "New Outbound" else "Edit Outbound",
        draftValue = { obj },
        subtitle = "$type · ${o.optString("tag")}",
        nav = nav,
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction(if (id <= 0) "Create outbound" else "Save changes", loading = runner.busy) {
                    val tag = j.str("tag")
                    when {
                        tag.isBlank() -> ToastBus.show("tag is required")
                        session.data.value.outbounds.any {
                            it.optString("tag") == tag && it.optLongOrX("id") != j.long("id")
                        } -> ToastBus.show("duplicate tag")
                        else -> runner.go {
                            val r = session.save("outbounds", if (id <= 0) "new" else "edit", j.o)
                            if (r.isSuccess) { ToastBus.show("saved ✓"); nav.pop() } else throw Exception(r.exceptionOrNull()?.message)
                        }
                    }
                }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            GlassCard(contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectField(
                        label = "Type",
                        value = type,
                        options = OUT_TYPES.map { Opt(it, it) },
                        onChange = { v -> v?.let(::changeType) },
                        clearable = false,
                    )
                    GlassTextField("Tag", j.str("tag"), { v -> j.setStr("tag", v, blankRemoves = false) })
                }
            }

            if (type !in NO_SERVER) {
                GlassCard(contentPadding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassTextField("Server", j.str("server"), { v -> j.setStr("server", v, blankRemoves = false) }, modifier = Modifier.weight(1.5f))
                            NumberField("Port", j.long("server_port").takeIf { it > 0 },
                                onChange = { v -> j.o.put("server_port", v ?: 0) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            OutProtocolFields(j, type)
            if (type in setOf("hysteria", "hysteria2", "tuic")) com.sonix21.suinode.ui.screens.shared.TransportTuning(j)

            if (type in OUT_TRANSPORT_AVAILABLE) TransportSection(j)
            if (type in OUT_TLS_AVAILABLE)
                OutTlsEditor(j.ensureObj("tls"), server = j.str("server").takeIf { it.isNotBlank() }, serverPort = j.long("server_port"))
            if (type in OUT_MUX_AVAILABLE) MuxSection(j, MuxDirection.OUT)
            if (type !in NO_DIAL)
                DialSection(j, remember(data) { session.outboundTags(data) }, dnsTags = remember(data) { session.dnsServerTags(data) })
            com.sonix21.suinode.ui.screens.AdvancedJsonCard(j.o) { obj = it }
        }
    }
}

/** Per-type outbound field editors. */
@Composable
private fun OutProtocolFields(j: J, type: String) {
    when (type) {
        "socks" -> SocksFields(j)
        "http" -> HttpFields(j)
        "shadowsocks" -> ShadowsocksOutFields(j)
        "snell" -> SnellOutFields(j)
        "bridge" -> BridgeOutFields(j)
        "vmess" -> VmessOutFields(j)
        "trojan" -> TrojanOutFields(j)
        "naive" -> NaiveOutFields(j)
        "hysteria" -> HysteriaOutFields(j)
        "shadowtls" -> ShadowTlsOutFields(j)
        "vless" -> VlessOutFields(j)
        "tuic" -> TuicOutFields(j)
        "hysteria2" -> Hysteria2OutFields(j)
        "anytls" -> AnytlsOutFields(j)
        "tor" -> TorFields(j)
        "ssh" -> SshFields(j)
        "selector" -> LBFields(j, urlTest = false)
        "urltest" -> LBFields(j, urlTest = true)
    }
}

@Composable private fun SocksFields(j: J) {
    Card2("SOCKS") {
        GlassTextField("Username", j.str("username"), { j.setStr("username", it) })
        GlassTextField("Password", j.str("password"), { j.setStr("password", it) })
        SelectField(label = "Version", value = j.str("version"), options = listOf(Opt("4","4"),Opt("4a","4a"),Opt("5","5")),
            onChange = { j.setStr("version", it ?: "5") }, clearable = false)
        NetworkField(j)
        UotField(j)
    }
}

@Composable private fun HttpFields(j: J) {
    Card2("HTTP") {
        GlassTextField("Username", j.str("username"), { j.setStr("username", it) })
        GlassTextField("Password", j.str("password"), { j.setStr("password", it) })
        GlassTextField("Path", j.str("path"), { j.setStr("path", it) })
        HeadersCard(j)
    }
}

@Composable private fun ShadowsocksOutFields(j: J) {
    Card2("Shadowsocks") {
        SelectField(label = "Method", value = j.str("method"), options = SS_METHODS.map { Opt(it, it) },
            onChange = { m -> val mm = m ?: "none"; j.o.put("method", mm); genSsPassword(mm)?.let { j.o.put("password", it) } ?: j.o.remove("password") },
            clearable = false)
        GlassTextField("Password", j.str("password"), { v -> if (v.isBlank()) j.o.remove("password") else j.o.put("password", v) })
        NetworkField(j)
        UotField(j)
        GlassTextField("Plugin", j.str("plugin"), { j.setStr("plugin", it) })
        if (j.has("plugin")) GlassTextField("Plugin options", j.str("plugin_opts"), { j.setStr("plugin_opts", it) })
    }
}

// Outbounds support v4 and v6 (inbounds v5/v6). v4 carries obfs, v6 a mode;
// switching versions drops the fields belonging to the one being left behind.
@Composable private fun SnellOutFields(j: J) {
    Card2("Snell") {
        SelectField(label = "Version", value = j.int("version").takeIf { it > 0 } ?: 6,
            options = listOf(Opt("v4", 4), Opt("v6", 6)),
            onChange = { v ->
                val nv = v ?: 6; j.o.put("version", nv)
                if (nv == 6) { j.o.remove("obfs_mode"); j.o.remove("obfs_host") }
                else j.o.remove("mode")
            }, clearable = false)
        GlassTextField("PSK", j.str("psk"), { v -> if (v.isBlank()) j.o.remove("psk") else j.o.put("psk", v) })
        GlassTextField("User key", j.optStringOrNull("userkey") ?: "", { v -> j.setStr("userkey", v) })
        if ((j.int("version").takeIf { it > 0 } ?: 6) == 4) {
            SelectField(label = "Obfs mode", value = j.optStringOrNull("obfs_mode"),
                options = listOf(Opt("none", "none"), Opt("http", "http"), Opt("tls", "tls")),
                onChange = { v -> j.setOrRemove("obfs_mode", v) })
            GlassTextField("Obfs host", j.optStringOrNull("obfs_host") ?: "", { v -> j.setStr("obfs_host", v) })
        } else {
            SelectField(label = "Mode", value = j.optStringOrNull("mode"),
                options = listOf(Opt("default", "default"), Opt("unshaped", "unshaped"), Opt("unsafe-raw", "unsafe-raw")),
                onChange = { v -> j.setOrRemove("mode", v) })
        }
        SwitchRow("Reuse", j.bool("reuse"), { on -> j.setBool("reuse", on, onlyTrue = true) })
    }
}

@Composable private fun BridgeOutFields(j: J) {
    Card2("Bridge") {
        GlassTextField("Interface", j.optStringOrNull("interface") ?: "", { v -> j.setStr("interface", v) })
        GlassTextField("Bridge name", j.optStringOrNull("bridge_name") ?: "", { v -> j.setStr("bridge_name", v) })
        NumberField("iproute2 table index", j.optLongOr("iproute2_table_index").takeIf { it > 0 },
            onChange = { v -> j.setLong("iproute2_table_index", v) })
        NumberField("iproute2 rule index", j.optLongOr("iproute2_rule_index").takeIf { it > 0 },
            onChange = { v -> j.setLong("iproute2_rule_index", v) })
    }
}

@Composable private fun VmessOutFields(j: J) {
    Card2("VMESS") {
        GlassTextField("UUID", j.str("uuid"), { j.setStr("uuid", it) })
        NumberField("Alter ID", j.long("alter_id").takeIf { it >= 0 } ?: 0L, onChange = { j.o.put("alter_id", it ?: 0) })
        SelectField(label = "Security", value = j.str("security").ifBlank { "auto" },
            options = listOf("auto","none","zero","aes-128-gcm","aes-128-ctr","chacha20-poly1305").map { Opt(it,it) },
            onChange = { j.setStr("security", it ?: "auto") }, clearable = false)
        SelectField(label = "UDP packet encoding", value = j.str("packet_encoding").ifBlank { "none" },
            options = listOf(Opt("none","none"),Opt("packetaddr","packetaddr"),Opt("xudp","xudp")),
            onChange = { j.setStr("packet_encoding", it?.takeIf { p -> p != "none" }) })
        NetworkField(j)
        SwitchRow("Global padding", j.bool("global_padding"), { j.setBool("global_padding", it, onlyTrue = true) })
        SwitchRow("Authenticated length", j.bool("authenticated_length"), { j.setBool("authenticated_length", it, onlyTrue = true) })
    }
}

@Composable private fun TrojanOutFields(j: J) {
    Card2("Trojan") {
        GlassTextField("Password", j.str("password"), { j.setStr("password", it) })
        NetworkField(j)
    }
}

@Composable private fun NaiveOutFields(j: J) {
    Card2("Naive") {
        GlassTextField("Username", j.str("username"), { j.setStr("username", it) })
        GlassTextField("Password", j.str("password"), { j.setStr("password", it) })
        NumberField("Insecure concurrency", j.long("insecure_concurrency").takeIf { it > 0 },
            onChange = { j.setLong("insecure_concurrency", it) })
        SwitchRow("UDP over TCP", j.has("udp_over_tcp"), { on ->
            if (on) j.o.put("udp_over_tcp", true) else j.o.remove("udp_over_tcp")
        })
        SwitchRow("QUIC", j.bool("quic"), { j.setBool("quic", it, onlyTrue = true) })
        if (j.bool("quic")) {
            SelectField(label = "QUIC congestion control", value = j.str("quic_congestion_control"),
                options = listOf(Opt("bbr","bbr"),Opt("BBR2","bbr2"),Opt("cubic","cubic"),Opt("Reno","reno")),
                onChange = { j.setStr("quic_congestion_control", it) })
        }
        val ehObj = j.o.optJSONObject("extra_headers") ?: JSONObject().also { j.o.put("extra_headers", it) }
        HeadersCard(J(ehObj), title = "Extra headers")
    }
}

@Composable private fun HysteriaOutFields(j: J) {
    Card2("Hysteria") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Upload", j.long("up_mbps").takeIf { it > 0 } ?: 100L, suffix = "Mbps",
                onChange = { j.o.put("up_mbps", it ?: 0) }, modifier = Modifier.weight(1f))
            NumberField("Download", j.long("down_mbps").takeIf { it > 0 } ?: 100L, suffix = "Mbps",
                onChange = { j.o.put("down_mbps", it ?: 0) }, modifier = Modifier.weight(1f))
        }
        GlassTextField("Auth string", j.str("auth_str"), { j.setStr("auth_str", it) })
        GlassTextField("Obfs", j.str("obfs"), { j.setStr("obfs", it) })
        NetworkField(j)
        // Shared QUIC tuning replaces the deprecated receive-window controls.
        SwitchRow("Port hopping", j.has("server_ports"), { on ->
            if (on) j.o.put("server_ports", jarr(emptyList<String>())) else { j.o.remove("server_ports"); j.o.remove("hop_interval") }
        })
        if (j.has("server_ports")) {
            com.sonix21.suinode.ui.screens.shared.CsvField("Port ranges", j.arr("server_ports")?.strListX() ?: emptyList(), { parts ->
                if (parts.isEmpty()) j.o.remove("server_ports") else j.o.put("server_ports", jarr(parts))
            }, hint = "2080:3000")
            DurationField("Hop interval", j.optStringOrNull("hop_interval"), 's', onChange = { j.setOrRemove("hop_interval", it) })
        }
    }
}

@Composable private fun ShadowTlsOutFields(j: J) {
    Card2("ShadowTLS") {
        SelectField(label = "Version", value = j.int("version").takeIf { it > 0 } ?: 3,
            options = listOf(Opt("1",1),Opt("2",2),Opt("3",3)),
            onChange = { v ->
                val nv = v ?: 3; j.o.put("version", nv)
                if (nv == 1) j.o.remove("password")
                else if (!j.has("password")) j.o.put("password", "")
            }, clearable = false)
        if ((j.int("version").takeIf { it > 0 } ?: 3) > 1)
            GlassTextField("Password", j.str("password"), { v -> if (v.isBlank()) j.o.remove("password") else j.o.put("password", v) })
    }
}

@Composable private fun VlessOutFields(j: J) {
    Card2("VLESS") {
        GlassTextField("UUID", j.str("uuid"), { j.setStr("uuid", it) })
        SelectField(label = "Flow", value = j.str("flow"),
            options = listOf(Opt("", ""), Opt("xtls-rprx-vision", "xtls-rprx-vision")),
            onChange = { j.setStr("flow", it?.takeIf { f -> f.isNotBlank() }) })
        SelectField(label = "UDP packet encoding", value = j.str("packet_encoding").ifBlank { "none" },
            options = listOf(Opt("none","none"),Opt("packetaddr","packetaddr"),Opt("xudp","xudp")),
            onChange = { j.setStr("packet_encoding", it?.takeIf { p -> p != "none" }) })
        NetworkField(j)
    }
}

@Composable private fun TuicOutFields(j: J) {
    Card2("TUIC") {
        GlassTextField("UUID", j.str("uuid"), { j.setStr("uuid", it) })
        GlassTextField("Password", j.str("password"), { j.setStr("password", it) })
        SelectField(label = "Congestion control", value = j.str("congestion_control"),
            options = listOf(Opt("cubic","cubic"),Opt("new_reno","new_reno"),Opt("bbr","bbr")),
            onChange = { j.setStr("congestion_control", it ?: "cubic") })
        SelectField(label = "UDP relay mode", value = j.str("udp_relay_mode"),
            options = listOf(Opt("native","native"),Opt("quic","quic")),
            onChange = { j.setStr("udp_relay_mode", it) })
        SwitchRow("UDP over stream", j.bool("udp_over_stream"), { j.setBool("udp_over_stream", it, onlyTrue = true) })
        SwitchRow("Zero-RTT handshake", j.bool("zero_rtt_handshake"), { j.setBool("zero_rtt_handshake", it, onlyTrue = true) })
        DurationField("Heartbeat", j.optStringOrNull("heartbeat"), 's', fallbackSeconds = 10,
            onChange = { j.setOrRemove("heartbeat", it) })
        NetworkField(j)
    }
}

@Composable private fun Hysteria2OutFields(j: J) {
    Card2("Hysteria2") {
        GlassTextField("Password", j.str("password"), { j.setStr("password", it) })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Upload", j.long("up_mbps").takeIf { it > 0 }, suffix = "Mbps",
                onChange = { j.setLong("up_mbps", it) }, modifier = Modifier.weight(1f))
            NumberField("Download", j.long("down_mbps").takeIf { it > 0 }, suffix = "Mbps",
                onChange = { j.setLong("down_mbps", it) }, modifier = Modifier.weight(1f))
        }
        SwitchRow("Obfuscation", j.has("obfs"), { on ->
            if (on) j.o.put("obfs", jo("type" to "salamander", "password" to "")) else j.o.remove("obfs")
        })
        if (j.has("obfs")) {
            val ob = j.ensureObj("obfs")
            GlassTextField("Obfuscated password", ob.str("password"), { v ->
                if (v.isBlank()) ob.o.remove("password") else ob.o.put("password", v)
            })
        }
        com.sonix21.suinode.ui.screens.shared.CsvField("Port hopping ranges", j.arr("server_ports")?.strListX() ?: emptyList(), { parts ->
            if (parts.isEmpty()) j.o.remove("server_ports") else j.o.put("server_ports", jarr(parts))
        }, hint = "2080:3000")
        DurationField("Hop interval", j.optStringOrNull("hop_interval"), 's', fallbackSeconds = 10,
            onChange = { j.setOrRemove("hop_interval", it) })
        SwitchRow("Brutal debug", j.bool("brutal_debug"), { j.setBool("brutal_debug", it, onlyTrue = true) })
        NetworkField(j)
    }
}

@Composable private fun AnytlsOutFields(j: J) {
    Card2("AnyTLS") {
        GlassTextField("Password", j.str("password"), { j.setStr("password", it) })
        DurationField("Idle session check interval", j.optStringOrNull("idle_session_check_interval"), 's',
            fallbackSeconds = 30, onChange = { j.setOrRemove("idle_session_check_interval", it) })
        DurationField("Idle session timeout", j.optStringOrNull("idle_session_timeout"), 's',
            fallbackSeconds = 30, onChange = { j.setOrRemove("idle_session_timeout", it) })
        NumberField("Minimum idle session", j.long("min_idle_session").takeIf { it > 0 },
            onChange = { j.setLong("min_idle_session", it) })
    }
}

@Composable private fun TorFields(j: J) {
    Card2("Tor") {
        GlassTextField("Executable file path", j.str("executable_path"), { j.setStr("executable_path", it, blankRemoves = false) })
        GlassTextField("Data directory", j.str("data_directory"), { j.setStr("data_directory", it, blankRemoves = false) })
        com.sonix21.suinode.ui.screens.shared.CsvField("Extra args", j.arr("extra_args")?.strListX() ?: emptyList(), { parts ->
            if (parts.isEmpty()) j.o.remove("extra_args") else j.o.put("extra_args", jarr(parts))
        })
        com.sonix21.suinode.ui.glass.KeyValueEditor(
            title = "Torrc",
            entries = com.sonix21.suinode.ui.glass.jsonObjectEntries(j.o.optJSONObject("torrc")),
        ) { list -> j.setOrRemove("torrc", com.sonix21.suinode.ui.glass.entriesToJsonObject(list)) }
    }
}

@Composable private fun SshFields(j: J) {
    val keyMode = j.has("private_key") || j.has("private_key_path")
    Card2("SSH") {
        SwitchRow("Use SSH key auth", keyMode, { on ->
            if (on) {
                j.o.remove("user"); j.o.remove("password")
                j.o.put("private_key_path", "")
            } else {
                j.o.remove("private_key"); j.o.remove("private_key_path"); j.o.remove("private_key_passphrase")
                j.o.put("user", "")
            }
        })
        if (keyMode) {
            val usePath = j.has("private_key_path")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectField(label = "Key source", value = if (usePath) "path" else "text",
                    options = listOf(Opt("Path", "path"), Opt("Text", "text")),
                    onChange = { v ->
                        if (v == "path") { j.o.remove("private_key"); j.o.put("private_key_path", "") }
                        else { j.o.remove("private_key_path"); j.o.put("private_key", "") }
                    }, clearable = false)
                if (usePath)
                    GlassTextField("Key path", j.str("private_key_path"), { v -> j.setStr("private_key_path", v, blankRemoves = false) }, Modifier.weight(1f))
            }
            if (!usePath)
                GlassTextField("Private key", j.str("private_key"), { v -> j.setStr("private_key", v, blankRemoves = false) }, singleLine = false, minLines = 3)
            GlassTextField("Passphrase", j.str("private_key_passphrase"), { j.setStr("private_key_passphrase", it) })
        } else {
            GlassTextField("Username", j.str("user"), { j.setStr("user", it) })
            GlassTextField("Password", j.str("password"), { j.setStr("password", it) })
        }
        SwitchRow("Host keys", j.has("host_key"), { on ->
            if (on) j.o.put("host_key", jarr(emptyList<String>())) else j.o.remove("host_key")
        })
        if (j.has("host_key")) {
            com.sonix21.suinode.ui.screens.shared.CsvField("Host keys", j.arr("host_key")?.strListX() ?: emptyList(), { parts ->
                if (parts.isEmpty()) j.o.remove("host_key") else j.o.put("host_key", jarr(parts))
            })
        }
        SwitchRow("Key algorithms", j.has("host_key_algorithms"), { on ->
            if (on) j.o.put("host_key_algorithms", jarr(emptyList<String>())) else j.o.remove("host_key_algorithms")
        })
        if (j.has("host_key_algorithms")) {
            com.sonix21.suinode.ui.screens.shared.CsvField("Algorithms", j.arr("host_key_algorithms")?.strListX() ?: emptyList(), { parts ->
                if (parts.isEmpty()) j.o.remove("host_key_algorithms") else j.o.put("host_key_algorithms", jarr(parts))
            })
        }
        SwitchRow("Client version", j.has("client_version"), { on ->
            if (on) j.o.put("client_version", "SSH-2.0-OpenSSH_7.4p1") else j.o.remove("client_version")
        })
        if (j.has("client_version"))
            GlassTextField("Client version string", j.str("client_version"), { v -> j.setStr("client_version", v, blankRemoves = false) })
    }
}

@Composable
private fun LBFields(j: J, urlTest: Boolean) {
    val session = useSession()
    val data by session.data.collectAsState()
    val tags = remember(data) { session.outboundTags(data) }
    Card2(if (urlTest) "URL Test" else "Selector") {
        MultiSelectField("Outbounds", j.arr("outbounds")?.strListX()?.toSet() ?: emptySet(),
            tags.map { Opt(it, it) }, { sel -> j.setStrs("outbounds", sel.sorted(), emptyRemoves = true) })
        val members = j.arr("outbounds")?.strListX() ?: emptyList()
        if (members.isNotEmpty())
            SelectField(label = "Default outbound", value = j.str("default"),
                options = members.map { Opt(it, it) }, onChange = { j.setStr("default", it) })
        SwitchRow("Interrupt exist connections", j.bool("interrupt_exist_connections"),
            { j.setBool("interrupt_exist_connections", it, onlyTrue = true) })

        if (urlTest) {
            SwitchRow("Custom URL", j.has("url"), { on ->
                if (on) j.o.put("url", "https://www.gstatic.com/generate_204") else j.o.remove("url")
            })
            if (j.has("url")) GlassTextField("URL", j.str("url"), { v -> j.setStr("url", v) })
            DurationField("Interval", j.optStringOrNull("interval"), 's', fallbackSeconds = 3,
                onChange = { j.setOrRemove("interval", it) })
            OptionalNum(j, "tolerance", "Tolerance (ms)", 50)
            DurationField("Idle timeout", j.optStringOrNull("idle_timeout"), 'm', fallbackSeconds = 1800,
                onChange = { j.setOrRemove("idle_timeout", it) })
        }
    }
}

private fun sessionTags(@Suppress("UNUSED_PARAMETER") data: com.sonix21.suinode.data.PanelData): List<String> = emptyList()

@Composable
private fun Card2(title: String, content: @Composable () -> Unit) {
    val g = LocalGlass.current
    GlassCard(contentPadding = 14.dp) {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun OptionalNum(j: J, key: String, label: String, def: Long) {
    SwitchRow(label, j.has(key), { on -> if (on) j.o.put(key, def) else j.o.remove(key) })
    if (j.has(key)) NumberField("$label value", j.long(key), onChange = { v -> j.o.put(key, v ?: def) })
}

private fun org.json.JSONArray.strListX(): List<String> =
    (0 until length()).mapNotNull { runCatching { optString(it) }.getOrNull() }

private fun org.json.JSONObject.deepCopyX(): JSONObject = JSONObject(toString())
