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
import androidx.compose.material.icons.filled.Security
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
import com.sonix21.suinode.core.Rand
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.ui.glass.ConfirmDialog
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
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
import com.sonix21.suinode.ui.screens.RecordLoadingPage
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.shared.CsvField
import com.sonix21.suinode.ui.screens.useSession

@Composable
fun TlsScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()
    var deleteId by remember { mutableStateOf<Long?>(null) }

    PageScaffold(
        title = "TLS Templates",
        subtitle = "${data.tlsConfigs.size} templates",
        nav = nav,
        busy = runner.busy,
        actions = {
            IconGhostButton(Icons.Filled.Verified, { nav.push(Route.CertProviders) }, contentDesc = "certificate providers")
        },
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("+ New template", type = com.sonix21.suinode.ui.glass.HeaderActionType.New) { nav.push(Route.TlsEditor(0)) }
        },
    ) {
        ItemsScroll(
            items = data.tlsConfigs.sortedBy { it.optLongOr("id") },
            emptyIcon = Icons.Filled.Security,
            emptyTitle = "No TLS templates",
            emptySubtitle = "Templates are shared between inbounds via tls_id.",
        ) { t ->
            val id = t.optLongOr("id")
            val linked = data.inbounds.count { it.optLongOr("tls_id") == id }
            GlassCard(contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(t.optString("name"), color = g.text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(t.obj("server")?.str("server_name")?.takeIf { it.isNotBlank() } ?: "-",
                                color = g.textFaint, fontSize = 11.5.sp)
                            if (t.obj("server")?.has("reality") == true) Text("REALITY", color = g.pink, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                            if (t.obj("server")?.has("acme") == true) Text("ACME", color = g.teal, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                            if (t.obj("server")?.has("ech") == true) Text("ECH", color = g.violet, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                            Text("$linked inbounds", color = g.textFaint, fontSize = 10.5.sp)
                        }
                    }
                    IconGhostButton(Icons.Filled.Refresh, {
                        runner.go {
                            val copy = org.json.JSONObject(t.toString())
                            copy.put("id", 0L)
                            var name = t.optString("name") + "-copy"
                            while (data.tlsConfigs.any { it.optString("name") == name }) name += "-copy"
                            copy.put("name", name)
                            val r = session.save("tls", "new", copy)
                            ToastBus.show(if (r.isSuccess) "cloned" else r.exceptionOrNull()?.message ?: "failed")
                        }
                    }, contentDesc = "clone")
                    IconGhostButton(Icons.Rounded.Edit, { nav.push(Route.TlsEditor(id)) }, contentDesc = "edit")
                    IconGhostButton(Icons.Rounded.DeleteOutline, {
                        if (linked == 0) deleteId = id else ToastBus.show("in use by $linked inbound(s)")
                    }, tint = if (linked == 0) g.err else g.textFaint.copy(alpha = 0.4f), contentDesc = "delete")
                }
            }
        }
    }

    deleteId?.let { id ->
        ConfirmDialog(title = "Delete template", message = "Delete this TLS template?", onConfirm = {
            runner.go {
                val r = session.save("tls", "del", id)
                ToastBus.show(if (r.isSuccess) "deleted" else r.exceptionOrNull()?.message ?: "failed")
            }
        }, onDismiss = { deleteId = null })
    }
}

// ------------------------------------------------------------------ editor

@Composable
fun TlsEditorScreen(nav: NavController, id: Long) {
    val session = useSession()
    val runner = rememberRunner()
    val g = LocalGlass.current

    var obj by remember { mutableStateOf<org.json.JSONObject?>(null) }

    suspend fun loadTls() {
        obj = session.fetchRecord("tls", id)
    }

    var loadAttempt by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(id, loadAttempt) {
        if (id > 0) {
            runner.go { loadTls() }
        } else {
            obj = jo(
                "id" to 0L,
                "name" to "",
                "server" to jo("enabled" to true),
                "client" to org.json.JSONObject(),
            )
        }
    }

    val o = obj
    if (o == null) {
        RecordLoadingPage("TLS template", nav, runner) { loadAttempt++ }
        return
    }
    val j = J(o)
    val server = j.ensureObj("server")
    val client = j.ensureObj("client")
    val isReality = server.has("reality")

    fun setReality(on: Boolean) {
        if (on) {
            com.sonix21.suinode.core.Panel161.setSpoof(client.o, null)
            server.o.put("reality", jo("enabled" to true,
                "handshake" to jo("server_port" to 443L),
                "short_id" to jarr(Rand.shortIds())))
            server.o.remove("acme"); server.o.remove("certificate_path"); server.o.remove("key_path")
            server.o.remove("certificate"); server.o.remove("key")
            client.o.put("reality", jo("public_key" to ""))
            client.o.put("utls", jo("enabled" to true, "fingerprint" to "chrome"))
        } else {
            server.o.remove("reality")
            client.o.remove("reality")
        }
    }

    PageScaffold(
        title = if (id <= 0) "New TLS Template" else "Edit TLS Template",
        draftValue = { obj },
        subtitle = o.optString("name").ifBlank { null },
        nav = nav,
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction(if (id <= 0) "Create template" else "Save template", loading = runner.busy) {
                    when {
                        j.str("name").isBlank() -> ToastBus.show("name is required")
                        else -> runner.go {
                            val r = session.save("tls", if (id <= 0) "new" else "edit", j.o)
                            if (r.isSuccess) { ToastBus.show("saved ✓"); nav.pop() } else throw Exception(r.exceptionOrNull()?.message)
                        }
                    }
                }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arranged12()) {

            GlassCard(contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField("Name", j.str("name"), { v -> j.setStr("name", v, blankRemoves = false) })
                    SelectField(
                        label = "Mode",
                        value = if (isReality) "reality" else "tls",
                        options = listOf(Opt("TLS", "tls"), Opt("REALITY", "reality")),
                        onChange = { v -> setReality(v == "reality") },
                        clearable = false,
                    )
                    // SNI toggle-style: show when present or always for reality
                    SwitchRow("Server Name (SNI)", server.has("server_name"), { on ->
                        if (on) server.o.put("server_name", "") else server.o.remove("server_name")
                    })
                    if (server.has("server_name"))
                        GlassTextField("SNI value", server.str("server_name"), { v -> server.setStr("server_name", v, blankRemoves = false) })

                    CsvField("ALPN", server.arr("alpn")?.strListX() ?: emptyList(), { parts ->
                        if (parts.isEmpty()) server.o.remove("alpn") else server.o.put("alpn", jarr(parts))
                    }, hint = "h3,h2,http/1.1")

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectField(label = "Min version", value = server.str("min_version"),
                            options = listOf("1.0","1.1","1.2","1.3").map { Opt(it,it) },
                            onChange = { server.setStr("min_version", it) }, modifier = Modifier.weight(1f))
                        SelectField(label = "Max version", value = server.str("max_version"),
                            options = listOf("1.0","1.1","1.2","1.3").map { Opt(it,it) },
                            onChange = { server.setStr("max_version", it) }, modifier = Modifier.weight(1f))
                    }

                    SwitchRow("Client: insecure", client.bool("insecure"),
                        { client.setBool("insecure", it, onlyTrue = true) })
                    SwitchRow("Client: disable SNI", client.bool("disable_sni"),
                        { client.setBool("disable_sni", it, onlyTrue = true) })
                }
            }

            if (!isReality) CertSection(server)

            // Shared certificate providers live in the base config and are
            // referenced here by tag (sing-box tls.certificate_provider).
            if (!isReality) {
                val providerTags = session.data.value.config.optArr("certificate_providers")
                    ?.objList()?.mapNotNull { it.optStringOrNull("tag")?.takeIf(String::isNotEmpty) } ?: emptyList()
                if (providerTags.isNotEmpty()) {
                    GlassCard(contentPadding = 14.dp) {
                        SelectField(
                            label = "Shared certificate provider",
                            value = server.str("certificate_provider"),
                            options = providerTags.map { Opt(it, it) },
                            onChange = { v -> server.setOrRemove("certificate_provider", v) },
                        )
                    }
                }
            }

            if (isReality) RealitySection(server, client, session)

            AcmeSection(server, enabled = !isReality && server.has("acme"), onToggle = { on ->
                if (on) server.o.put("acme", jo("domain" to jarr(emptyList<String>()))) else server.o.remove("acme")
            })

            EchSection(server, client)

            if (!isReality) GlassCard(contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.sonix21.suinode.ui.screens.shared.TlsSpoofFields(client)
                }
            }

            GlassCard(contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SwitchRow("uTLS fingerprint", client.has("utls"), { on ->
                        if (on) client.o.put("utls", jo("enabled" to true, "fingerprint" to "chrome")) else client.o.remove("utls")
                    })
                    if (client.has("utls")) {
                        val u = client.ensureObj("utls")
                        SelectField(label = "Fingerprint", value = u.str("fingerprint"),
                            options = com.sonix21.suinode.ui.screens.shared.UTLS_FINGERPRINTS.map { Opt(it, it) },
                            clearable = false,
                            onChange = { v -> if (v != null) u.o.put("fingerprint", v) })
                    }
                    SwitchRow("Root store (kTLS)", server.has("store"), { on ->
                        if (on) server.o.put("store", "mozilla") else server.o.remove("store")
                    })
                    if (server.has("store")) {
                        SelectField(label = "Store", value = server.str("store"),
                            options = listOf(Opt("mozilla", "mozilla"), Opt("chrome", "chrome")),
                            clearable = false,
                            onChange = { v -> if (v != null) server.o.put("store", v) })
                    }
                    SwitchRow("kTLS transmit", server.has("kernel_tx"), { on -> if (on) server.o.put("kernel_tx", false) else server.o.remove("kernel_tx") })
                    SwitchRow("kTLS receive", server.has("kernel_rx"), { on -> if (on) server.o.put("kernel_rx", false) else server.o.remove("kernel_rx") })
                }
            }
            com.sonix21.suinode.ui.screens.AdvancedJsonCard(j.o) { obj = it }
        }
    }
}

private fun Arranged12(): Arrangement.Vertical = Arrangement.spacedBy(12.dp)

// ------------------------------------------------------------- certificate

@Composable
private fun CertSection(server: J) {
    val session = useSession()
    val runner = rememberRunner()
    val usePath = server.has("certificate_path") || server.has("key_path")

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Certificate") {
            GhostButton("Self-signed", tint = LocalGlass.current.teal) {
                runner.go {
                    val sni = server.str("server_name").ifBlank { "\'\'" }
                    val env = session.client.get("keypairs", mapOf("k" to "tls", "o" to sni))
                    if (!env.success) throw Exception(env.msg)
                    val lines = env.objStrList()
                    val keyLines = extractBlock(lines, "PRIVATE KEY")
                    val certLines = extractBlock(lines, "CERTIFICATE")
                    if (keyLines.isEmpty() || certLines.isEmpty()) throw Exception("unexpected keypair response")
                    server.o.remove("key_path"); server.o.remove("certificate_path")
                    server.o.put("key", jarr(keyLines)); server.o.put("certificate", jarr(certLines))
                    ToastBus.show("certificate generated")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectField(label = "Source", value = if (usePath) "path" else "text",
                options = listOf(Opt("Path", "path"), Opt("Text (PEM)", "text")),
                clearable = false,
                onChange = { v ->
                    if (v == "path") { server.o.remove("key"); server.o.remove("certificate"); server.o.put("key_path", ""); server.o.put("certificate_path", "") }
                    else { server.o.remove("key_path"); server.o.remove("certificate_path"); server.o.put("key", jarr(emptyList<String>())); server.o.put("certificate", jarr(emptyList<String>())) }
                })
            if (usePath) {
                GlassTextField("Certificate path", server.str("certificate_path"), { v -> server.setStr("certificate_path", v) })
                GlassTextField("Key path", server.str("key_path"), { v -> server.setStr("key_path", v) })
            } else {
                PemArea(server, "certificate", "Certificate PEM")
                PemArea(server, "key", "Private key PEM")
            }
        }
    }
}

@Composable
private fun PemArea(holder: J, key: String, label: String) {
    var text by remember(key) {
        mutableStateOf((holder.arr(key)?.let { a -> (0 until a.length()).mapNotNull { x -> a.optString(x).takeIf(String::isNotEmpty) } } ?: emptyList()).joinToString("\n"))
    }
    GlassTextField(label, text, { t ->
        text = t
        val lines = t.split("\n").map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) holder.o.remove(key) else holder.o.put(key, jarr(lines))
    }, singleLine = false, minLines = 4, maxLines = 16)
}

// ----------------------------------------------------------------- reality

@Composable
private fun RealitySection(server: J, client: J, session: com.sonix21.suinode.data.PanelSession) {
    val runner = rememberRunner()
    val rs = server.ensureObj("reality")
    val rc = client.ensureObj("reality")
    val hs = rs.ensureObj("handshake")

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("REALITY")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField("Handshake server", hs.str("server"),
                    { v -> hs.setStr("server", v, blankRemoves = false) }, Modifier.weight(1.4f),
                    hint = "www.example.com")
                NumberField("Port", hs.long("server_port").takeIf { it > 0 } ?: 443L,
                    onChange = { v -> hs.o.put("server_port", v ?: 443) }, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField("Private key", rs.str("private_key"), { v -> rs.setStr("private_key", v) }, Modifier.weight(1f), obscure = true)
                IconGhostButton(Icons.Filled.Refresh, {
                    runner.go {
                        val env = session.client.get("keypairs", mapOf("k" to "reality"))
                        if (!env.success) throw Exception(env.msg)
                        val lines = env.objStrList()
                        lines.firstOrNull { it.startsWith("PrivateKey") }?.substring(12)?.let { rs.o.put("private_key", it) }
                        lines.firstOrNull { it.startsWith("PublicKey") }?.substring(11)?.let { rc.o.put("public_key", it) }
                        ToastBus.show("REALITY keypair generated")
                    }
                }, contentDesc = "generate reality keys")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTextField("Public key (client)", rc.str("public_key"), { v -> rc.setStr("public_key", v) }, Modifier.weight(1f))
                IconGhostButton(Icons.Filled.Refresh, {
                    rs.o.put("short_id", jarr(Rand.shortIds()))
                    ToastBus.show("short ids regenerated")
                }, tint = LocalGlass.current.violet, contentDesc = "regenerate short ids")
            }
            CsvField("Short IDs", rs.arr("short_id")?.strListX() ?: emptyList(), { parts ->
                rs.o.put("short_id", if (parts.isEmpty()) jarr(listOf("")) else jarr(parts))
            }, hint = "comma separated")
            SwitchRow("Max time difference", rs.has("max_time_difference"), { on ->
                if (on) rs.o.put("max_time_difference", "1m") else rs.o.remove("max_time_difference")
            })
            if (rs.has("max_time_difference"))
                com.sonix21.suinode.ui.glass.DurationField("Minutes", rs.optStringOrNull("max_time_difference"), 'm', fallbackSeconds = 1,
                    onChange = { rs.setOrRemove("max_time_difference", it) })
        }
    }
}

// -------------------------------------------------------------------- acme

@Composable
fun AcmeSection(server: J, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    if (!enabled) {
        GlassCard(contentPadding = 14.dp) {
            SwitchRow("ACME (automatic certificate)", false, onToggle)
        }
        return
    }
    val acme = server.ensureObj("acme")
    val dnsProviders = mapOf(
        "cloudflare" to listOf("api_token", "zone_token"),
        "alidns" to listOf("access_key_id", "access_key_secret", "region_id", "security_token"),
        "acmedns" to listOf("username", "password", "subdomain", "server_url"),
    )

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("ACME") { GhostButton("Remove", tint = LocalGlass.current.err) { onToggle(false) } }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CsvField("Domains", acme.arr("domain")?.strListX() ?: emptyList(), { parts ->
                acme.o.put("domain", jarr(parts))
            }, hint = "a.com,b.org")
            ToggleTextA(acme, "data_directory", "Data directory", "")
            ToggleTextA(acme, "default_server_name", "Default server name", "")
            ToggleTextA(acme, "email", "Email", "")
            SwitchRow("Disable HTTP challenge", acme.bool("disable_http_challenge"),
                { acme.setBool("disable_http_challenge", it, onlyTrue = true) })
            SwitchRow("Disable TLS-ALPN challenge", acme.bool("disable_tls_alpn_challenge"),
                { acme.setBool("disable_tls_alpn_challenge", it, onlyTrue = true) })
            NumberField("Alternative HTTP port", acme.long("alternative_http_port").takeIf { it > 0 },
                hint = "80", onChange = { acme.setLong("alternative_http_port", it) })
            NumberField("Alternative TLS port", acme.long("alternative_tls_port").takeIf { it > 0 },
                hint = "443", onChange = { acme.setLong("alternative_tls_port", it) })

            val hasEab = acme.has("external_account")
            SwitchRow("External account binding", hasEab, { on ->
                if (on) acme.o.put("external_account", jo("key_id" to "", "mac_key" to "")) else acme.o.remove("external_account")
            })
            if (hasEab) {
                val eab = acme.ensureObj("external_account")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField("Key ID", eab.str("key_id"), { eab.setStr("key_id", it) }, Modifier.weight(1f))
                    GlassTextField("MAC key", eab.str("mac_key"), { eab.setStr("mac_key", it) }, Modifier.weight(1f))
                }
            }

            val hasDns = acme.has("dns01_challenge")
            SwitchRow("DNS-01 challenge", hasDns, { on ->
                if (on) acme.o.put("dns01_challenge", jo("provider" to "cloudflare")) else acme.o.remove("dns01_challenge")
            })
            if (hasDns) {
                val d = acme.ensureObj("dns01_challenge")
                SelectField(label = "DNS provider", value = d.str("provider"),
                    options = dnsProviders.keys.map { Opt(it, it) }, clearable = false,
                    onChange = { v -> if (v != null) {
                        d.o.keys().asSequence().toList().forEach { d.o.remove(it) }
                        d.o.put("provider", v)
                    } })
                dnsProviders[d.str("provider")]?.forEach { param ->
                    GlassTextField(param.replace('_', ' '), d.str(param), { v ->
                        if (v.isBlank()) d.o.remove(param) else d.o.put(param, v)
                    })
                }
            }
        }
    }
}

@Composable
private fun ToggleTextA(j: J, key: String, label: String, defOn: String) {
    SwitchRow(label, j.has(key), { on -> if (on) j.o.put(key, defOn) else j.o.remove(key) })
    if (j.has(key)) GlassTextField(label, j.str(key), { v -> j.setStr(key, v, blankRemoves = false) })
}

// --------------------------------------------------------------------- ech

@Composable
private fun EchSection(server: J, client: J) {
    val session = useSession()
    val runner = rememberRunner()
    val enabled = server.has("ech")

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("ECH") {
            SwitchRow("", enabled, { on ->
                if (on) {
                    server.o.put("ech", jo("enabled" to true))
                    client.o.put("ech", org.json.JSONObject())
                } else {
                    server.o.remove("ech"); client.o.remove("ech")
                }
            })
        }
        if (enabled) {
            val sech = server.ensureObj("ech")
            val cech = client.ensureObj("ech")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.sonix21.suinode.ui.glass.GhostButton("Generate keys") {
                        runner.go {
                            val sni = server.str("server_name").ifBlank { "\'\'" }
                            val env = session.client.get("keypairs", mapOf("k" to "ech", "o" to sni))
                            if (!env.success) throw Exception(env.msg)
                            val lines = env.objStrList()
                            val configs = extractBlock(lines, "ECH CONFIGS")
                            val keys = extractBlock(lines, "ECH KEYS")
                            if (keys.isNotEmpty()) sech.o.put("key", jarr(keys))
                            if (configs.isNotEmpty()) cech.o.put("config", jarr(configs))
                            ToastBus.show("ECH material generated")
                        }
                    }
                }
                PemArea(sech, "key", "ECH keys PEM (server)")
                PemArea(cech, "config", "ECH configs PEM (client)")
                GlassTextField("Query server name", cech.str("query_server_name"),
                    { v -> cech.setStr("query_server_name", v) }, hint = "ech.example.com")
            }
        }
    }
}

private fun extractBlock(lines: List<String>, block: String): List<String> {
    val out = mutableListOf<String>()
    var inside = false
    for (l in lines) {
        when {
            l.contains("-----BEGIN $block") -> { inside = true; out.add(l.trim()) }
            l.contains("-----END $block") -> { inside = false; out.add(l.trim()) }
            inside -> out.add(l.trim())
        }
    }
    return out
}
