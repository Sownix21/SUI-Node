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
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.data.Panels
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GhostButton
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.Opt
import com.sonix21.suinode.ui.glass.SectionHeader
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.screens.rememberRunner

val UTLS_FINGERPRINTS = listOf("chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized")
private val TLS_VERSIONS = listOf("1.0", "1.1", "1.2", "1.3")

/**
 * Client-side inline TLS object editor (outbounds / dns servers / addr entries).
 * Mirrors the panel's oTls shape. When [server]/[serverPort] are provided the
 * certificate pin can be fetched live from the panel (getCertPing).
 */
@Composable
fun OutTlsEditor(holder: J, title: String = "TLS", server: String? = null, serverPort: Long = 0) {
    val g = LocalGlass.current
    val session = Panels.session
    val runner = rememberRunner()

    GlassCard(contentPadding = 14.dp) {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Enabled", holder.bool("enabled"), { holder.o.put("enabled", it) })
            if (holder.bool("enabled")) {
            SwitchRow("Insecure (skip verify)", holder.bool("insecure"), { holder.setBool("insecure", it, onlyTrue = true) })
            SwitchRow("Disable SNI", holder.bool("disable_sni"), { holder.setBool("disable_sni", it, onlyTrue = true) })
            GlassTextField("Server name (SNI)", holder.str("server_name"), { v -> holder.setStr("server_name", v) })
            CsvField("ALPN", holder.arr("alpn")?.strListX() ?: emptyList(), { parts ->
                if (parts.isEmpty()) holder.remove("alpn") else holder.o.put("alpn", jarr(parts))
            }, hint = "h3,h2,http/1.1")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectField(
                    label = "Min version", value = holder.str("min_version"),
                    options = TLS_VERSIONS.map { Opt(it, it) },
                    onChange = { holder.setStr("min_version", it) }, modifier = Modifier.weight(1f))
                SelectField(
                    label = "Max version", value = holder.str("max_version"),
                    options = TLS_VERSIONS.map { Opt(it, it) },
                    onChange = { holder.setStr("max_version", it) }, modifier = Modifier.weight(1f))
            }

            // ---- uTLS fingerprint
            val hasUtls = holder.has("utls")
            SwitchRow("uTLS fingerprint", hasUtls, { on ->
                if (on) holder.o.put("utls", jo("enabled" to true, "fingerprint" to "chrome"))
                else holder.remove("utls")
            })
            if (hasUtls) {
                val u = holder.ensureObj("utls")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectField(
                        label = "Fingerprint",
                        value = u.str("fingerprint"),
                        options = UTLS_FINGERPRINTS.map { Opt(it, it) },
                        onChange = { v -> if (v != null) u.o.put("fingerprint", v) },
                        clearable = false,
                        modifier = Modifier.weight(1f),
                    )
                    IconGhostButton(Icons.Filled.Refresh, {
                        val idx = UTLS_FINGERPRINTS.indexOf(u.str("fingerprint"))
                        u.o.put("fingerprint", UTLS_FINGERPRINTS[(idx + 1) % UTLS_FINGERPRINTS.size])
                    }, contentDesc = "next fingerprint")
                }
            }

            // ---- REALITY
            val hasReality = holder.has("reality")
            SwitchRow("REALITY", hasReality, { on ->
                if (on) {
                    Panel161.setSpoof(holder.o, null)
                    holder.o.put("reality", jo("enabled" to true, "public_key" to "", "short_id" to ""))
                }
                else holder.remove("reality")
            })
            if (hasReality) {
                val r = holder.ensureObj("reality")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassTextField("Public key", r.str("public_key"), { v -> r.setStr("public_key", v) }, modifier = Modifier.weight(1f))
                        IconGhostButton(Icons.Filled.Refresh, {
                            runner.go {
                                val env = session?.client?.get("keypairs", mapOf("k" to "reality")) ?: return@go
                                if (!env.success) throw Exception(env.msg)
                                val pub = env.objStrList().firstOrNull { it.startsWith("PublicKey") }?.substring(11)
                                    ?: throw Exception("bad keypair response")
                                r.o.put("public_key", pub)
                                ToastBus.show("public key generated")
                            }
                        }, contentDesc = "generate reality keypair")
                    }
                    GlassTextField("Short ID", r.str("short_id"), { v -> r.setStr("short_id", v) })
                }
            }

            TlsSpoofFields(holder)

            // ---- ECH
            val hasEch = holder.has("ech")
            SwitchRow("ECH", hasEch, { on ->
                if (on) holder.o.put("ech", jo("enabled" to true))
                else holder.remove("ech")
            })
            if (hasEch) {
                val e = holder.ensureObj("ech")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CsvField("ECH configs", e.arr("config")?.strListX() ?: emptyList(), { parts ->
                        if (parts.isEmpty()) e.remove("config") else e.o.put("config", jarr(parts))
                    }, hint = "paste config lines")
                    GlassTextField("Query server name", e.str("query_server_name"), { v -> e.setStr("query_server_name", v) })
                }
            }

            // ---- certificate pin (sha256 of the server's public key)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CsvField("Certificate pin (public key sha256)",
                    holder.arr("certificate_public_key_sha256")?.strListX() ?: emptyList(), { parts ->
                        if (parts.isEmpty()) holder.remove("certificate_public_key_sha256")
                        else holder.o.put("certificate_public_key_sha256", jarr(parts))
                    }, hint = "base64 sha256", modifier = Modifier.weight(1f))
                if (!server.isNullOrBlank() && serverPort > 0) {
                    IconGhostButton(Icons.Filled.Refresh, {
                        runner.go {
                            val env = session?.client?.postForm("getCertPing",
                                mapOf("domain" to server, "port" to serverPort.toString())) ?: return@go
                            if (!env.success) throw Exception(env.msg)
                            val leaf = env.objObj()?.optStringOrNull("leafHash")
                                ?: throw Exception("no leaf hash returned")
                            holder.o.put("certificate_public_key_sha256", jarr(leaf.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                            ToastBus.show("pinned leaf hash ✓")
                        }
                    }, contentDesc = "fetch certificate pin")
                }
            }

            // ---- fragment
            SwitchRow("Fragment (TLS fragmentation)", holder.bool("fragment"), { holder.setBool("fragment", it, onlyTrue = true) })
            if (holder.has("fragment")) {
                DurationField("Fragment fallback delay", holder.optStringOrNull("fragment_fallback_delay"), 's',
                    onChange = { holder.setOrRemove("fragment_fallback_delay", it) })
            }
            }
        }
    }
}

private fun org.json.JSONArray.strListX(): List<String> =
    (0 until length()).mapNotNull { runCatching { optString(it) }.getOrNull() }
