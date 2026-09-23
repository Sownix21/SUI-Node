package com.sonix21.suinode.ui.screens.services

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.core.optStringOrNull
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
import com.sonix21.suinode.ui.screens.ItemsScroll
import com.sonix21.suinode.ui.screens.RecordLoadingPage
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.inbounds.TlsTemplatePicker
import com.sonix21.suinode.ui.screens.inbounds.TypeChip
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.shared.DialSection
import com.sonix21.suinode.ui.screens.useSession

@Composable
fun ServicesScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()
    var deleteTag by remember { mutableStateOf<String?>(null) }

    PageScaffold(
        title = "Services",
        subtitle = "${data.services.size} total",
        nav = nav,
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("New service", type = com.sonix21.suinode.ui.glass.HeaderActionType.New) { nav.push(com.sonix21.suinode.ui.nav.Route.ServiceEditor(0)) }
        },
    ) {
        ItemsScroll(
            items = data.services.sortedBy { it.optLongOr("id") },
            emptyIcon = Icons.Filled.Dns,
            emptyTitle = "No services",
            emptySubtitle = "Add a relay, DNS listener, core API or multiplexer supported by your panel.",
        ) { s ->
            val tag = s.optString("tag")
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(tag, modifier = Modifier.weight(1f), color = g.text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TypeChip(s.optString("type"))
                            if (s.optLongOr("tls_id") > 0) Text("TLS", color = g.orange, fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                        }
                        Text(if (s.optString("type") == "oom-killer") "Memory protection · no listener" else "${s.optStringOrNull("listen") ?: "-"}:${s.optLongOr("listen_port")}", color = g.textFaint, fontSize = 11.5.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconGhostButton(Icons.Rounded.Edit, { nav.push(com.sonix21.suinode.ui.nav.Route.ServiceEditor(s.optLongOr("id"))) }, contentDesc = "Edit service")
                        IconGhostButton(Icons.Rounded.DeleteOutline, { deleteTag = tag }, tint = g.err, contentDesc = "Delete service")
                    }
                }
            }
        }
    }

    deleteTag?.let { tag ->
        ConfirmDialog(title = "Delete service", message = "Delete \"$tag\"?", onConfirm = {
            runner.go {
                val r = session.save("services", "del", tag)
                ToastBus.show(if (r.isSuccess) "deleted" else r.exceptionOrNull()?.message ?: "failed")
            }
        }, onDismiss = { deleteTag = null })
    }
}

// ------------------------------------------------------------------ editor

fun createDefaultService(type: String) = ServiceConfig.create(type)

@Composable
fun ServiceEditorScreen(nav: NavController, id: Long) {
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()

    var obj by remember { mutableStateOf<org.json.JSONObject?>(null) }

    var loadAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(id, loadAttempt) {
        if (id > 0) {
            runner.go { obj = session.fetchRecord("services", id) }
        } else {
            val port = com.sonix21.suinode.core.Rand.int(10000, 60000).toLong()
            val o = createDefaultService("derp")
            o.put("id", 0L); o.put("tag", "derp-" + com.sonix21.suinode.core.Rand.seq(3))
            o.put("listen", "::"); o.put("listen_port", port)
            obj = o
        }
    }

    val o = obj
    if (o == null) {
        RecordLoadingPage("Service", nav, runner) { loadAttempt++ }
        return
    }
    val j = J(o)
    val type = j.str("type")

    fun changeType(newType: String) {
        val fresh = createDefaultService(newType)
        fresh.put("id", j.long("id"))
        fresh.put("tag", if (j.long("id") > 0) j.str("tag") else "$newType-" + com.sonix21.suinode.core.Rand.seq(3))
        if (newType != "oom-killer" && j.has("listen_port")) {
            fresh.put("listen", j.str("listen").ifBlank { "::" })
            fresh.put("listen_port", j.optLongOr("listen_port"))
        }
        obj = fresh
    }

    PageScaffold(
        title = if (id <= 0) "New service" else "Edit service",
        draftValue = { obj },
        subtitle = "$type · ${o.optString("tag")}",
        nav = nav,
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction(if (id <= 0) "Create service" else "Save changes", loading = runner.busy) {
                    when {
                        j.str("tag").isBlank() -> ToastBus.show("tag is required")
                        data.services.any { it.optString("tag") == j.str("tag") && it.optLongOr("id") != id } -> ToastBus.show("duplicate tag")
                        else -> runner.go {
                            val r = session.save("services", if (id <= 0) "new" else "edit", ServiceConfig.payload(j.o))
                            if (r.isSuccess) { ToastBus.show("saved ✓"); nav.pop() } else throw Exception(r.exceptionOrNull()?.message)
                        }
                    }
                }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            GlassCard(contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectField(label = "Type", value = type,
                        options = ServiceConfig.types.map { (value, label) -> Opt(label, value) },
                        onChange = { v -> v?.let(::changeType) }, clearable = false)
                    GlassTextField("Tag", j.str("tag"), { v -> j.setStr("tag", v, blankRemoves = false) })

                }
            }

            if (type != "oom-killer") com.sonix21.suinode.ui.screens.inbounds.ListenSection(j, type, session)
            when (type) {
                "derp" -> DerpFields(j, session)
                "ssm-api" -> SsmApiFields(j, session)
                "ocm", "ccm" -> MultiplexerFields(j, session)
                "api" -> CoreApiFields(j, session)
                "oom-killer" -> OomKillerFields(j)
            }

            if (type in ServiceConfig.tlsTypes) GlassCard(contentPadding = 16.dp) {
                TlsTemplatePicker(j, session, required = false)
            }
            com.sonix21.suinode.ui.screens.AdvancedJsonCard(j.o) { obj = it }
        }
    }
}

// -------------------------------------------------------------------- derp

@Composable
private fun DerpFields(j: J, session: com.sonix21.suinode.data.PanelSession) {
    val data by session.data.collectAsState()
    val tsTags = remember(data) { session.tailscaleEndpointTags(data) }

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("DERP")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextField("Config path", j.str("config_path"), { j.setStr("config_path", it) })

            SwitchRow("Verify client endpoints", j.has("verify_client_endpoint"), { on ->
                if (on) j.o.put("verify_client_endpoint", com.sonix21.suinode.core.jarr(emptyList<String>())) else j.o.remove("verify_client_endpoint")
            })
            if (j.has("verify_client_endpoint") && tsTags.isNotEmpty()) {
                com.sonix21.suinode.ui.glass.MultiSelectField("Tailscale endpoints",
                    j.arr("verify_client_endpoint")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } } }?.toSet() ?: emptySet(),
                    tsTags.map { Opt(it, it) }, { sel ->
                        j.o.put("verify_client_endpoint", com.sonix21.suinode.core.jarr(sel.toList()))
                    })
            }

            DerpVerificationUrls(j, session)
            if (j.has("verify_client_endpoint") && tsTags.isEmpty()) Text("No Tailscale endpoints are configured on this panel.", color = LocalGlass.current.textFaint)
            SwitchRow("Home page", j.has("home"), { on -> if (on) j.o.put("home", "") else j.o.remove("home") })
            if (j.has("home"))
                GlassTextField("Home URL", j.str("home"), { v -> j.setStr("home", v, blankRemoves = false) }, hint = "blank | http[s]://example.com/path")

            SwitchRow("Mesh with other DERP", j.has("mesh_with"), { on ->
                if (on) { j.o.put("mesh_with", jarr(listOf(jo("tls" to org.json.JSONObject())))); j.o.put("mesh_psk", "") }
                else { j.o.remove("mesh_with"); j.o.remove("mesh_psk"); j.o.remove("mesh_psk_file") }
            })
            if (j.has("mesh_with")) {
                SectionHeader("Mesh nodes") {
                    IconGhostButton(Icons.Filled.Add, { j.ensureArr("mesh_with").put(jo("tls" to org.json.JSONObject())) }, contentDesc = "Add mesh node")
                }
                val arr = j.arr("mesh_with")!!
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i)?.let(::J) ?: continue
                    GlassCard(corner = 14.dp, contentPadding = 12.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Mesh node ${i + 1}", color = LocalGlass.current.violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                IconGhostButton(Icons.Rounded.DeleteOutline, {
                                    val nl = org.json.JSONArray()
                                    for (x in 0 until arr.length()) if (x != i) nl.put(arr.getJSONObject(x))
                                    j.o.put("mesh_with", nl)
                                }, tint = LocalGlass.current.err)
                            }
                            GlassTextField("Server", m.str("server"), { v -> m.setStr("server", v, blankRemoves = false) })
                            NumberField("Port", m.optLongOr("server_port"), onChange = { m.o.put("server_port", it ?: 0) })
                            GlassTextField("Host", m.str("host"), { v -> m.setStr("host", v) })
                            DialSection(m, session.outboundTags(data), dnsTags = session.dnsServerTags(data))
                            com.sonix21.suinode.ui.screens.shared.OutTlsEditor(m)
                        }
                    }
                }
                // psk vs file toggle
                val hasFile = j.has("mesh_psk_file")
                SelectField(
                    label = "Mesh PSK mode",
                    value = if (hasFile) "file" else "psk",
                    options = listOf(Opt("Mesh PSK", "psk"), Opt("PSK file", "file")),
                    clearable = false,
                    onChange = { v ->
                        if (v == "file") { j.o.remove("mesh_psk"); if (!j.has("mesh_psk_file")) j.o.put("mesh_psk_file", "") }
                        else { j.o.remove("mesh_psk_file"); if (!j.has("mesh_psk")) j.o.put("mesh_psk", "") }
                    },
                )
                if (!hasFile) GlassTextField("Mesh PSK", j.str("mesh_psk"), { j.setStr("mesh_psk", it, blankRemoves = false) }, obscure = true)
                if (hasFile) GlassTextField("Mesh PSK file", j.str("mesh_psk_file"), { j.setStr("mesh_psk_file", it) })
            }

            SwitchRow("STUN server", j.has("stun"), { on ->
                if (on) j.o.put("stun", jo("enabled" to true)) else j.o.remove("stun")
            })
            j.obj("stun")?.let { stun ->
                SwitchRow("Enabled", stun.bool("enabled"), { stun.o.put("enabled", it) })
                if (stun.bool("enabled")) com.sonix21.suinode.ui.screens.inbounds.ListenSection(stun, "stun", session)
            }
        }
    }
}

// ----------------------------------------------------------------- ssm-api

@Composable
private fun SsmApiFields(j: J, session: com.sonix21.suinode.data.PanelSession) {
    val data by session.data.collectAsState()
    val ssTags = remember(data) { session.unmanagedSsInboundTags(data) }

    fun rows() = ServiceConfig.pathRows(j.o.optJSONObject("servers"))
    fun update(rows: List<Pair<String, String>>) { j.o.setOrRemove("servers", if (rows.isEmpty()) null else ServiceConfig.paths(rows)) }

    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Shadowsocks API paths") {
            IconGhostButton(androidx.compose.material.icons.Icons.Filled.Add, {
                val list = rows().toMutableList()
                list.add("/ss${list.size}" to (ssTags.firstOrNull() ?: ""))
                update(list)
            }, contentDesc = "add path")
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows().forEachIndexed { i, (path, tag) ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField("Path", path, { nv ->
                        val list = rows().toMutableList(); list[i] = nv to tag
                        update(list)
                    }, modifier = Modifier.fillMaxWidth())
                    SelectField(label = "SS inbound", value = tag, options = ssTags.map { Opt(it, it) },
                        clearable = false,
                        onChange = { nv ->
                            val list = rows().toMutableList(); list[i] = path to (nv ?: "")
                            update(list)
                        },
                        modifier = Modifier.fillMaxWidth())
                    IconGhostButton(Icons.Rounded.DeleteOutline, {
                        val list = rows().filterIndexed { idx, _ -> idx != i }
                        update(list)
                    }, tint = LocalGlass.current.err)
                }
            }
            if (rows().isEmpty()) Text("no paths — expose unmanaged shadowsocks inbounds over HTTP API",
                color = LocalGlass.current.textFaint, fontSize = 11.5.sp)
        }
    }
}

// -------------------------------------------------------------- ocm / ccm

@Composable
private fun MultiplexerFields(j: J, session: com.sonix21.suinode.data.PanelSession) {
    val data by session.data.collectAsState()
    val detourTags = remember(data) { session.outboundTags(data) }

    GlassCard(contentPadding = 14.dp) {
        SectionHeader(if (j.str("type") == "ccm") "Claude Code Multiplexer" else "OpenAI Codex Multiplexer")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextField("Credential path", j.str("credential_path"), { j.setStr("credential_path", it) })
            GlassTextField("Usages path", j.str("usages_path"), { j.setStr("usages_path", it) })
            if (detourTags.isNotEmpty()) {
                SelectField(label = "Detour", value = j.str("detour"),
                    options = (listOf("") + detourTags).map { Opt(it.ifBlank { "none" }, it) },
                    onChange = { v -> j.setStr("detour", v?.takeIf { it.isNotBlank() }) })
            }
            SectionHeader("Users")
            val users = j.arr("users")
            if (users != null) {
                for (i in 0 until users.length()) {
                    val u = users.optJSONObject(i)?.let(::J) ?: continue
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassTextField("Name", u.str("name"), { v -> u.setStr("name", v, blankRemoves = false) }, Modifier.fillMaxWidth())
                        GlassTextField("Token", u.str("token"), { v -> u.setStr("token", v, blankRemoves = false) }, modifier = Modifier.fillMaxWidth(), obscure = true)
                        IconGhostButton(Icons.Rounded.DeleteOutline, {
                            val nl = org.json.JSONArray()
                            for (x in 0 until users.length()) if (x != i) nl.put(users.getJSONObject(x))
                            j.o.put("users", nl)
                        }, tint = LocalGlass.current.err)
                    }
                }
            }
            Row {
                com.sonix21.suinode.ui.glass.GhostButton("+ Add user") {
                    val cur = j.arr("users") ?: com.sonix21.suinode.core.jarr(emptyList<Nothing>())
                    val nl = org.json.JSONArray()
                    for (x in 0 until cur.length()) nl.put(cur.getJSONObject(x))
                    nl.put(jo("name" to "", "token" to ""))
                    j.o.put("users", nl)
                }
            }
        }
    }
}
