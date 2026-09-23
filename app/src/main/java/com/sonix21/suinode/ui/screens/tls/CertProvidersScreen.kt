package com.sonix21.suinode.ui.screens.tls

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.ui.glass.ConfirmDialog
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.GhostButton
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.NumberField
import com.sonix21.suinode.ui.glass.Opt
import com.sonix21.suinode.ui.glass.PrimaryButton
import com.sonix21.suinode.ui.glass.SectionHeader
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.nav.Route
import com.sonix21.suinode.ui.screens.ItemsScroll
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.shared.CsvField
import com.sonix21.suinode.ui.screens.useSession
import org.json.JSONArray
import org.json.JSONObject

val CERT_PROVIDER_TYPES = listOf("acme", "tailscale", "cloudflare-origin-ca")

fun providerTypeName(type: String) = when (type) {
    "acme" -> "ACME"
    "tailscale" -> "Tailscale"
    "cloudflare-origin-ca" -> "Cloudflare Origin CA"
    else -> type
}

fun configProviders(config: JSONObject): MutableList<JSONObject> {
    val arr = config.optArr("certificate_providers") ?: return mutableListOf()
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.toMutableList()
}

fun setConfigProviders(config: JSONObject, providers: List<JSONObject>) {
    if (providers.isEmpty()) config.remove("certificate_providers")
    else config.put("certificate_providers", jarr(providers))
}

/** Persists the providers list by saving the whole base config. */
suspend fun saveProviders(session: com.sonix21.suinode.data.PanelSession, config: JSONObject, providers: List<JSONObject>) {
    setConfigProviders(config, providers)
    val r = session.save("config", "set", config)
    if (r.isFailure) throw Exception(r.exceptionOrNull()?.message)
}

// -------------------------------------------------------------------- list

@Composable
fun CertProvidersScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()
    var deleteIndex by remember { mutableStateOf<Int?>(null) }

    val providers = remember(data.config) { configProviders(data.config) }
    fun users(tag: String): List<String> =
        data.tlsConfigs.filter { it.obj("server")?.str("certificate_provider") == tag }
            .mapNotNull { it.optStringOrNull("name") }

    PageScaffold(
        title = "Certificate providers",
        subtitle = "${providers.size} providers · part of the base config",
        nav = nav,
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("+ New provider", type = com.sonix21.suinode.ui.glass.HeaderActionType.New) { nav.push(Route.CertProviderEditor(-1)) }
        },
    ) {
        ItemsScroll(
            items = providers,
            emptyIcon = Icons.Filled.Verified,
            emptyTitle = "No certificate providers",
            emptySubtitle = "Providers issue and renew certificates; TLS templates reference them by tag.",
        ) { p ->
            val index = providers.indexOf(p)
            val tag = p.optString("tag")
            val linked = users(tag)
            val domainCount = p.optArr("domain")?.length() ?: 0
            GlassCard(contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(tag, color = g.text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(providerTypeName(p.optString("type")), color = g.teal, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Text(if (domainCount > 0) "$domainCount domain(s)" else "no domains", color = g.textFaint, fontSize = 11.sp)
                            Text(if (linked.isEmpty()) "unused" else "${linked.size} TLS", color = g.textFaint, fontSize = 11.sp)
                        }
                    }
                    IconGhostButton(Icons.Filled.Refresh, {
                        runner.go {
                            val copy = JSONObject(p.toString())
                            var t = tag + "-copy"
                            while (providers.any { it.optString("tag") == t }) t += "-copy"
                            copy.put("tag", t)
                            saveProviders(session, data.config, providers.toMutableList().apply { add(copy) })
                            ToastBus.show("cloned")
                        }
                    }, contentDesc = "clone")
                    IconGhostButton(Icons.Rounded.Edit, { nav.push(Route.CertProviderEditor(index)) }, contentDesc = "edit")
                    IconGhostButton(Icons.Rounded.DeleteOutline, {
                        if (linked.isEmpty()) deleteIndex = index
                        else ToastBus.show("in use by ${linked.size} TLS template(s)")
                    }, tint = if (linked.isEmpty()) g.err else g.textFaint.copy(alpha = 0.4f), contentDesc = "delete")
                }
            }
        }
    }

    deleteIndex?.let { idx ->
        ConfirmDialog(title = "Delete provider", message = "Remove this certificate provider from the config?", onConfirm = {
            runner.go {
                saveProviders(session, data.config, providers.toMutableList().apply { removeAt(idx) })
                ToastBus.show("deleted")
            }
        }, onDismiss = { deleteIndex = null })
    }
}

// ------------------------------------------------------------------ editor

private fun createProvider(type: String, tag: String): JSONObject = when (type) {
    "acme" -> jo("type" to "acme", "tag" to tag, "domain" to jarr(emptyList<String>()))
    "tailscale" -> jo("type" to "tailscale", "tag" to tag)
    "cloudflare-origin-ca" -> jo("type" to "cloudflare-origin-ca", "tag" to tag, "domain" to jarr(emptyList<String>()))
    else -> jo("type" to type, "tag" to tag)
}

@Composable
fun CertProviderEditorScreen(nav: NavController, index: Int) {
    val g = LocalGlass.current
    val session = useSession()
    val runner = rememberRunner()
    val data by session.data.collectAsState()

    var obj by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(index) {
        obj = if (index >= 0) {
            configProviders(data.config).getOrNull(index)?.deepCopy()
        } else {
            var tag = "cert-" + Rand.seq(3)
            while (configProviders(data.config).any { it.optString("tag") == tag }) tag = "cert-" + Rand.seq(3)
            createProvider("acme", tag)
        }
    }

    val o = obj ?: return
    val j = J(o)
    val type = j.str("type")
    // ACME is hidden on Windows, where it does not work — mirrors the panel.
    val types = if (data.os == "windows" && type != "acme")
        CERT_PROVIDER_TYPES.filter { it != "acme" } else CERT_PROVIDER_TYPES

    fun changeType(newType: String) {
        val fresh = createProvider(newType, j.str("tag"))
        obj = fresh
    }

    PageScaffold(
        title = if (index < 0) "New provider" else "Edit provider",
        draftValue = { obj },
        subtitle = providerTypeName(type),
        nav = nav,
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction(if (index < 0) "Create provider" else "Save provider", loading = runner.busy) {
                    val tag = j.str("tag")
                    val others = configProviders(data.config).filterIndexed { i, _ -> i != index }
                    when {
                        tag.isBlank() -> ToastBus.show("tag is required")
                        others.any { it.optString("tag") == tag } -> ToastBus.show("duplicate tag")
                        else -> runner.go {
                            val list = configProviders(data.config).toMutableList()
                            if (index >= 0) list[index] = j.o else list.add(j.o)
                            saveProviders(session, data.config, list)
                            ToastBus.show("saved ✓")
                            nav.pop()
                        }
                    }
                }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectField(
                        label = "Type", value = type,
                        options = types.map { Opt(providerTypeName(it), it) },
                        onChange = { v -> v?.let(::changeType) },
                        clearable = false,
                    )
                    GlassTextField("Tag", j.str("tag"), { v -> j.setStr("tag", v, blankRemoves = false) })
                }
            }

            if (type == "acme") { AcmeProviderFields(j, session); AcmeProviderFieldsAdv(j, session) }
            if (type == "tailscale") TailscaleProviderFields(j)
            if (type == "cloudflare-origin-ca") OriginCaProviderFields(j)

            com.sonix21.suinode.ui.screens.AdvancedJsonCard(j.o) { obj = it }
        }
    }
}

private fun httpClientOpts(session: com.sonix21.suinode.data.PanelSession): List<Opt<String>> =
    session.data.value.config.optArr("http_clients")?.objList()
        ?.mapNotNull { it.optStringOrNull("tag")?.takeIf(String::isNotEmpty) }
        ?.map { Opt(it, it) } ?: emptyList()

@Composable
private fun AcmeProviderFields(j: J, session: com.sonix21.suinode.data.PanelSession) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("ACME")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CsvField("Domains (comma separated)", j.arr("domain")?.strListX() ?: emptyList(), { parts ->
                if (parts.isEmpty()) j.o.remove("domain") else j.o.put("domain", jarr(parts))
            }, hint = "example.com,www.example.com")

            SwitchRow("Data directory", j.has("data_directory"), { on ->
                if (on) j.o.put("data_directory", "") else j.o.remove("data_directory")
            })
            if (j.has("data_directory"))
                GlassTextField("Data directory value", j.str("data_directory"), { v -> j.setStr("data_directory", v) })

            SwitchRow("Default server name", j.has("default_server_name"), { on ->
                if (on) j.o.put("default_server_name", j.arr("domain")?.optString(0) ?: "") else j.o.remove("default_server_name")
            })
            if (j.has("default_server_name"))
                GlassTextField("Default domain", j.str("default_server_name"), { v -> j.setStr("default_server_name", v) })

            SwitchRow("Email", j.has("email"), { on ->
                if (on) j.o.put("email", "") else j.o.remove("email")
            })
            if (j.has("email"))
                GlassTextField("Email value", j.str("email"), { v -> j.setStr("email", v) })

            SwitchRow("Challenge options", j.has("disable_http_challenge") || j.has("disable_tls_alpn_challenge"), { on ->
                if (on) { j.o.put("disable_http_challenge", false); j.o.put("disable_tls_alpn_challenge", false) }
                else { j.o.remove("disable_http_challenge"); j.o.remove("disable_tls_alpn_challenge") }
            })
            if (j.has("disable_http_challenge") || j.has("disable_tls_alpn_challenge")) {
                SwitchRow("Disable HTTP challenge", j.bool("disable_http_challenge"), { on -> j.o.put("disable_http_challenge", on) })
                SwitchRow("Disable TLS-ALPN challenge", j.bool("disable_tls_alpn_challenge"), { on -> j.o.put("disable_tls_alpn_challenge", on) })
            }
        }
    }
}

@Composable
private fun AcmeProviderFieldsAdv(j: J, session: com.sonix21.suinode.data.PanelSession) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("ACME · advanced")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Alternative ports", j.has("alternative_http_port") || j.has("alternative_tls_port"), { on ->
                if (on) { j.o.put("alternative_http_port", 80); j.o.put("alternative_tls_port", 443) }
                else { j.o.remove("alternative_http_port"); j.o.remove("alternative_tls_port") }
            })
            if (j.has("alternative_http_port") || j.has("alternative_tls_port")) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Alt HTTP port", j.long("alternative_http_port").takeIf { it > 0 },
                        onChange = { v -> j.setLong("alternative_http_port", v) }, modifier = Modifier.weight(1f))
                    NumberField("Alt TLS port", j.long("alternative_tls_port").takeIf { it > 0 },
                        onChange = { v -> j.setLong("alternative_tls_port", v) }, modifier = Modifier.weight(1f))
                }
            }

            SwitchRow("CA provider", j.has("provider"), { on ->
                if (on) j.o.put("provider", "letsencrypt") else j.o.remove("provider")
            })
            if (j.has("provider")) {
                SelectField(label = "Provider", value = j.str("provider").takeIf { it in setOf("letsencrypt", "zerossl") } ?: "",
                    options = listOf(Opt("Let's Encrypt", "letsencrypt"), Opt("ZeroSSL", "zerossl"), Opt("Custom", "")),
                    onChange = { v -> j.o.put("provider", v ?: "letsencrypt") })
                if (j.str("provider") !in setOf("letsencrypt", "zerossl"))
                    GlassTextField("Custom CA directory URL", j.str("provider"), { v -> j.setStr("provider", v, blankRemoves = false) })
            }

            SwitchRow("External account", j.has("external_account"), { on ->
                if (on) j.o.put("external_account", jo("key_id" to "", "mac_key" to "")) else j.o.remove("external_account")
            })
            if (j.has("external_account")) {
                val ea = j.ensureObj("external_account")
                GlassTextField("Key ID", ea.str("key_id"), { v -> ea.setStr("key_id", v) })
                GlassTextField("MAC key", ea.str("mac_key"), { v -> ea.setStr("mac_key", v) })
            }

            SwitchRow("DNS-01 challenge", j.has("dns01_challenge"), { on ->
                if (on) j.o.put("dns01_challenge", jo("provider" to "cloudflare")) else j.o.remove("dns01_challenge")
            })
            if (j.has("dns01_challenge")) AcmeDns01Fields(j)

            val hcOpts = httpClientOpts(session)
            if (hcOpts.isNotEmpty()) {
                SelectField(label = "HTTP client", value = j.optStringOrNull("http_client"),
                    options = hcOpts, onChange = { v -> j.setOrRemove("http_client", v) })
            }
        }
    }
}

private val DNS01_PROVIDERS = mapOf(
    "cloudflare" to listOf("api_token", "zone_token"),
    "alidns" to listOf("access_key_id", "access_key_secret", "region_id", "security_token"),
    "acmedns" to listOf("username", "password", "subdomain", "server_url"),
)

@Composable
private fun AcmeDns01Fields(j: J) {
    val d = j.ensureObj("dns01_challenge")
    val dp = d.str("provider").ifBlank { "cloudflare" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SelectField(label = "DNS provider", value = dp,
            options = DNS01_PROVIDERS.keys.map { Opt(it, it) },
            onChange = { v ->
                val nv = v ?: "cloudflare"
                val fresh = jo("provider" to nv)
                (DNS01_PROVIDERS[nv] ?: emptyList()).forEach { fresh.put(it, "") }
                j.o.put("dns01_challenge", fresh)
            }, clearable = false)
        (DNS01_PROVIDERS[dp] ?: emptyList()).forEach { p ->
            GlassTextField(p, d.str(p), { v -> d.setStr(p, v) })
        }
    }
}

@Composable
private fun TailscaleProviderFields(j: J) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Tailscale")
        Spacer(Modifier.height(8.dp))
        GlassTextField("Endpoint", j.optStringOrNull("endpoint") ?: "", { v -> j.setStr("endpoint", v) },
            supporting = "Reads the certificate Tailscale issued for the node.")
    }
}

@Composable
private fun OriginCaProviderFields(j: J) {
    val session = useSession()
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Cloudflare Origin CA")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CsvField("Domains (comma separated)", j.arr("domain")?.strListX() ?: emptyList(), { parts ->
                if (parts.isEmpty()) j.o.remove("domain") else j.o.put("domain", jarr(parts))
            }, hint = "example.com,www.example.com")
            GlassTextField("API token", j.optStringOrNull("api_token") ?: "", { v -> j.setStr("api_token", v) }, obscure = true)
            GlassTextField("Origin CA key", j.optStringOrNull("origin_ca_key") ?: "", { v -> j.setStr("origin_ca_key", v) }, obscure = true)
            SelectField(label = "Request type", value = j.optStringOrNull("request_type"),
                options = listOf(Opt("RSA", "origin-rsa"), Opt("ECC", "origin-ecc")),
                onChange = { v -> j.setOrRemove("request_type", v) })
            SelectField(label = "Requested validity (days)", value = j.optLongOr("requested_validity").takeIf { it > 0 },
                options = listOf(7L, 30L, 90L, 365L, 730L, 1095L, 5475L).map { Opt(it.toString(), it) },
                onChange = { v -> j.setLong("requested_validity", v) })
            GlassTextField("Data directory", j.optStringOrNull("data_directory") ?: "", { v -> j.setStr("data_directory", v) })
            val hcOpts = httpClientOpts(session)
            if (hcOpts.isNotEmpty()) {
                SelectField(label = "HTTP client", value = j.optStringOrNull("http_client"),
                    options = hcOpts, onChange = { v -> j.setOrRemove("http_client", v) })
            }
        }
    }
}
