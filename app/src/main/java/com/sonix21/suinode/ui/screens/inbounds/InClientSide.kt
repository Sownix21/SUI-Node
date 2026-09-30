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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.data.PanelSession
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
import com.sonix21.suinode.ui.screens.shared.HeadersCard
import com.sonix21.suinode.ui.screens.shared.MuxDirection
import com.sonix21.suinode.ui.screens.shared.MuxSection
import com.sonix21.suinode.ui.screens.shared.NetworkField
import com.sonix21.suinode.ui.screens.shared.UotField

/** "Client side" tab: out_json overrides + client multiplex + dial + multi-domain. */
@Composable
fun ClientSideTab(j: J, session: PanelSession, type: String, typeVersion: Int) {
    val g = LocalGlass.current
    val out = j.ensureObj("out_json")
    val dnsTags = remember2(session)

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Client basics")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (type) {
                "socks" -> SelectField(
                    label = "Version",
                    value = out.str("version"),
                    options = listOf(Opt("4", "4"), Opt("4a", "4a"), Opt("5", "5")),
                    onChange = { v -> out.setStr("version", v) },
                )
            }
            if (type in NEED_NETWORK) NetworkField(out)
            if (type in NEED_UOT) UotField(out)
            if (type == "http") {
                GlassTextField("Path", out.str("path"), { v -> out.setStr("path", v) })
                HeadersCard(out)
            }
            if (type == "vmess" || type == "vless") {
                SelectField(
                    label = "UDP packet encoding",
                    value = out.str("packet_encoding").ifBlank { "none" },
                    options = listOf(Opt("none", "none"), Opt("packetaddr", "packetaddr"), Opt("xudp", "xudp")),
                    onChange = { v -> out.setStr("packet_encoding", v?.takeIf { it != "none" }) },
                )
            }
            if (type == "vmess") {
                SelectField(
                    label = "Security",
                    value = out.str("security").ifBlank { "auto" },
                    options = listOf("auto", "none", "zero", "aes-128-gcm", "aes-128-ctr", "chacha20-poly1305").map { Opt(it, it) },
                    onChange = { v -> out.setStr("security", v ?: "auto") },
                    clearable = false,
                )
                SwitchRow("Global padding", out.bool("global_padding"), { out.setBool("global_padding", it, onlyTrue = true) })
                SwitchRow("Authenticated length", out.bool("authenticated_length"), { out.setBool("authenticated_length", it, onlyTrue = true) })
            }
            if (type == "hysteria") {
                GlassTextField("Stream receive window", out.str("stream_receive_window"),
                    { out.setStr("stream_receive_window", it.trim()) }, hint = "8mb")
            }
            if (type == "tuic") {
                SelectField(
                    label = "UDP relay mode",
                    value = out.str("udp_relay_mode"),
                    options = listOf(Opt("native", "native"), Opt("quic", "quic")),
                    onChange = { v -> out.setStr("udp_relay_mode", v) },
                )
                SwitchRow("UDP over stream", out.bool("udp_over_stream"), { out.setBool("udp_over_stream", it, onlyTrue = true) })
            }
            if (type == "hysteria" || type == "hysteria2") {
                com.sonix21.suinode.ui.screens.shared.CsvField("Port hopping ranges", out.arr("server_ports")?.strListX() ?: emptyList(), { parts ->
                    if (parts.isEmpty()) out.remove("server_ports") else out.o.put("server_ports", jarr(parts))
                }, hint = "2080:3000")
                DurationField("Hop interval", out.optStringOrNull("hop_interval"), 's', onChange = { out.setOrRemove("hop_interval", it) })
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // outbound mux (presence of inbound-level multiplex key mirrors panel)
    if (j.has("multiplex")) MuxSection(out, MuxDirection.OUT)

    DialSection(out, emptyList(), clientMode = true, dnsTags = dnsTags)

    Spacer(Modifier.height(12.dp))
    MultiDomainEditor(j, session)
}

private val NEED_NETWORK = setOf("socks", "shadowsocks", "vmess", "trojan", "hysteria", "vless", "tuic", "hysteria2")
private val NEED_UOT = setOf("socks", "shadowsocks")

@Composable
private fun remember2(session: PanelSession): List<String> {
    val data by session.data.collectAsState()
    return androidx.compose.runtime.remember(data) { session.dnsServerTags(data) }
}

private fun org.json.JSONArray.strListX(): List<String> =
    (0 until length()).mapNotNull { runCatching { optString(it) }.getOrNull() }

/** Addrs list — extra connection domains for clients. */
@Composable
private fun MultiDomainEditor(j: J, session: PanelSession) {
    val g = LocalGlass.current
    val data by session.data.collectAsState()

    fun addrList(): MutableList<org.json.JSONObject> {
        val arr = j.o.optJSONArray("addrs") ?: jarr(emptyList<Nothing>())
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.toMutableList()
    }

    fun setAddrs(list: List<org.json.JSONObject>) =
        j.o.put("addrs", jarr(list))

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Multi domain") {
            IconGhostButton(Icons.Filled.Add, {
                val host = try { java.net.InetAddress.getLocalHost().hostName } catch (_: Exception) { "" }
                val list = addrList()
                list.add(jo("server" to (host.ifBlank { "example.com" }), "server_port" to j.long("listen_port")))
                setAddrs(list)
            }, contentDesc = "add address")
        }
        Spacer(Modifier.height(8.dp))
        val addrs = addrList()
        if (addrs.isEmpty()) Text("no extra domains", color = g.textFaint, fontSize = 12.sp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            addrs.forEachIndexed { i, a ->
                val aj = J(a)
                GlassCard(corner = 16.dp, contentPadding = 12.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${i + 1}", color = g.violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            IconGhostButton(Icons.Rounded.DeleteOutline, {
                                setAddrs(addrList().filterIndexed { idx, _ -> idx != i })
                            }, tint = g.err, contentDesc = "remove")
                        }
                        GlassTextField("Server address", aj.str("server"), { v -> aj.setStr("server", v, blankRemoves = false) })
                        NumberField("Server port", aj.long("server_port"), onChange = { v -> aj.o.put("server_port", v ?: 0) })
                        SwitchRow("Remark", aj.has("remark"), { on ->
                            if (on) aj.o.put("remark", "") else aj.o.remove("remark")
                        })
                        if (aj.has("remark")) {
                            GlassTextField("Remark value", aj.str("remark"), { v -> aj.setStr("remark", v, blankRemoves = false) })
                        }
                        SwitchRow("TLS", aj.has("tls"), { on ->
                            if (on) aj.o.put("tls", jo("enabled" to true)) else aj.o.remove("tls")
                        })
                        if (typeSupportsTls(j.str("type")) && aj.has("tls")) {
                            OutTlsMini(J(aj.o.optJSONObject("tls")!!))
                        }
                    }
                }
            }
        }
    }
}

private fun typeSupportsTls(type: String) =
    type in setOf("http", "vmess", "trojan", "naive", "hysteria", "tuic", "hysteria2", "vless", "anytls")

/** Minimal client TLS editor for addr entries (enabled/insecure/server_name/alpn). */
@Composable
private fun OutTlsMini(t: J) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow("Enabled", t.bool("enabled"), { t.o.put("enabled", it) })
        SwitchRow("Insecure (skip verify)", t.bool("insecure"), { t.setBool("insecure", it, onlyTrue = true) })
        GlassTextField("SNI", t.str("server_name"), { v -> t.setStr("server_name", v) })
        com.sonix21.suinode.ui.screens.shared.CsvField("ALPN", t.arr("alpn")?.strListX() ?: emptyList(), { parts ->
            if (parts.isEmpty()) t.remove("alpn") else t.o.put("alpn", jarr(parts))
        }, hint = "h3,h2,http/1.1")
    }
}
