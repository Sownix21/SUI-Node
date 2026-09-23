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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.Rand
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.ui.glass.DurationField
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.NumberField
import com.sonix21.suinode.ui.glass.Opt
import com.sonix21.suinode.ui.glass.SectionHeader
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.screens.shared.DialSection
import com.sonix21.suinode.ui.screens.shared.NetworkField

val SS_METHODS = listOf(
    "none", "aes-128-gcm", "aes-192-gcm", "aes-256-gcm",
    "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305",
    "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
)

fun genSsPassword(method: String): String? = when {
    method == "2022-blake3-aes-128-gcm" -> Rand.ssPassword(16)
    method.startsWith("2022") -> Rand.ssPassword(32)
    method == "none" -> null
    else -> Rand.seq(10)
}

/** Renders the protocol-specific server-side card. */
@Composable
fun ProtocolCard(j: J, type: String, id: Long, typeVersion: Int) {
    when (type) {
        "direct" -> DirectCard(j)
        "shadowsocks" -> ShadowsocksCard(j)
        "snell" -> SnellCard(j)
        "hysteria" -> HysteriaCard(j)
        "hysteria2" -> Hysteria2Card(j)
        "naive" -> NaiveCard(j)
        "shadowtls" -> ShadowTlsCard(j, id, typeVersion)
        "tuic" -> TuicCard(j)
        "anytls" -> AnyTlsCard(j)
        "tun" -> TunCard(j)
        "tproxy" -> TProxyCard(j)
        "cloudflared" -> CloudflaredCard(j)
        else -> {}
    }
}

// ------------------------------------------------------------------- direct

@Composable
private fun DirectCard(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Direct")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NetworkField(j)
            GlassTextField("Override address", j.str("override_address"), { v -> j.setStr("override_address", v) })
            NumberField("Override port", j.long("override_port").takeIf { it > 0 },
                onChange = { v -> j.setLong("override_port", v) })
        }
    }
}

// -------------------------------------------------------------- shadowsocks

@Composable
private fun ShadowsocksCard(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Shadowsocks")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectField(
                label = "Method",
                value = j.str("method"),
                options = SS_METHODS.map { Opt(it, it) },
                onChange = { m ->
                    val method = m ?: "none"
                    j.o.put("method", method)
                    val pw = genSsPassword(method)
                    if (pw == null) j.o.remove("password") else j.o.put("password", pw)
                },
                clearable = false,
            )
            if (j.str("method") != "none") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField("Password", j.str("password"), { v ->
                        if (v.isBlank()) j.o.remove("password") else j.o.put("password", v)
                    }, modifier = Modifier.weight(1f))
                    IconGhostButton(Icons.Filled.Refresh, {
                        genSsPassword(j.str("method"))?.let { j.o.put("password", it) }
                    }, contentDesc = "regenerate password")
                }
            }
            SwitchRow("Manageable (managed mode)", j.bool("managed"), { on -> j.setBool("managed", on, onlyTrue = true) })
            NetworkField(j)
        }
    }
}

// ----------------------------------------------------------------- hysteria

@Composable
private fun HysteriaCard(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Hysteria")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Upload", j.long("up_mbps").takeIf { it > 0 } ?: 100L, suffix = "Mbps",
                    onChange = { j.o.put("up_mbps", it ?: 0) }, modifier = Modifier.weight(1f))
                NumberField("Download", j.long("down_mbps").takeIf { it > 0 } ?: 100L, suffix = "Mbps",
                    onChange = { j.o.put("down_mbps", it ?: 0) }, modifier = Modifier.weight(1f))
            }
            GlassTextField("Obfuscated password", j.str("obfs"), { v -> j.setStr("obfs", v) })
            SwitchRow("Disable MTU discovery", j.bool("disable_mtu_discovery"),
                { j.setBool("disable_mtu_discovery", it, onlyTrue = true) })
            OptionalNumber(j, "recv_window_conn", "Receive window conn", 15728640L)
            OptionalNumber(j, "recv_window_client", "Receive window client", 67108864L)
            OptionalNumber(j, "max_conn_client", "Max connections per client", 1024L)
        }
    }
}

@Composable
private fun OptionalNumber(j: J, key: String, label: String, def: Long) {
    SwitchRow(label, j.has(key), { on -> if (on) j.o.put(key, def) else j.o.remove(key) })
    if (j.has(key)) {
        NumberField("$label value", j.long(key), suffix = "", onChange = { v -> j.o.put(key, v ?: def) })
    }
}

// ---------------------------------------------------------------- hysteria2

@Composable
private fun Hysteria2Card(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Hysteria2")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Ignore client bandwidth", j.bool("ignore_client_bandwidth"),
                { j.setBool("ignore_client_bandwidth", it, onlyTrue = true) })
            if (!j.bool("ignore_client_bandwidth")) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Upload", j.long("up_mbps").takeIf { it > 0 }, suffix = "Mbps",
                        onChange = { j.setLong("up_mbps", it) }, modifier = Modifier.weight(1f))
                    NumberField("Download", j.long("down_mbps").takeIf { it > 0 }, suffix = "Mbps",
                        onChange = { j.setLong("down_mbps", it) }, modifier = Modifier.weight(1f))
                }
            }
            SwitchRow("Obfuscation", j.has("obfs"), { on ->
                if (on) j.o.put("obfs", jo("type" to "salamander", "password" to ""))
                else j.o.remove("obfs")
            })
            if (j.has("obfs")) {
                val obfs = j.ensureObj("obfs")
                GlassTextField("Obfuscated password", obfs.str("password"), { v ->
                    if (v.isBlank()) obfs.o.remove("password") else obfs.o.put("password", v)
                })
            }

            // masquerade: string (simple) or object {type,...}
            SwitchRow("Masquerade", j.has("masquerade"), { on ->
                if (on) j.o.put("masquerade", "") else j.o.remove("masquerade")
            })
            if (j.has("masquerade")) MasqueradeEditor(j)
        }
    }
}

@Composable
private fun MasqueradeEditor(holder: J) {
    val raw = holder.o.opt("masquerade")
    val mode = when (raw) {
        is String -> "simple"
        is org.json.JSONObject -> when (raw.optString("type")) {
            "file" -> "file"; "proxy" -> "proxy"; "string" -> "string"
            else -> "simple"
        }
        else -> "simple"
    }
    SelectField(
        label = "Masquerade type",
        value = mode,
        options = listOf(
            Opt("Simple", "simple"), Opt("File server", "file"),
            Opt("Reverse proxy", "proxy"), Opt("Fixed response", "string"),
        ),
        onChange = { v ->
            val nv = v ?: return@SelectField
            holder.o.remove("masquerade")
            when (nv) {
                "simple" -> holder.o.put("masquerade", "")
                else -> holder.o.put("masquerade", jo("type" to nv))
            }
        },
        clearable = false,
    )
    when (mode) {
        "simple" -> {
            val cur = if (raw is String) raw else ""
            GlassTextField("HTTP3 server on auth fails", cur, { v ->
                holder.o.put("masquerade", v)
            }, hint = "file:///var/www | http://127.0.0.1:8080")
        }
        "file" -> {
            val m = J(raw as org.json.JSONObject)
            GlassTextField("File server root directory", m.str("directory"),
                { v -> if (v.isBlank()) m.o.remove("directory") else m.o.put("directory", v) }, hint = "/var/www")
        }
        "proxy" -> {
            val m = J(raw as org.json.JSONObject)
            GlassTextField("Target URL", m.str("url"), { v -> m.setStr("url", v) }, hint = "http://example.com:8080")
            SwitchRow("Rewrite host", m.bool("rewrite_host"), { m.setBool("rewrite_host", it, onlyTrue = true) })
        }
        "string" -> {
            val m = J(raw as org.json.JSONObject)
            NumberField("HTTP code", m.long("status_code").takeIf { it in 100..599 },
                onChange = { v -> if (v != null) m.o.put("status_code", v.toInt()) })
            GlassTextField("Content", m.str("content"), { v -> m.setStr("content", v, blankRemoves = false) })
            com.sonix21.suinode.ui.screens.shared.HeadersCard(m)
        }
    }
}

// -------------------------------------------------------------------- naive

@Composable
private fun NaiveCard(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Naive")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NetworkField(j)
            SelectField(
                label = "QUIC congestion control",
                value = j.str("quic_congestion_control"),
                options = listOf(
                    Opt("BBR", "bbr"), Opt("BBR Standard", "bbr_standard"), Opt("BBRv2", "bbr2"),
                    Opt("BBRv2 variant", "bbr2_variant"), Opt("Cubic", "cubic"), Opt("New Reno", "reno"),
                ),
                onChange = { v -> j.setStr("quic_congestion_control", v) },
            )
        }
    }
}

// ---------------------------------------------------------------- shadowtls

@Composable
private fun ShadowTlsCard(j: J, id: Long, typeVersion: Int) {
    val g = LocalGlass.current
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("ShadowTLS")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectField(
                label = "Version",
                value = j.int("version").takeIf { it > 0 } ?: 3,
                options = listOf(Opt("1", 1), Opt("2", 2), Opt("3", 3)),
                onChange = { v ->
                    val nv = v ?: 3
                    j.o.put("version", nv)
                    when (nv) {
                        1 -> { j.o.remove("password"); j.o.remove("handshake_for_server_name"); j.o.remove("wildcard_sni") }
                        2 -> {
                            if (!j.has("password")) j.o.put("password", "")
                            if (!j.has("handshake_for_server_name")) j.o.put("handshake_for_server_name", org.json.JSONObject())
                            j.o.remove("wildcard_sni")
                        }
                        else -> {
                            j.o.remove("password")
                            if (!j.has("handshake_for_server_name")) j.o.put("handshake_for_server_name", org.json.JSONObject())
                            if (!j.has("wildcard_sni")) j.o.put("wildcard_sni", "")
                        }
                    }
                },
                clearable = false,
            )
            if (id > 0) Text("version locked after creation", color = LocalGlass.current.textFaint, fontSize = 10.5.sp)
            if (j.has("password"))
                GlassTextField("Password", j.str("password"), { v -> if (v.isBlank()) j.o.remove("password") else j.o.put("password", v) })
            if (j.has("wildcard_sni")) {
                SelectField(
                    label = "Wildcard SNI",
                    value = j.str("wildcard_sni"),
                    options = listOf(Opt("off", "off"), Opt("authed", "authed"), Opt("all", "all")),
                    onChange = { v -> j.setStr("wildcard_sni", v) },
                )
            }
            val hs = j.ensureObj("handshake")
            GlassTextField("Handshake server", hs.str("server"), { v -> hs.setStr("server", v, blankRemoves = false) })
            NumberField("Handshake port", hs.long("server_port").takeIf { it > 0 } ?: 443L,
                onChange = { v -> hs.o.put("server_port", v ?: 443) })
            DialSection(hs, emptyList())

            // handshake_for_server_name map
            val mapObj = j.o.optJSONObject("handshake_for_server_name")
            if (mapObj != null) {
                var sni by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                SectionHeader("Per-SNI handshakes")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField("Add handshake server", sni, { sni = it }, modifier = Modifier.weight(1f), hint = "sni.example.com")
                    IconGhostButton(Icons.Filled.Add, {
                        val k = sni.trim()
                        if (k.isNotEmpty() && !mapObj.has(k)) { mapObj.put(k, org.json.JSONObject()); sni = "" }
                    }, contentDesc = "add")
                }
                mapObj.keys().asSequence().toList().forEach { sniKey ->
                    val h = mapObj.optJSONObject(sniKey)?.let { J(it) } ?: J(org.json.JSONObject())
                    GlassCard(corner = 14.dp, contentPadding = 12.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(sniKey, color = g.violet, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconGhostButton(Icons.Rounded.DeleteOutline, { mapObj.remove(sniKey) }, tint = g.err)
                            }
                            GlassTextField("Server", h.str("server"), { v -> h.setStr("server", v, blankRemoves = false) })
                            NumberField("Port", h.long("server_port").takeIf { it > 0 } ?: 443L,
                                onChange = { v -> h.o.put("server_port", v ?: 443) })
                            DialSection(h, emptyList())
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------- tuic

@Composable
private fun TuicCard(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("TUIC")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectField(
                label = "Congestion control",
                value = j.str("congestion_control"),
                options = listOf(Opt("cubic", "cubic"), Opt("new_reno", "new_reno"), Opt("bbr", "bbr")),
                onChange = { v -> j.setStr("congestion_control", v ?: "cubic") },
                clearable = false,
            )
            SwitchRow("Zero-RTT handshake", j.bool("zero_rtt_handshake"),
                { j.setBool("zero_rtt_handshake", it, onlyTrue = true) })
            DurationField("Authentication timeout", j.optStringOrNull("auth_timeout"), 's',
                onChange = { j.setOrRemove("auth_timeout", it) })
            DurationField("Heartbeat", j.optStringOrNull("heartbeat"), 's', fallbackSeconds = 10,
                onChange = { j.setOrRemove("heartbeat", it) })
        }
    }
}

// ------------------------------------------------------------------- anytls

@Composable
private fun AnyTlsCard(j: J) {
    var text by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf((j.arr("padding_scheme")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotEmpty) } } ?: defaultPadding()).joinToString("\n"))
    }
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("AnyTLS")
        Spacer(Modifier.height(8.dp))
        GlassTextField(
            "Padding scheme (one per line)", text,
            { t ->
                text = t
                val lines = t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) j.o.remove("padding_scheme") else j.o.put("padding_scheme", jarr(lines))
            },
            singleLine = false, minLines = 4, maxLines = 12,
        )
    }
}

private fun defaultPadding(): List<String> = listOf(
    "stop=8", "0=30-30", "1=100-400",
    "2=400-500,c,500-1000,c,500-1000,c,500-1000,c,500-1000",
    "3=9-9,500-1000", "4=500-1000", "5=500-1000", "6=500-1000", "7=500-1000",
)

// ---------------------------------------------------------------------- tun

@Composable
private fun TunCard(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Tun")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.sonix21.suinode.ui.screens.shared.CsvField("Addresses", j.arr("address")?.strListX() ?: emptyList(), { parts ->
                if (parts.isEmpty()) j.o.remove("address") else j.o.put("address", jarr(parts))
            }, hint = "172.18.0.1/30")
            GlassTextField("Interface name", j.str("interface_name"),
                { v -> j.setStr("interface_name", v) }, hint = "tun0")
            NumberField("MTU", j.long("mtu").takeIf { it > 0 }, onChange = { j.setLong("mtu", it) })
            DurationField("UDP timeout", j.optStringOrNull("udp_timeout"), 'm', fallbackSeconds = 5,
                onChange = { j.setOrRemove("udp_timeout", it) })
            SelectField(
                label = "Stack",
                value = j.str("stack"),
                options = listOf(Opt("system", "system"), Opt("gvisor", "gvisor"), Opt("mixed", "mixed")),
                onChange = { j.setStr("stack", it ?: "system") },
                clearable = false,
            )
            SwitchRow("Independent NAT", j.bool("endpoint_independent_nat"),
                { j.setBool("endpoint_independent_nat", it, onlyTrue = true) })
            SwitchRow("Auto route", j.bool("auto_route"), { on ->
                j.o.put("auto_route", on)
                if (!on) { j.o.remove("auto_redirect"); j.o.remove("strict_route"); j.o.remove("exclude_mptcp"); j.o.remove("auto_redirect_iproute2_fallback_rule_index") }
            })
            if (j.bool("auto_route")) {
                SwitchRow("Auto redirect", j.bool("auto_redirect"), { j.setBool("auto_redirect", it, onlyTrue = true) })
                SwitchRow("Strict route", j.bool("strict_route"), { j.setBool("strict_route", it, onlyTrue = true) })
                if (j.bool("auto_route") && j.bool("auto_redirect")) {
                    SwitchRow("Exclude MPTCP", j.bool("exclude_mptcp"), { j.setBool("exclude_mptcp", it, onlyTrue = true) })
                    NumberField("iproute2 fallback rule index",
                        j.long("auto_redirect_iproute2_fallback_rule_index").takeIf { it > 0 } ?: 32768L,
                        onChange = { v -> j.setLong("auto_redirect_iproute2_fallback_rule_index", v) })
                }
            }
        }
    }
}

@Composable
private fun TProxyCard(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("TProxy")
        Spacer(Modifier.height(8.dp))
        NetworkField(j)
    }
}

// --------------------------------------------------------------------- snell

// Inbounds support v5 and v6. v5 carries an obfs mode, v6 carries a mode; the
// version selects which extra options apply, so switching drops the fields
// belonging to the version being left behind (mirrors the panel frontend).
@Composable
private fun SnellCard(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Snell")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectField(
                label = "Version",
                value = j.int("version"),
                options = listOf(Opt("v5", 5), Opt("v6", 6)),
                onChange = { v ->
                    val nv = v ?: 6
                    j.o.put("version", nv)
                    if (nv == 6) { j.o.remove("obfs_mode"); j.o.remove("obfs_host") }
                    else j.o.remove("mode")
                },
                clearable = false,
            )
            GlassTextField(
                "PSK", j.str("psk"),
                { v -> j.setStr("psk", v, blankRemoves = false) },
                trailing = {
                    IconGhostButton(Icons.Filled.Refresh, onClick = { j.o.put("psk", Rand.seq(32)) })
                },
            )
            if (j.int("version") == 5) {
                SelectField(
                    label = "Obfs mode",
                    value = j.optStringOrNull("obfs_mode"),
                    options = listOf(Opt("none", "none"), Opt("http", "http"), Opt("tls", "tls")),
                    onChange = { v -> j.setOrRemove("obfs_mode", v) },
                )
            }
            if (j.int("version") == 6) {
                SelectField(
                    label = "Mode",
                    value = j.optStringOrNull("mode"),
                    options = listOf(Opt("default", "default"), Opt("unshaped", "unshaped"), Opt("unsafe-raw", "unsafe-raw")),
                    onChange = { v -> j.setOrRemove("mode", v) },
                )
            }
        }
    }
}

// --------------------------------------------------------------- cloudflared

@Composable
private fun CloudflaredCard(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Cloudflared")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextField("Token", j.str("token"),
                { v -> j.setStr("token", v, blankRemoves = false) }, obscure = true)
            SelectField(
                label = "Protocol",
                value = j.optStringOrNull("protocol"),
                options = listOf(Opt("auto", "auto"), Opt("quic", "quic"), Opt("http2", "http2"), Opt("h2mux", "h2mux")),
                onChange = { v -> j.setOrRemove("protocol", v) },
            )
            NumberField("HA connections", j.optLongOr("ha_connections").takeIf { it > 0 },
                onChange = { v -> j.setLong("ha_connections", v) })
            SelectField(
                label = "Edge IP version",
                value = j.optLongOr("edge_ip_version", -1L).takeIf { it >= 0 },
                options = listOf(Opt("Auto", 0L), Opt("IPv4", 4L), Opt("IPv6", 6L)),
                onChange = { v -> j.setOrRemove("edge_ip_version", v) },
            )
            SelectField(
                label = "Datagram version",
                value = j.optStringOrNull("datagram_version"),
                options = listOf(Opt("v2", "v2"), Opt("v3", "v3")),
                onChange = { v -> j.setOrRemove("datagram_version", v) },
            )
            GlassTextField("Region", j.str("region"), { v -> j.setStr("region", v) })
            GlassTextField("Grace period", j.str("grace_period"),
                { v -> j.setStr("grace_period", v) }, hint = "30s")
            SwitchRow("Post quantum", j.bool("post_quantum"),
                { on -> j.setBool("post_quantum", on, onlyTrue = true) })
        }
    }
}
