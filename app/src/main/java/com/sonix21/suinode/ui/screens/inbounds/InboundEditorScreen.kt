package com.sonix21.suinode.ui.screens.inbounds

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.Rand
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.data.PanelSession
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
import com.sonix21.suinode.ui.screens.clients.TabChip
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.shared.DialSection
import com.sonix21.suinode.ui.screens.shared.MuxDirection
import com.sonix21.suinode.ui.screens.shared.MuxSection
import com.sonix21.suinode.ui.screens.shared.TransportSection
import com.sonix21.suinode.ui.screens.useSession
import org.json.JSONArray
import org.json.JSONObject

// ------------------------------------------------------------- type registry

val IN_TYPES = listOf(
    "direct", "mixed", "socks", "http", "shadowsocks", "snell", "vmess", "trojan", "naive",
    "hysteria", "shadowtls", "tuic", "hysteria2", "vless", "anytls", "tun", "redirect", "tproxy",
    "cloudflared",
)

private val HAS_TABS = setOf("socks", "http", "mixed", "shadowsocks", "vmess", "shadowtls", "trojan", "hysteria", "vless", "anytls", "tuic", "hysteria2", "naive")
private val HAS_USERS = setOf("mixed", "socks", "http", "shadowsocks", "vmess", "trojan", "naive", "hysteria", "shadowtls", "tuic", "hysteria2", "vless", "anytls")
val IN_HAS_TLS = setOf("http", "vmess", "trojan", "naive", "hysteria", "tuic", "hysteria2", "vless", "anytls")
private val ONLY_TLS = setOf("hysteria", "hysteria2", "tuic", "naive", "anytls")
private val MUX_AVAILABLE = setOf("vless", "vmess", "trojan", "shadowsocks")
private val TRANSPORT_AVAILABLE = setOf("vless", "vmess", "trojan")
private val HAS_ADDR = HAS_TABS

fun createDefaultInbound(type: String): JSONObject = (when (type) {
    "direct" -> jo()
    "mixed" -> jo()
    "socks" -> jo()
    "http" -> jo("tls_id" to 0L)
    "shadowsocks" -> jo("method" to "none")
    "snell" -> jo("version" to 6, "psk" to Rand.seq(32))
    "vmess" -> jo("tls_id" to 0L, "transport" to JSONObject())
    "trojan" -> jo("tls_id" to 0L, "transport" to JSONObject())
    "naive" -> jo("tls_id" to 0L)
    "hysteria" -> jo("up_mbps" to 100L, "down_mbps" to 100L, "tls_id" to 0L)
    "shadowtls" -> jo("version" to 3, "handshake" to jo("server_port" to 443L), "handshake_for_server_name" to JSONObject())
    "tuic" -> jo("congestion_control" to "cubic", "tls_id" to 0L)
    "hysteria2" -> jo("tls_id" to 0L)
    "vless" -> jo("tls_id" to 0L, "transport" to JSONObject())
    "anytls" -> jo(
        "tls_id" to 0L,
        "padding_scheme" to jarr(
            listOf(
                "stop=8", "0=30-30", "1=100-400",
                "2=400-500,c,500-1000,c,500-1000,c,500-1000,c,500-1000",
                "3=9-9,500-1000", "4=500-1000", "5=500-1000", "6=500-1000", "7=500-1000",
            )
        ),
    )
    "tun" -> jo("mtu" to 9000L, "stack" to "system", "udp_timeout" to "5m", "auto_route" to false)
    "redirect" -> jo()
    "tproxy" -> jo()
    "cloudflared" -> jo("token" to "", "protocol" to "auto")
    else -> jo()
}).apply { put("type", type) }

// ------------------------------------------------------------------ screen

@Composable
fun InboundEditorScreen(nav: NavController, id: Long) {
    val g = LocalGlass.current
    val session = useSession()
    val runner = rememberRunner()

    var loaded by remember { mutableStateOf(id <= 0L) }
    var side by remember { mutableIntStateOf(0) } // 0 server / 1 client
    var obj by remember { mutableStateOf<JSONObject?>(null) }
    var initUsersModel by remember { mutableStateOf("none") }
    var initUsersValues by remember { mutableStateOf(emptySet<String>()) }
    var typeVersion by remember { mutableIntStateOf(0) } // bump to resync CsvFields after type change

    var loadAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(id, loadAttempt) {
        if (id > 0) {
            runner.go {
                val full = session.fetchRecord("inbounds", id)
                if (full.optStringOrNull("type") in HAS_TABS && !full.has("out_json")) full.put("out_json", JSONObject())
                obj = full; loaded = true
            }
        } else {
            val port = Rand.int(10000, 60000).toLong()
            val base = createDefaultInbound("direct")
            base.put("id", 0L); base.put("tag", "direct-$port"); base.put("listen", "::"); base.put("listen_port", port)
            base.put("addrs", JSONArray()); base.put("out_json", JSONObject())
            obj = base; loaded = true
        }
    }

    val o = obj
    if (o == null) {
        RecordLoadingPage("Inbound", nav, runner) { loadAttempt++ }
        return
    }
    val j = J(o)
    val type = j.str("type")

    fun changeType(newType: String) {
        if (!j.long("listen_port").let { it in 1..65535 }) j.o.put("listen_port", Rand.int(10000, 60000))
        // tun brings its own interface options; cloudflared dials out to the
        // Cloudflare edge and sing-box rejects listen fields on both.
        val noListen = newType == "tun" || newType == "cloudflared"
        val keepTag = if (id > 0) o.optString("tag")
            else if (noListen) "$newType-${Rand.seq(3)}"
            else "$newType-${o.optLongOr("listen_port")}"
        val prevId = o.optLongOr("id")
        val prevListen = o.optStringOrNull("listen") ?: "::"
        val prevPort = o.optLongOr("listen_port")
        val fresh = createDefaultInbound(newType)
        fresh.put("id", prevId); fresh.put("tag", keepTag)
        if (!noListen) { fresh.put("listen", prevListen); fresh.put("listen_port", prevPort) }
        if (newType in HAS_ADDR) { fresh.put("addrs", JSONArray()); fresh.put("out_json", JSONObject()) }
        // preserve tls selection if new type supports it
        if (newType in IN_HAS_TLS && o.optLongOr("tls_id") > 0) fresh.put("tls_id", o.optLongOr("tls_id"))
        obj = fresh
        initUsersModel = "none"; initUsersValues = emptySet()
        side = 0
        typeVersion++
    }

    PageScaffold(
        title = if (id == 0L) "New Inbound" else "Edit Inbound",
        draftValue = { obj },
        subtitle = "$type · ${o.optString("tag")}",
        nav = nav,
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction(if (id == 0L) "Create inbound" else "Save changes", loading = runner.busy) {
                    val tag = j.str("tag")
                    val port = j.long("listen_port")
                    when {
                        tag.isBlank() -> ToastBus.show("tag is required")
                        type !in setOf("tun", "cloudflared") && (port < 1 || port > 65535) -> ToastBus.show("port must be 1–65535")
                        type in ONLY_TLS && j.long("tls_id") == 0L -> ToastBus.show("$type requires a TLS template")
                        session.data.value.inbounds.any {
                            it.optString("tag") == tag && (if (id == 0L) true else it.optLongOr("id") != id)
                        } -> ToastBus.show("duplicate tag")
                        else -> runner.go {
                            if (id == 0L && initUsersModel != "none" && type in HAS_USERS) {
                                val clients = session.data.value.clients
                                val ids: List<Long> = when (initUsersModel) {
                                    "all" -> clients.map { it.optLongOr("id") }
                                    "group" -> clients.filter { it.optString("group") in initUsersValues }.map { it.optLongOr("id") }
                                    else -> initUsersValues.mapNotNull { it.toLongOrNull() }
                                }
                                val r = session.save("inbounds", "new", j.o, ids)
                                if (r.isSuccess) { ToastBus.show("created ✓"); nav.pop() } else throw Exception(r.exceptionOrNull()?.message)
                            } else {
                                val r = session.save("inbounds", if (id == 0L) "new" else "edit", j.o)
                                if (r.isSuccess) { ToastBus.show("saved ✓"); nav.pop() } else throw Exception(r.exceptionOrNull()?.message)
                            }
                        }
                    }
                }
        },
    ) {
        Column(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ---- basics row
                GlassCard(contentPadding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectField(
                            label = "Type",
                            value = type,
                            options = IN_TYPES.map { Opt(it, it) },
                            onChange = { v -> v?.let(::changeType) },
                            clearable = false,
                        )
                        GlassTextField("Tag", j.str("tag"), { v -> j.setStr("tag", v, blankRemoves = false) })
                    }
                }

                // ---- tabs
                if (type in HAS_TABS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TabChip("Server side", side == 0, Modifier.weight(1f)) { side = 0 }
                        TabChip("Client side", side == 1, Modifier.weight(1f)) { side = 1 }
                    }
                }

                if (side == 0 || type !in HAS_TABS) {
                    ListenSection(j, type, session)
                    ProtocolCard(j, type, id, typeVersion)
                    if (type in setOf("hysteria", "hysteria2", "tuic")) com.sonix21.suinode.ui.screens.shared.TransportTuning(j)
                    if (type in TRANSPORT_AVAILABLE) TransportSection(j)
                    if (id == 0L && type in HAS_USERS && usersVisible(type, j)) UsersPicker(session, initUsersModel, initUsersValues,
                        onModel = { initUsersModel = it; initUsersValues = emptySet() },
                        onValues = { initUsersValues = it })
                    if (type in IN_HAS_TLS) TlsTemplatePicker(j, session, required = type in ONLY_TLS)
                    if (type in MUX_AVAILABLE) MuxSection(j, MuxDirection.IN)
                } else {
                    ClientSideTab(j, session, type, typeVersion)
                }
                com.sonix21.suinode.ui.screens.AdvancedJsonCard(j.o) { obj = it }
            }
        }
    }
}

private fun usersVisible(type: String, j: J): Boolean {
    if (type == "shadowtls" && j.int("version") < 3) return false
    if (type == "shadowsocks" && j.bool("managed")) return false
    return true
}

// ------------------------------------------------------------------ listen

@Composable
fun ListenSection(j: J, type: String, session: PanelSession) {
    if (type == "tun" || type == "cloudflared") return
    val g = LocalGlass.current
    val data by session.data.collectAsState()
    val detourOptions = remember(data) { session.inboundTags(data) }

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Listen")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField("Address", j.str("listen"), { v -> j.setStr("listen", v, blankRemoves = false) }, modifier = Modifier.weight(1.4f))
                NumberField("Port", j.long("listen_port"), suffix = "", modifier = Modifier.weight(1f),
                    onChange = { p -> j.o.put("listen_port", p ?: 0L) })
            }
            val detourEnabled = j.has("detour")
            val tcpEnabled = j.has("tcp_fast_open") || j.has("tcp_multi_path")
            val udpEnabled = j.has("udp_fragment") || j.has("udp_timeout")
            val keepAliveEnabled = j.has("disable_tcp_keep_alive") || j.has("tcp_keep_alive") || j.has("tcp_keep_alive_interval")
            SectionHeader("Listen options")
            SwitchRow("Detour", detourEnabled, { on -> if (on) j.o.put("detour", detourOptions.firstOrNull() ?: "") else j.remove("detour") })
            SwitchRow("TCP options", tcpEnabled, { on -> if (on) { j.o.put("tcp_fast_open", false); j.o.put("tcp_multi_path", false) } else { j.remove("tcp_fast_open"); j.remove("tcp_multi_path") } })
            SwitchRow("UDP options", udpEnabled, { on -> if (on) { j.o.put("udp_fragment", false); j.o.put("udp_timeout", "5m") } else { j.remove("udp_fragment"); j.remove("udp_timeout") } })
            SwitchRow("TCP keep alive options", keepAliveEnabled, { on -> if (on) { j.o.put("tcp_keep_alive", "5m"); j.o.put("tcp_keep_alive_interval", "75s") } else { j.remove("disable_tcp_keep_alive"); j.remove("tcp_keep_alive"); j.remove("tcp_keep_alive_interval") } })
            if (detourEnabled && detourOptions.isNotEmpty()) {
                SelectField(
                    label = "Detour (forward to another inbound)",
                    value = j.str("detour"),
                    options = (listOf("") + detourOptions).map { Opt(it.ifBlank { "none" }, it) },
                    onChange = { v -> j.setStr("detour", v?.takeIf { it.isNotBlank() }) },
                )
            }
            if (tcpEnabled) {
                SwitchRow("TCP fast open", j.bool("tcp_fast_open"), { j.o.put("tcp_fast_open", it) })
                SwitchRow("TCP multi path", j.bool("tcp_multi_path"), { j.o.put("tcp_multi_path", it) })
            }
            if (udpEnabled) {
                SwitchRow("UDP fragment", j.bool("udp_fragment"), { j.o.put("udp_fragment", it) })
                com.sonix21.suinode.ui.glass.DurationField("UDP NAT expiration", j.optStringOrNull("udp_timeout"), 'm', fallbackSeconds = 300,
                    onChange = { j.setOrRemove("udp_timeout", it) })
            }
            if (keepAliveEnabled) {
                SwitchRow("Disable TCP keep alive", j.bool("disable_tcp_keep_alive"), { j.o.put("disable_tcp_keep_alive", it) })
            if (!j.bool("disable_tcp_keep_alive")) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.sonix21.suinode.ui.glass.DurationField("TCP keep alive", j.optStringOrNull("tcp_keep_alive"), 'm', fallbackSeconds = 300,
                        onChange = { j.setOrRemove("tcp_keep_alive", it) }, modifier = Modifier.weight(1f))
                    com.sonix21.suinode.ui.glass.DurationField("Keep alive interval", j.optStringOrNull("tcp_keep_alive_interval"), 's', fallbackSeconds = 75,
                        onChange = { j.setOrRemove("tcp_keep_alive_interval", it) }, modifier = Modifier.weight(1f))
                }
            }
            }
        }
    }
}

// ------------------------------------------------------------- users picker

@Composable
private fun UsersPicker(session: PanelSession, model: String, values: Set<String>, onModel: (String) -> Unit, onValues: (Set<String>) -> Unit) {
    val g = LocalGlass.current
    val data by session.data.collectAsState()
    val groups = session.groups(data)
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Initial users")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectField(
                label = "Assign clients",
                value = model,
                options = listOf(Opt("None", "none"), Opt("All", "all"), Opt("Groups", "group"), Opt("Pick clients", "client")),
                onChange = { onModel(it ?: "none") },
                clearable = false,
            )
            if (model == "group") MultiSelectField("Groups", values, groups.map { Opt(it, it) }, onValues)
            if (model == "client")
                MultiSelectField("Clients", values,
                    data.clients.map { Opt(it.optString("name"), it.optLongOr("id").toString()) }, onValues)
        }
    }
}

// -------------------------------------------------------------- tls picker

@Composable
fun TlsTemplatePicker(inboundHolder: J, session: PanelSession, required: Boolean) {
    val data by session.data.collectAsState()
    SelectField(
        label = if (required) "TLS template (required)" else "TLS template",
        value = inboundHolder.long("tls_id"),
        options = listOf(Opt("None", 0L)) + data.tlsConfigs.map { Opt(it.optString("name"), it.optLongOr("id")) },
        onChange = { v -> inboundHolder.o.put("tls_id", v ?: 0L) },
        clearable = false,
    )
}
