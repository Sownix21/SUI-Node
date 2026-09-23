package com.sonix21.suinode.ui.screens.endpoints

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.Panels
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.shared.CsvField
import org.json.JSONArray
import org.json.JSONObject

private fun lines(o: JSONObject, key: String): List<String> = when (val v = o.opt(key)) {
    is JSONArray -> v.strList()
    is String -> v.lines()
    else -> emptyList()
}

@Composable
private fun MaterialField(t: J, key: String, label: String, textMode: Boolean) {
    if (textMode) GlassTextField(label, lines(t.o, key).joinToString("\n"), {
        t[key] = jarr(if (it.isBlank()) emptyList() else it.lines())
    }, singleLine = false, minLines = 3, obscure = key.contains("key"))
    else GlassTextField("$label path on server", t.str("${key}_path"), { t.setStr("${key}_path", it, false) })
    if (textMode && t.str("${key}_path").isNotBlank()) GlassTextField("$label retained server path", t.str("${key}_path"), { t.setStr("${key}_path", it) },
        supporting = "Existing path preserved; clear it if replacing this material with text")
}

@Composable
private fun TlsOption(t: J, title: String, keys: List<String>, initial: Pair<String, Any>, content: @Composable () -> Unit) {
    val enabled = keys.any(t::has)
    SwitchRow(title, enabled, { on -> if (on) t[initial.first] = initial.second else keys.forEach(t::remove) })
    if (enabled) content()
}

/** Endpoint-specific TLS names, not sing-box's shared TLS-template projection. */
@Composable
internal fun VpnTlsFields(endpoint: J) {
    JsonEditSignal.revision
    val connect = endpoint.str("type") == "openconnect"
    val server = endpoint.str("type") == "openvpn-server"
    val t = endpoint.ensureObj("tls")
    val materialKeys = if (connect) listOf("certificate_authority", "client_certificate", "client_key")
        else listOf("certificate", "key", "client_certificate", "client_key")
    var textMode by remember(endpoint.o) { mutableStateOf(materialKeys.any(t::has) || t.obj("control_wrap")?.has("key") == true) }
    val runner = rememberRunner()
    val api = Panels.session?.client
    fun switchMaterial(text: Boolean) {
        materialKeys.forEach { t.remove(if (text) "${it}_path" else it) }
        t.obj("control_wrap")?.remove(if (text) "key_path" else "key")
        textMode = text
    }
    GlassCard(contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(if (connect) "OpenConnect TLS" else "OpenVPN TLS")
            runner.error?.let { Text(it, color = LocalGlass.current.err) }
            Text("Endpoint TLS settings · s-ui 1.6.1+", color = LocalGlass.current.textFaint)
            if (endpoint.long("tls_id") > 0) Text("This endpoint has an old template reference. Configure its own TLS material before saving; 1.6.1 ignores that reference.", color = LocalGlass.current.warn)
            SwitchRow("Enter certificate/key text", textMode, ::switchMaterial,
                subtitle = "Off uses paths on the panel server; switching clears material in the other format")
            MaterialField(t, if (connect) "certificate_authority" else "certificate",
                if (server) "Server certificate" else "Certificate authority", textMode)
            if (server) {
                MaterialField(t, "key", "Server private key", textMode)
                GhostButton("Generate self-signed server certificate", enabled = !runner.busy) { runner.go {
                    val e = api?.get("keypairs", mapOf("k" to "tls", "o" to endpoint.str("tag"))) ?: return@go
                    check(e.success) { e.msg }
                    val cert = Panel161.pemBlock(e.objStrList(), "CERTIFICATE")
                    val key = Panel161.pemBlock(e.objStrList(), "PRIVATE KEY")
                    // Only replace the generated pair; preserve other paths already entered.
                    t.remove("certificate_path"); t.remove("key_path")
                    t["certificate"] = jarr(cert); t["key"] = jarr(key); textMode = true
                    JsonEditSignal.bump()
                } }
                SelectField("Verify client certificates", t.str("verify_client_certificate", "require"),
                    listOf("require", "optional", "none").map { Opt(it.replaceFirstChar(Char::uppercase), it) },
                    clearable = false, onChange = { value ->
                        t["verify_client_certificate"] = value ?: "require"
                        if (value == "none") { t.remove("client_certificate"); t.remove("client_certificate_path") }
                    })
                if (t.str("verify_client_certificate", "require") != "none") MaterialField(t, "client_certificate", "Client certificate authority", textMode)
            } else {
                val mutualKeys = listOf("client_certificate", "client_certificate_path", "client_key", "client_key_path") + if (connect) listOf("client_key_password") else emptyList()
                TlsOption(t, "Client certificate authentication", mutualKeys,
                    if (textMode) "client_certificate" to JSONArray() else "client_certificate_path" to "") {
                    MaterialField(t, "client_certificate", "Client certificate", textMode)
                    MaterialField(t, "client_key", "Client private key", textMode)
                    if (connect) GlassTextField("Client key password", t.str("client_key_password"), { t.setStr("client_key_password", it) }, obscure = true)
                }
            }
            if (!connect) TlsOption(t, "Control channel protection", listOf("control_wrap"), "control_wrap" to jo("type" to "tls_crypt")) {
                val wrap = t.ensureObj("control_wrap")
                SelectField("Control wrapping", wrap.str("type", "tls_crypt"), listOf("tls_auth", "tls_crypt", "tls_crypt_v2").map { Opt(it.replace('_', '-'), it) },
                    clearable = false, onChange = { wrap["type"] = it ?: "tls_crypt"; if (it != "tls_auth") wrap.remove("direction") })
                MaterialField(wrap, "key", "Control channel key", textMode)
                if (wrap.str("type") == "tls_auth") SelectField("Key direction", wrap.optStringOrNull("direction"),
                    listOf(Opt("Server", "server"), Opt("Client", "client")), onChange = { wrap.setOrRemove("direction", it) })
                if (wrap.str("type") != "tls_crypt_v2") GhostButton("Generate OpenVPN static key", enabled = !runner.busy) { runner.go {
                    val e = api?.get("keypairs", mapOf("k" to "openvpn")) ?: return@go
                    check(e.success) { e.msg }
                    val key = Panel161.pemBlock(e.objStrList(), "OpenVPN Static key V1")
                    wrap.remove("key_path"); wrap["key"] = jarr(key); textMode = true
                    JsonEditSignal.bump()
                } } else Text("tls-crypt-v2 requires matching v2 key material from your OpenVPN setup.", color = LocalGlass.current.textFaint)
            }
            TlsOption(t, "Peer fingerprint", listOf("peer_fingerprint"), "peer_fingerprint" to JSONArray()) {
                CsvField("Peer certificate fingerprints", lines(t.o, "peer_fingerprint"), { t.setStrs("peer_fingerprint", it, false) })
            }
            if (!server) TlsOption(t, "Expected server name", listOf("server_name"), "server_name" to "") {
                GlassTextField("Server name", t.str("server_name"), { t.setStr("server_name", it, false) })
            }
            if (connect) TlsOption(t, "Certificate verification options", listOf("insecure", "system_trust_disabled"), "insecure" to false) {
                SwitchRow("Skip certificate verification", t.bool("insecure"), { t["insecure"] = it })
                SwitchRow("Disable system trust", t.bool("system_trust_disabled"), { t["system_trust_disabled"] = it })
            } else {
                TlsOption(t, "Certificate revocation list", listOf("crl_path"), "crl_path" to "") {
                    GlassTextField("CRL path on server", t.str("crl_path"), { t.setStr("crl_path", it, false) })
                }
                TlsOption(t, "TLS version range", listOf("version_min", "version_max"), "version_min" to "1.2") {
                    val versions = listOf("1.0", "1.1", "1.2", "1.3").map { Opt(it, it) }
                    SelectField("Minimum TLS version", t.optStringOrNull("version_min"), versions, onChange = { t.setOrRemove("version_min", it) })
                    SelectField("Maximum TLS version", t.optStringOrNull("version_max"), versions, onChange = { t.setOrRemove("version_max", it) })
                }
                TlsOption(t, "Certificate profile", listOf("remote_certificate_tls", "certificate_profile"), "remote_certificate_tls" to if (server) "client" else "server") {
                    SelectField("Remote certificate role", t.optStringOrNull("remote_certificate_tls"), listOf("server", "client", "none").map { Opt(it, it) }, onChange = { t.setOrRemove("remote_certificate_tls", it) })
                    SelectField("Profile", t.optStringOrNull("certificate_profile"), listOf("legacy", "preferred", "insecure", "suiteb").map { Opt(it, it) }, onChange = { t.setOrRemove("certificate_profile", it) })
                }
            }
        }
    }
}
