package com.sonix21.suinode.ui.screens.shared

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.core.setOrRemove
import com.sonix21.suinode.ui.glass.DurationField
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.KeyValueEditor
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.NumberField
import com.sonix21.suinode.ui.glass.Opt
import com.sonix21.suinode.ui.glass.SectionHeader
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.SwitchRow

// ------------------------------------------------------------ network / uot

/** CSV entry field that tolerates typing separators before committing. */
@Composable
fun CsvField(
    label: String,
    values: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(values.joinToString(",")) }
    LaunchedEffect(values) { if (text.split(",").map { it.trim() }.filter { it.isNotEmpty() } != values) text = values.joinToString(",") }
    GlassTextField(
        label = label,
        value = text,
        onValueChange = { raw ->
            text = raw
            val trimmed = raw.trim()
            when {
                trimmed.isEmpty() -> onChange(emptyList())
                trimmed.endsWith(",") -> Unit // wait for completion
                else -> onChange(raw.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            }
        },
        hint = hint,
        modifier = modifier,
    )
}

@Composable
fun NetworkField(data: J) {
    SelectField(
        label = "Network",
        value = data.str("network").ifBlank { "" },
        options = listOf(Opt("TCP/UDP", ""), Opt("TCP", "tcp"), Opt("UDP", "udp")),
        onChange = { v -> data.setStr("network", v?.takeIf { it.isNotBlank() }) },
    )
}

@Composable
fun UotField(data: J) {
    val cur = data.obj("udp_over_tcp")?.int("version") ?: 0
    SelectField(
        label = "UDP over TCP",
        value = cur,
        options = listOf(Opt("Disable", 0), Opt("1", 1), Opt("2", 2)),
        onChange = { v ->
            if (v == null || v <= 0) data.remove("udp_over_tcp")
            else data.o.put("udp_over_tcp", com.sonix21.suinode.core.jo("enabled" to true, "version" to v))
        },
    )
}

// ------------------------------------------------------------------ headers

/** Edits a JSON object map of headers (duplicate keys become arrays). */
@Composable
fun HeadersCard(holder: J, title: String = "Headers") {
    val entries = jsonObjectEntries(if (holder.has("headers")) holder.o.optJSONObject("headers") else null)
    Column {
        KeyValueEditor(title, entries) { list ->
            val mapped = entriesToJsonObject(list)
            holder.setOrRemove("headers", mapped)
        }
    }
}

// -------------------------------------------------------------- multiplex

enum class MuxDirection { IN, OUT }

/** Mirrors components/Multiplex.vue for both directions. */
@Composable
fun MuxSection(holder: J, direction: MuxDirection) {
    val g = LocalGlass.current
    val enabled = holder.obj("multiplex")?.bool("enabled") == true

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Multiplex")
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            "Enable multiplex",
            enabled,
            { on ->
                if (on) holder.o.put("multiplex", com.sonix21.suinode.core.jo("enabled" to true))
                else holder.remove("multiplex")
            },
        )
        if (enabled) {
            val mux = holder.ensureObj("multiplex")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchRow("Padding", mux.bool("padding"), { on -> mux.setBool("padding", on, onlyTrue = true) })
                SwitchRow(
                    "Brutal",
                    mux.obj("brutal")?.bool("enabled") == true,
                    { on ->
                        if (on) mux.o.put("brutal", com.sonix21.suinode.core.jo("enabled" to true, "up_mbps" to 100, "down_mbps" to 100))
                        else mux.remove("brutal")
                    },
                )
                val brutal = mux.obj("brutal")
                if (brutal != null && brutal.bool("enabled")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Upload", brutal.long("up_mbps"), suffix = "Mbps",
                            onChange = { brutal.o.put("up_mbps", it ?: 0) }, modifier = Modifier.weight(1f))
                        NumberField("Download", brutal.long("down_mbps"), suffix = "Mbps",
                            onChange = { brutal.o.put("down_mbps", it ?: 0) }, modifier = Modifier.weight(1f))
                    }
                }

                if (direction == MuxDirection.OUT) {
                    val proto = mux.str("protocol")
                    SelectField(
                        label = "Protocol",
                        value = proto.ifBlank { "" },
                        options = listOf(Opt("", ""), Opt("smux", "smux"), Opt("yamux", "yamux"), Opt("h2mux", "h2mux")),
                        onChange = { v -> mux.setStr("protocol", v?.takeIf { it.isNotBlank() }) },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Max connections", mux.long("max_connections").takeIf { it > 0 },
                            onChange = { mux.setLong("max_connections", it) }, modifier = Modifier.fillMaxWidth())
                        NumberField("Min streams", mux.long("min_streams").takeIf { it > 0 },
                            onChange = { mux.setLong("min_streams", it) }, modifier = Modifier.fillMaxWidth())
                        NumberField("Max streams", mux.long("max_streams").takeIf { it > 0 },
                            onChange = { mux.setLong("max_streams", it) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- transport

val TRANSPORT_TYPES = listOf("http", "ws", "quic", "grpc", "httpupgrade")

/** Mirrors components/Transport.vue — presence of transport.type toggles. */
@Composable
fun TransportSection(holder: J) {
    val t = holder.obj("transport")
    val enabled = t != null && t.has("type")

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Transport")
        Spacer(Modifier.height(8.dp))
        SwitchRow("Enable transport layer", enabled, { on ->
            if (on) holder.o.put("transport", com.sonix21.suinode.core.jo("type" to "http"))
            else holder.remove("transport")
        })
        if (!enabled) return@GlassCard

        val tr = holder.ensureObj("transport")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectField(
                label = "Type",
                value = tr.str("type"),
                options = TRANSPORT_TYPES.map { Opt(it, it) },
                onChange = { v -> if (v != null) holder.o.put("transport", jo("type" to v)) },
                clearable = false,
            )
            when (tr.str("type")) {
                "http" -> HttpTransportFields(tr)
                "ws" -> WsTransportFields(tr)
                "grpc" -> GrpcTransportFields(tr)
                "httpupgrade" -> HttpUpgradeTransportFields(tr)
                // quic: no extra fields (parity with panel UI)
            }
        }
    }
}

@Composable
private fun HttpTransportFields(tr: J) {
    CsvField("Hosts", tr.arr("host")?.strListX() ?: emptyList(), { parts ->
        if (parts.isEmpty()) tr.remove("host") else tr.o.put("host", jarr(parts))
    })
    GlassTextField("Path", tr.str("path"), { v -> tr.setStr("path", v) })
    SelectField(
        label = "Request Method",
        value = tr.str("method"),
        options = listOf("POST", "GET", "PUT", "PATCH", "DELETE").map { Opt(it, it) },
        onChange = { v -> tr.setStr("method", v) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DurationField("Idle timeout", tr.optStringOrNull("idle_timeout"), 's',
            onChange = { tr.setOrRemove("idle_timeout", it) }, modifier = Modifier.weight(1f))
        DurationField("Ping timeout", tr.optStringOrNull("ping_timeout"), 's',
            onChange = { tr.setOrRemove("ping_timeout", it) }, modifier = Modifier.weight(1f))
    }
    HeadersCard(tr)
}

@Composable
private fun WsTransportFields(tr: J) {
    // seed defaults like the panel does on mount
    if (!tr.has("early_data_header_name")) tr.o.put("early_data_header_name", "Sec-WebSocket-Protocol")
    if (!tr.has("path")) tr.o.put("path", "/")
    GlassTextField("Path", tr.str("path"), { v -> tr.setStr("path", v) })
    GlassTextField("Host", tr.obj("headers")?.str("Host") ?: "", { v ->
        val headers = tr.o.optJSONObject("headers") ?: org.json.JSONObject()
        if (v.isBlank()) headers.remove("Host") else headers.put("Host", v)
        if (headers.length() == 0) tr.remove("headers") else tr.o.put("headers", headers)
    })
    NumberField("Max early data", tr.long("max_early_data").takeIf { it > 0 },
        onChange = { tr.setLong("max_early_data", it) })
    GlassTextField("Early data header name", tr.str("early_data_header_name"),
        { v -> tr.setStr("early_data_header_name", v) })
    HeadersCard(tr)
}

@Composable
private fun GrpcTransportFields(tr: J) {
    GlassTextField("Service name", tr.str("service_name"), { v -> tr.setStr("service_name", v) })
    SwitchRow("Permit without stream", tr.bool("permit_without_stream"),
        { on -> tr.setBool("permit_without_stream", on, onlyTrue = true) })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DurationField("Idle timeout", tr.optStringOrNull("idle_timeout"), 's', fallbackSeconds = 15,
            onChange = { tr.setOrRemove("idle_timeout", it) }, modifier = Modifier.weight(1f))
        DurationField("Ping timeout", tr.optStringOrNull("ping_timeout"), 's', fallbackSeconds = 15,
            onChange = { tr.setOrRemove("ping_timeout", it) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HttpUpgradeTransportFields(tr: J) {
    GlassTextField("Hosts", tr.str("host"), { v -> tr.setStr("host", v) })
    GlassTextField("Path", tr.str("path"), { v -> tr.setStr("path", v) })
    HeadersCard(tr)
}

private fun org.json.JSONArray.strListX(): List<String> =
    (0 until length()).mapNotNull { runCatching { optString(it) }.getOrNull() }

// -------------------------------------------------------------------- dial

/** Dial fields (shared by endpoints/outbounds/shadowtls handshake). */
@Composable
fun DialSection(dialHolder: J, detourOptions: List<String>, clientMode: Boolean = false, dnsTags: List<String> = emptyList()) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Dial")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val detour = dialHolder.has("detour")
            val bind = dialHolder.has("bind_interface")
            val ipv4 = dialHolder.has("inet4_bind_address")
            val ipv6 = dialHolder.has("inet6_bind_address")
            val noPort = dialHolder.has("bind_address_no_port")
            val mark = dialHolder.has("routing_mark")
            val reuse = dialHolder.has("reuse_addr")
            val tcp = dialHolder.has("tcp_fast_open") || dialHolder.has("tcp_multi_path")
            val udp = dialHolder.has("udp_fragment")
            val timeout = dialHolder.has("connect_timeout")
            val keepAlive = dialHolder.has("disable_tcp_keep_alive") || dialHolder.has("tcp_keep_alive") || dialHolder.has("tcp_keep_alive_interval")
            val resolver = dialHolder.has("domain_resolver")

            SectionHeader("Dial options")
            if (!clientMode) {
                SwitchRow("Detour", detour, { on -> if (on) dialHolder.o.put("detour", detourOptions.firstOrNull() ?: "") else dialHolder.remove("detour") })
                SwitchRow("Bind interface", bind, { on -> if (on) dialHolder.o.put("bind_interface", "") else dialHolder.remove("bind_interface") })
                SwitchRow("Bind IPv4", ipv4, { on -> if (on) dialHolder.o.put("inet4_bind_address", "") else dialHolder.remove("inet4_bind_address") })
                SwitchRow("Bind IPv6", ipv6, { on -> if (on) dialHolder.o.put("inet6_bind_address", "") else dialHolder.remove("inet6_bind_address") })
                SwitchRow("Bind address no port", noPort, { on -> if (on) dialHolder.o.put("bind_address_no_port", true) else dialHolder.remove("bind_address_no_port") })
                SwitchRow("Linux routing mark", mark, { on -> if (on) dialHolder.o.put("routing_mark", 0) else dialHolder.remove("routing_mark") })
                SwitchRow("Reuse address", reuse, { on -> if (on) dialHolder.o.put("reuse_addr", true) else dialHolder.remove("reuse_addr") })
            }
            SwitchRow("TCP options", tcp, { on -> if (on) { dialHolder.o.put("tcp_fast_open", false); dialHolder.o.put("tcp_multi_path", false) } else { dialHolder.remove("tcp_fast_open"); dialHolder.remove("tcp_multi_path") } })
            SwitchRow("UDP options", udp, { on -> if (on) dialHolder.o.put("udp_fragment", false) else dialHolder.remove("udp_fragment") })
            SwitchRow("Connect timeout", timeout, { on -> if (on) dialHolder.o.put("connect_timeout", "5s") else dialHolder.remove("connect_timeout") })
            SwitchRow("TCP keep alive options", keepAlive, { on -> if (on) { dialHolder.o.put("tcp_keep_alive", "5m"); dialHolder.o.put("tcp_keep_alive_interval", "75s") } else { dialHolder.remove("disable_tcp_keep_alive"); dialHolder.remove("tcp_keep_alive"); dialHolder.remove("tcp_keep_alive_interval") } })
            if (!clientMode) SwitchRow("Domain resolver", resolver, { on -> if (on) dialHolder.o.put("domain_resolver", dnsTags.firstOrNull() ?: "") else dialHolder.remove("domain_resolver") })

            if (!clientMode && detour && detourOptions.isNotEmpty()) {
                SelectField(
                    label = "Detour (forward via)",
                    value = dialHolder.str("detour").ifBlank { "" },
                    options = listOf(Opt("", "")) + detourOptions.map { Opt(it, it) },
                    onChange = { v -> dialHolder.setStr("detour", v?.takeIf { it.isNotBlank() }) },
                )
            }
            if (!clientMode) {
                if (bind) GlassTextField("Bind to network interface", dialHolder.str("bind_interface"), { v -> dialHolder.setStr("bind_interface", v, false) })
                if (ipv4) GlassTextField("Bind IPv4", dialHolder.str("inet4_bind_address"), { v -> dialHolder.setStr("inet4_bind_address", v, false) })
                if (ipv6) GlassTextField("Bind IPv6", dialHolder.str("inet6_bind_address"), { v -> dialHolder.setStr("inet6_bind_address", v, false) })
                if (noPort) SwitchRow("Bind address no port value", dialHolder.bool("bind_address_no_port"), { dialHolder.o.put("bind_address_no_port", it) })
                if (mark) NumberField("Linux routing mark", dialHolder.long("routing_mark"), onChange = { dialHolder.o.put("routing_mark", it ?: 0) })
                if (reuse) SwitchRow("Reuse address value", dialHolder.bool("reuse_addr"), { dialHolder.o.put("reuse_addr", it) })
            }
            if (tcp) {
                SwitchRow("TCP fast open", dialHolder.bool("tcp_fast_open"), { dialHolder.o.put("tcp_fast_open", it) })
                SwitchRow("TCP multi path", dialHolder.bool("tcp_multi_path"), { dialHolder.o.put("tcp_multi_path", it) })
            }
            if (udp) SwitchRow("UDP fragment", dialHolder.bool("udp_fragment"), { dialHolder.o.put("udp_fragment", it) })
            if (timeout) DurationField("Connect timeout value", dialHolder.optStringOrNull("connect_timeout"), 's', fallbackSeconds = 5, onChange = { dialHolder.setOrRemove("connect_timeout", it) })
            if (keepAlive) {
              SwitchRow("Disable TCP keep alive", dialHolder.bool("disable_tcp_keep_alive"), { dialHolder.o.put("disable_tcp_keep_alive", it) })
              if (!dialHolder.bool("disable_tcp_keep_alive")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DurationField("TCP keep alive", dialHolder.optStringOrNull("tcp_keep_alive"), 'm', fallbackSeconds = 300,
                    onChange = { dialHolder.setOrRemove("tcp_keep_alive", it) }, modifier = Modifier.weight(1f))
                DurationField("Keep alive interval", dialHolder.optStringOrNull("tcp_keep_alive_interval"), 's', fallbackSeconds = 75,
                    onChange = { dialHolder.setOrRemove("tcp_keep_alive_interval", it) }, modifier = Modifier.weight(1f))
              }
            }
            if (!clientMode && resolver) {
                SelectField(
                    label = "Domain resolver (DNS server)",
                    value = dialHolder.str("domain_resolver").ifBlank { "" },
                    options = listOf(Opt("", "")) + dnsTags.map { Opt(it, it) },
                    onChange = { v -> dialHolder.setStr("domain_resolver", v?.takeIf { it.isNotBlank() }) },
                )
            }
        }
    }
}
