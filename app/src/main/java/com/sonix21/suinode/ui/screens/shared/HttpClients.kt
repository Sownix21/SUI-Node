package com.sonix21.suinode.ui.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Http
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.PanelSession
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.*
import org.json.JSONArray
import org.json.JSONObject

/** Matches QuicFields.vue: revealing the group alone writes no options. */
@Composable
fun TransportTuning(j: J, quic: Boolean = true) {
    val keys = listOf("idle_timeout", "keep_alive_period", "stream_receive_window", "connection_receive_window", "max_concurrent_streams") +
        if (quic) listOf("initial_packet_size", "disable_path_mtu_discovery") else emptyList()
    var show by remember(j.o, quic) { mutableStateOf(keys.any(j::has)) }
    GlassCard(contentPadding = 14.dp) {
        SwitchRow(if (quic) "QUIC tuning" else "HTTP/2 tuning", show, { show = it; if (!it) keys.forEach(j::remove) })
        if (show) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextField("Idle timeout", j.str("idle_timeout"), { j.setStr("idle_timeout", it.trim()) }, hint = "30s")
            GlassTextField("Keep alive period", j.str("keep_alive_period"), { j.setStr("keep_alive_period", it.trim()) }, hint = "0s")
            NumberField("Max concurrent streams", j.long("max_concurrent_streams").takeIf { it > 0 }, { j.setLong("max_concurrent_streams", it) })
            GlassTextField("Stream receive window", j.str("stream_receive_window"), { j.setStr("stream_receive_window", it.trim()) }, hint = "8mb")
            GlassTextField("Connection receive window", j.str("connection_receive_window"), { j.setStr("connection_receive_window", it.trim()) }, hint = "16mb")
            if (quic) {
                NumberField("Initial packet size", j.long("initial_packet_size").takeIf { it > 0 }, { j.setLong("initial_packet_size", it) })
                SwitchRow("Disable path MTU discovery", j.bool("disable_path_mtu_discovery"), { j.setBool("disable_path_mtu_discovery", it, true) })
            }
        }
    }
}

@Composable
fun HttpClientOptions(j: J, session: PanelSession) {
    GlassCard(contentPadding = 14.dp) {
        SelectField("Engine", j.str("engine", "go"), listOf("go", "apple").map { Opt(it,it) }, onChange = { j.setStr("engine", it) })
        SelectField("HTTP version", j.int("version"), listOf(Opt("Automatic",0),Opt("HTTP/1.1",1),Opt("HTTP/2",2),Opt("HTTP/3",3)), clearable = false,
            onChange = { version ->
                j.setInt("version", version)
                val keys = listOf("initial_packet_size","disable_path_mtu_discovery") +
                    if (version == 1) listOf("idle_timeout","keep_alive_period","stream_receive_window","connection_receive_window","max_concurrent_streams") else emptyList()
                if (version != 3) keys.forEach(j::remove)
            })
        if (j.int("version") > 0) SwitchRow("Disable version fallback", j.bool("disable_version_fallback"), { j.setBool("disable_version_fallback",it,true) })
        HeadersCard(j)
    }
    if (j.int("version") != 1) TransportTuning(j, quic = j.int("version") == 3)
    OutTlsEditor(j.ensureObj("tls"))
    DialSection(j, session.outboundTags(), clientMode = true, dnsTags = session.dnsServerTags())
}

@Composable
fun HttpClientReference(holder: J, session: PanelSession) {
    val raw = holder["http_client"]
    val mode = when (raw) { is JSONObject -> "inline"; is String -> "shared"; else -> "default" }
    val tags = session.data.value.config.optJSONArray("http_clients")?.objList()?.map { it.optString("tag") } ?: emptyList()
    GlassCard(contentPadding = 14.dp) {
        SelectField("HTTP client", mode, listOf(Opt("Default","default"),Opt("Shared client","shared"),Opt("Custom options","inline")), clearable = false,
            onChange = { holder["http_client"] = when(it) { "inline" -> JSONObject(); "shared" -> tags.firstOrNull().orEmpty(); else -> null } })
        if (mode == "shared") SelectField("Client tag", raw as String, tags.map { Opt(it,it) }, onChange = { holder["http_client"] = it }, clearable = false)
    }
    if (raw is JSONObject) HttpClientOptions(J(raw), session)
}

@Composable
fun HttpClientsScreen(nav: NavController) {
    val session = useSession(); val data by session.data.collectAsState(); val runner = rememberRunner()
    val config = remember(session) { data.config.deepCopy() }
    var entries: List<JSONObject> by remember { mutableStateOf(config.optJSONArray("http_clients")?.objList()?.map { it.deepCopy() } ?: emptyList()) }
    var selected by remember { mutableStateOf(-1) }
    var draft by remember { mutableStateOf<JSONObject?>(null) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    fun persist(next: List<JSONObject>) = runner.go {
        val candidate = config.deepCopy().put("http_clients", jarr(next))
        session.save("config","set",candidate).getOrThrow()
        entries = next; config.put("http_clients",jarr(next)); draft = null
        ToastBus.show("HTTP clients saved")
    }
    PageScaffold(if (draft == null) "HTTP clients" else "Edit HTTP client", nav, busy = runner.busy,
        draftValue = if (draft != null) ({ draft }) else null,
        onBack = if (draft != null) ({ draft = null }) else null,
        primaryAction = {
            val current = draft
            if (current == null) {
                HeaderPrimaryAction("New HTTP client", type = com.sonix21.suinode.ui.glass.HeaderActionType.New) { selected = -1; draft = jo("tag" to "http-${Rand.seq(3)}") }
            } else {
                val j = J(current)
                HeaderPrimaryAction("Save HTTP client", loading = runner.busy) {
                    if (j.str("tag").isBlank() || entries.withIndex().any { it.index != selected && it.value.optString("tag") == j.str("tag") }) {
                        ToastBus.show("A unique tag is required")
                    } else {
                        val next = entries.toMutableList()
                        if (selected < 0) next.add(current.deepCopy()) else next[selected] = current.deepCopy()
                        persist(next)
                    }
                }
            }
        }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val current = draft
            if (current == null) {
                if (entries.isEmpty()) EmptyState(
                    androidx.compose.material.icons.Icons.Filled.Http,
                    "No shared HTTP clients",
                    "Create reusable connection settings for rule-set downloads and other HTTP requests.",
                )
                entries.forEachIndexed { i, entry -> GlassCard {
                    Text(entry.optString("tag"), color = LocalGlass.current.text)
                    GhostButton("Edit") { selected = i; draft = entry.deepCopy() }
                    GhostButton("Delete", tint = LocalGlass.current.err) { deleteIndex = i }
                } }

            } else {
                val j = J(current)
                GlassTextField("Tag", j.str("tag"), { j.setStr("tag",it,false) })
                HttpClientOptions(j,session)
                AdvancedJsonCard(current) { draft = it }

            }
        }
    }
    deleteIndex?.let { index ->
        ConfirmDialog(title = "Delete HTTP client",
            message = "Remove ${entries[index].optString("tag")}? Update any rule-set or other references before removing a shared client.",
            onConfirm = { persist(entries.filterIndexed { i, _ -> i != index }) },
            onDismiss = { deleteIndex = null })
    }
}
