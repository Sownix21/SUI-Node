package com.sonix21.suinode.ui.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

private val cipherSuites = listOf(
    "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA",
    "TLS_RSA_WITH_AES_128_GCM_SHA256", "TLS_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256",
    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256", "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
)

@Composable
fun TlsExtraOptions(t: J) {
    SwitchRow("Custom cipher suites", t.has("cipher_suites"), { if (it) t["cipher_suites"] = jarr(emptyList<String>()) else t.remove("cipher_suites") })
    if (t.has("cipher_suites")) MultiSelectField("Cipher suites", t.strs("cipher_suites").toSet(),
        (cipherSuites + t.strs("cipher_suites")).distinct().map { Opt(it, it) },
        { t.setStrs("cipher_suites", it.toList(), emptyRemoves = false) }, searchable = true)
    SwitchRow("Custom handshake timeout", t.has("handshake_timeout"), { if (it) t["handshake_timeout"] = "15s" else t.remove("handshake_timeout") })
    if (t.has("handshake_timeout")) DurationField("Handshake timeout", t.optStringOrNull("handshake_timeout"), 's',
        onChange = { t["handshake_timeout"] = it ?: "15s" })
}

@Composable
fun TlsFragmentFields(t: J) {
    SwitchRow("TLS fragmentation", t.bool("fragment"), { Panel163.fragment(t.o, it) })
    if (t.bool("fragment")) {
        SwitchRow("Record fragmentation", t.bool("record_fragment"), { t.setBool("record_fragment", it, true) })
        GlassTextField("Fragment fallback delay", t.str("fragment_fallback_delay"), { t.setStr("fragment_fallback_delay", it.trim()) }, hint = "500ms")
    }
}

@Composable
private fun PemLines(t: J, key: String, label: String) {
    GlassTextField(label, t.strs(key).joinToString("\n"), { t.setStrs(key, if (it.isBlank()) emptyList() else it.lines(), false) },
        singleLine = false, minLines = 3, obscure = key.contains("key"))
}

/** Server CA paths are an array; outbound certificate/key paths are strings. */
@Composable
fun MutualTlsFields(server: J?, client: J) {
    val serverKeys = Panel163.serverMutualKeys
    val clientKeys = Panel163.clientMutualKeys
    val enabled = serverKeys.any { server?.has(it) == true } || clientKeys.any(client::has)
    GlassCard(contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Mutual TLS / client authentication", enabled, { on ->
                if (on) Panel163.mutualSource(server?.o, client.o, false)
                else Panel163.disableMutual(server?.o, client.o)
            })
            if (enabled) {
                val textMode = server?.has("client_certificate") == true || client.has("client_certificate") || client.has("client_key")
                SelectField("Certificate source", if (textMode) "text" else "path", listOf(Opt("Server paths", "path"), Opt("PEM text", "text")),
                    clearable = false, onChange = { mode ->
                        Panel163.mutualSource(server?.o, client.o, mode == "text")
                    })
                if (server != null) {
                    SelectField("Client authentication policy", server.optStringOrNull("client_authentication"), Panel163.clientAuth.map { Opt(it, it) },
                        onChange = { server.setStr("client_authentication", it) })
                    if (textMode) PemLines(server, "client_certificate", "Client CA certificates (server)")
                    else CsvField("Client CA paths (server)", server.strs("client_certificate_path"), { server.setStrs("client_certificate_path", it, false) })
                    CsvField("Client public-key SHA256 pins", server.strs("client_certificate_public_key_sha256"), { server.setStrs("client_certificate_public_key_sha256", it) })
                }
                if (textMode) {
                    PemLines(client, "client_certificate", "Client certificate PEM")
                    PemLines(client, "client_key", "Client private key PEM")
                } else {
                    GlassTextField("Client certificate path", client.str("client_certificate_path"), { client.setStr("client_certificate_path", it, false) })
                    GlassTextField("Client private key path", client.str("client_key_path"), { client.setStr("client_key_path", it, false) })
                }
            }
        }
    }
}
