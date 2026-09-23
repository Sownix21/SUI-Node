package com.sonix21.suinode.ui.screens.endpoints

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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
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
import com.sonix21.suinode.core.Rand
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.ui.glass.ConfirmDialog
import com.sonix21.suinode.ui.glass.DurationField
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.NumberField
import com.sonix21.suinode.ui.glass.Opt
import com.sonix21.suinode.ui.glass.PrimaryButton
import com.sonix21.suinode.ui.glass.SectionHeader
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.StatusDot
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.nav.Route
import com.sonix21.suinode.ui.screens.ItemsScroll
import com.sonix21.suinode.ui.screens.RecordLoadingPage
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.inbounds.TypeChip
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.shared.CsvField
import com.sonix21.suinode.ui.screens.shared.DialSection
import com.sonix21.suinode.ui.screens.useSession
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun EndpointsScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()
    var deleteTag by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var checks by remember(session) { mutableStateOf(mapOf<String, LatencyResult>()) }
    var testing by remember { mutableStateOf(false) }
    fun test(tags: List<String>) {
        if (testing || runner.busy) return
        testing = true
        scope.launch {
            try {
                for (tag in tags.distinct()) {
                    checks = checks + (tag to LatencyResult(loading = true))
                    val result = try {
                        val response = session.client.get("checkOutbound", mapOf("tag" to tag))
                        if (response.success) LatencyResult.parse(response.objObj())
                        else LatencyResult(error = response.msg.ifBlank { "Test failed" })
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { LatencyResult(error = e.message ?: "Test failed") }
                    checks = checks + (tag to result)
                }
            } finally { testing = false }
        }
    }

    PageScaffold(
        title = "Endpoints",
        subtitle = "${data.endpoints.size} total",
        nav = nav,
        busy = runner.busy,
        actions = { IconGhostButton(Icons.Filled.Refresh, { runner.go { session.load(force = true) } }, contentDesc = "Refresh endpoints") },
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("New endpoint", type = com.sonix21.suinode.ui.glass.HeaderActionType.New) { nav.push(Route.EndpointEditor(0)) }
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End) {
            GhostButton(if (testing) "Testing…" else "Test all", enabled = !testing && !runner.busy && data.endpoints.isNotEmpty()) {
                test(data.endpoints.map { it.optString("tag") }.filter { it.isNotBlank() })
            }
        }
        ItemsScroll(
            items = data.endpoints.sortedBy { it.optLongOr("id") },
            emptyIcon = Icons.Filled.Cloud,
            emptyTitle = "No endpoints",
        ) { ep ->
            val tag = ep.optString("tag")
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tag in data.onlines.outbound) StatusDot(g.blue, 8.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(tag, modifier = Modifier.weight(1f), color = g.text, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TypeChip(ep.optString("type"))
                        }
                        val addr = ep.optJSONArray("address")?.optString(0).takeUnless { it.isNullOrBlank() } ?: "-"
                        val port = ep.optLongOr("listen_port")
                        val peers = ep.optJSONArray("peers")?.length()
                        Text("$addr  ·  port ${if (port > 0) port else "-"}" + (peers?.let { "  ·  $it peer(s)" } ?: ""),
                            color = g.textFaint, fontSize = 11.5.sp, maxLines = 1)
                    }
                }
                val result = checks[tag]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (result?.loading == true) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(when {
                        result?.loading == true -> "Testing…"
                        result?.delayMs != null -> "${result.delayMs} ms"
                        result?.error != null -> "Test failed"
                        else -> "Latency not tested"
                    }, modifier = Modifier.weight(1f), color = if (result?.error != null) g.err else g.textDim, fontSize = 12.sp)
                    IconButton(onClick = { test(listOf(tag)) }, enabled = !testing && !runner.busy) {
                        Icon(Icons.Filled.Speed, contentDescription = "Test endpoint latency", tint = if (testing) g.textFaint else g.teal)
                    }
                    if (EndpointConfig.canOfferQr(ep)) IconGhostButton(Icons.Filled.QrCode2, {
                        nav.push(Route.WgQr(ep.optLongOr("id")))
                    }, contentDesc = "Share WireGuard peers")
                    IconGhostButton(Icons.Rounded.Edit, { nav.push(Route.EndpointEditor(ep.optLongOr("id"))) }, contentDesc = "edit")
                    IconGhostButton(Icons.Rounded.DeleteOutline, { deleteTag = tag }, tint = g.err, contentDesc = "delete")
                }
                result?.error?.let { Text(it, color = g.err, fontSize = 11.sp) }
            }
        }
    }

    deleteTag?.let { tag ->
        ConfirmDialog(title = "Delete endpoint", message = "Delete \"$tag\"?", onConfirm = {
            runner.go {
                val r = session.save("endpoints", "del", tag)
                ToastBus.show(if (r.isSuccess) "deleted" else r.exceptionOrNull()?.message ?: "failed")
            }
        }, onDismiss = { deleteTag = null })
    }
}

// ------------------------------------------------------------------ editor

@Composable
fun EndpointEditorScreen(nav: NavController, id: Long) {
    val g = LocalGlass.current
    val session = useSession()
    val runner = rememberRunner()

    var obj by remember { mutableStateOf<org.json.JSONObject?>(null) }

    var loadAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(id, loadAttempt) {
        if (id > 0) {
            runner.go { obj = session.fetchRecord("endpoints", id) }
        } else {
            obj = EndpointConfig.create("wireguard").put("id", 0L)
                .put("tag", "wireguard-" + Rand.seq(3)).put("listen_port", Rand.int(10000, 60000))

        }
    }

    val o = obj
    if (o == null) {
        RecordLoadingPage("Endpoint", nav, runner) { loadAttempt++ }
        return
    }
    val j = J(o)
    val type = j.str("type")

    suspend fun genWgKey(): Pair<String, String>? {
        val env = session.client.get("keypairs", mapOf("k" to "wireguard"))
        if (!env.success) throw Exception(env.msg)
        val lines = env.objStrList()
        val priv = lines.firstOrNull { it.startsWith("PrivateKey") }?.substring(12)
        val pub = lines.firstOrNull { it.startsWith("PublicKey") }?.substring(11)
        return if (priv != null && pub != null) priv to pub else null
    }

    LaunchedEffect(id, type) {
        if (id <= 0 && j.str("type") == "wireguard" && j.str("private_key").isBlank()) {
            runner.go {
                genWgKey()?.let { (p, pub) ->
                    j.o.put("private_key", p)
                    val ext = j.ensureObj("ext"); ext.o.put("public_key", pub)
                }
            }
        }
    }

    fun changeType(newType: String) {
        if (id > 0) return
        val fresh = EndpointConfig.create(newType)
        fresh.put("id", 0L)
        fresh.put("tag", "$newType-" + Rand.seq(3))
        if (newType == "wireguard")
            fresh.put("listen_port", j.long("listen_port").takeIf { it in 1..65535 } ?: Rand.int(10000, 60000).toLong())
        obj = fresh
    }

    PageScaffold(
        title = if (id <= 0) "New Endpoint" else "Edit Endpoint",
        draftValue = { obj },
        subtitle = "$type · ${o.optString("tag")}",
        nav = nav,
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction(if (id <= 0) "Create endpoint" else "Save changes", loading = runner.busy) {
                    when {
                        j.str("tag").isBlank() -> ToastBus.show("tag is required")
                        session.data.value.endpoints.any {
                            it.optString("tag") == j.str("tag") && it.optLongOr("id") != id
                        } -> ToastBus.show("duplicate tag")
                        EndpointConfig.validate(j.o) != null -> ToastBus.show(EndpointConfig.validate(j.o)!!)
                        else -> runner.go {
                            val r = session.save("endpoints", if (id <= 0) "new" else "edit", EndpointConfig.payload(j.o))
                            if (r.isSuccess) { ToastBus.show("saved ✓"); nav.pop() } else throw Exception(r.exceptionOrNull()?.message)
                        }
                    }
                }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            GlassCard(contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (id > 0) Text(EndpointConfig.types[type] ?: type, color = g.textDim)
                    else SelectField(
                        label = "Type",
                        value = type,
                        options = EndpointConfig.types.map { (value, label) -> Opt(label, value) },
                        onChange = { v -> if (v != null && id <= 0) changeType(v) },
                        clearable = false,
                    )
                    GlassTextField("Tag", j.str("tag"), { v -> j.setStr("tag", v, blankRemoves = false) })
                }
            }

            when (type) {
                "wireguard" -> WireGuardFields(j, ::genWgKey, nav)
                "warp" -> WarpFields(j)
                "tailscale" -> TailscaleFields(j)
                "openconnect" -> OpenConnectFields(j)
                "openvpn-client", "openvpn-server" -> OpenVpnFields(j)
            }

            val currentData = session.data.collectAsState().value
            if (type == "openconnect" || (type.startsWith("openvpn-") && j.str("mode", "tls") == "tls")) VpnTlsFields(j)
            if (type != "openvpn-server") DialSection(j, remember(currentData) { session.outboundTags(currentData) },
                dnsTags = remember(currentData) { session.dnsServerTags(currentData) })
            com.sonix21.suinode.ui.screens.AdvancedJsonCard(j.o) { obj = it }
        }
    }
}

// ------------------------------------------------------------- wireguard

@Composable
private fun WireGuardFields(j: J, genKey: suspend () -> Pair<String, String>?, nav: NavController) {
    val g = LocalGlass.current
    val runner = rememberRunner()
    val session = useSession()
    val ext = j.ensureObj("ext")

    CardE("WireGuard") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTextField("Private key", j.str("private_key"),
                { v ->
                    j.setStr("private_key", v)
                    ext.o.put("public_key", "")
                }, modifier = Modifier.weight(1f), obscure = true)
            IconGhostButton(Icons.Filled.Key, {
                runner.go { genKey()?.let { (p, pub) -> j.o.put("private_key", p); ext.o.put("public_key", pub) } }
            }, contentDesc = "generate keypair")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTextField("Public key", ext.str("public_key"),
                { v -> ext.o.put("public_key", v) }, modifier = Modifier.weight(1f))
            IconGhostButton(Icons.Filled.Refresh, {
                runner.go {
                    val env = session.client.get("keypairs", mapOf("k" to "wireguard", "o" to j.str("private_key")))
                    if (!env.success) throw Exception(env.msg)
                    env.objStrList().firstOrNull()?.let { ext.o.put("public_key", it) }
                }
            }, contentDesc = "derive public key")
        }
        CsvField("Local IPs", j.arr("address")?.strListX() ?: emptyList(), { parts ->
            if (parts.isEmpty()) j.o.remove("address") else j.o.put("address", jarr(parts))
        })
        NumberField("Listen port", j.long("listen_port").takeIf { it > 0 }, onChange = { j.o.put("listen_port", it ?: 0) })
        SwitchRow("System interface", j.bool("system"), { j.setBool("system", it, onlyTrue = true) })
        if (j.bool("system"))
            GlassTextField("Interface name", j.str("name"), { v -> j.setStr("name", v) })

        OptionalNumE(j, "udp_timeout", "UDP timeout (min)", 5, minutes = true)
        OptionalNumE(j, "workers", "Workers", 2)
        OptionalNumE(j, "mtu", "MTU", 1408)

        // peers
        SectionHeader("Peers") {
            IconGhostButton(Icons.Filled.Key, {
                runner.go {
                    genKey()?.let { (p, pub) ->
                        ext.ensureArrKeys().put(jo("private_key" to p, "public_key" to pub))
                        j.arr("peers")?.put(jo("public_key" to pub, "allowed_ips" to jarr(listOf(freeIpForPeer(j)))))
                            ?: j.o.put("peers", jarr(listOf(jo("public_key" to pub, "allowed_ips" to jarr(listOf(freeIpForPeer(j)))))))
                    }
                }
            }, contentDesc = "add peer")
        }
        val peers = j.arr("peers")
        if (peers != null) {
            for (i in 0 until peers.length()) {
                val p = peers.optJSONObject(i)?.let(::J) ?: continue
                PeerCard(p, ext, j, onDelete = {
                    val arr = j.arr("peers")!!
                    val newList = org.json.JSONArray()
                    for (x in 0 until arr.length()) if (x != i) newList.put(arr.getJSONObject(x))
                    j.o.put("peers", newList)
                })
            }
        } else Text("no peers — add one to allow clients", color = g.textFaint, fontSize = 12.sp)

        GlassTextField("Reserved server field (ext)", ext.str("server"), { v -> ext.setStr("server", v) })
        GlassTextField("DNS (comma separated)", ext.str("dns"), { v -> ext.setStr("dns", v) })
    }
}

@Composable
private fun PeerCard(p: J, ext: J, parent: J, onDelete: () -> Unit) {
    val runner = rememberRunner()
    val session = useSession()
    GlassCard(corner = 14.dp, contentPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Peer", color = LocalGlass.current.violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                IconGhostButton(Icons.Filled.Refresh, {
                    runner.go {
                        val env = session.client.get("keypairs", mapOf("k" to "wireguard"))
                        if (!env.success) throw Exception(env.msg)
                        val priv = env.objStrList().firstOrNull { it.startsWith("PrivateKey") }?.substring(12) ?: ""
                        val pub = env.objStrList().firstOrNull { it.startsWith("PublicKey") }?.substring(11) ?: ""
                        val oldPublicKey = p.str("public_key")
                        p.o.put("public_key", pub)
                        // Match the old public key before replacing it.
                        val keys = ext.arr("keys")
                        if (keys != null) for (x in 0 until keys.length()) {
                            val k = keys.optJSONObject(x)
                            if (k?.optString("public_key") == oldPublicKey) k.put("public_key", pub).also { k.put("private_key", priv) }
                        }
                    }
                }, contentDesc = "regenerate keys")
                IconGhostButton(Icons.Rounded.DeleteOutline, onDelete, tint = LocalGlass.current.err, contentDesc = "delete peer")
            }
            GlassTextField("Public key", p.str("public_key"), { v -> p.setStr("public_key", v) })
            GlassTextField("Pre-shared key", p.str("pre_shared_key"), { v -> p.setStr("pre_shared_key", v) }, obscure = true)
            GlassTextField("Server address", p.str("address"), { v -> p.setStr("address", v) })
            NumberField("Port", p.long("port").takeIf { it > 0 }, onChange = { v -> p.o.put("port", v ?: 0) })
            NumberField("KeepAlive", p.long("persistent_keepalive_interval").takeIf { it > 0 }, suffix = "s",
                onChange = { v -> if (v == null || v <= 0) p.o.remove("persistent_keepalive_interval") else p.o.put("persistent_keepalive_interval", v) })
            CsvField("Allowed IPs", p.arr("allowed_ips")?.strListX() ?: emptyList(), { parts ->
                if (parts.isEmpty()) p.o.remove("allowed_ips") else p.o.put("allowed_ips", jarr(parts))
            })
            CsvField("Reserved", (p.arr("reserved")?.let { a -> (0 until a.length()).map { a.optLongOrR(it) } } ?: emptyList()).map { it.toString() }, { parts ->
                if (parts.isEmpty()) p.o.remove("reserved")
                else p.o.put("reserved", jarr(parts.mapNotNull { it.toIntOrNull() }))
            }, hint = "e.g. 12,34,56")
        }
    }
}

private fun freeIpForPeer(parent: J): String {
    val used = mutableSetOf<String>()
    parent.arr("peers")?.let { arr ->
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.optJSONArray("allowed_ips")?.let { a ->
            for (x in 0 until a.length()) used.add(a.optString(x))
        }
    }
    for (n in 2..254) {
        val cand = "10.0.1.$n/32"
        if (cand !in used) return cand
    }
    return "0.0.0.0/0"
}

private fun J.ensureArrKeys() =
    this.ensureArr("keys")

// -------------------------------------------------------------------- warp

@Composable
private fun WarpFields(j: J) {
    val peer = j.arr("peers")?.optJSONObject(0)?.let(::J)
    CardE("WARP") {
        GlassTextField("Private key", j.str("private_key"), { j.setStr("private_key", it) }, obscure = true)
        CsvField("Local IPs", j.arr("address")?.strListX() ?: emptyList(), { parts ->
            if (parts.isEmpty()) j.o.remove("address") else j.o.put("address", jarr(parts))
        })
        GlassTextField("License key", j.ensureObj("ext").str("license_key"), { v ->
            if (v.isBlank()) j.obj("ext")?.remove("license_key") else j.obj("ext")?.o?.put("license_key", v)
        })
        if (peer != null) {
            GlassTextField("Server address", peer.str("address"), { v -> peer.setStr("address", v, blankRemoves = false) })
            NumberField("Server port", peer.long("port").takeIf { it > 0 }, onChange = { v -> peer.o.put("port", v ?: 0) })
        }
        OptionalNumE(j, "udp_timeout", "UDP timeout (min)", 5, minutes = true)
        OptionalNumE(j, "workers", "Workers", 2)
        OptionalNumE(j, "mtu", "MTU", 1408)
        SwitchRow("System interface", j.bool("system"), { j.setBool("system", it, onlyTrue = true) })
        if (j.bool("system")) GlassTextField("Interface name", j.str("name"), { v -> j.setStr("name", v) })
    }
}

// --------------------------------------------------------------- tailscale

@Composable
private fun TailscaleFields(j: J) {
    CardE("Tailscale") {
        SwitchRow("Ephemeral mode", j.bool("ephemeral"), { j.setBool("ephemeral", it, onlyTrue = true) })
        SwitchRow("Accept routes", j.bool("accept_routes"), { j.setBool("accept_routes", it, onlyTrue = true) })
        ToggleText(j, "state_directory", "State directory", "\$HOME/.tailscale")
        ToggleText(j, "auth_key", "Authentication key", "")
        ToggleText(j, "control_url", "Control URL", "https://controlplane.tailscale.com")
        ToggleText(j, "hostname", "Hostname", "localhost")
        SwitchRow("Exit node group", j.has("exit_node"), { on ->
            if (on) { j.o.put("exit_node", ""); j.o.put("exit_node_allow_lan_access", false) }
            else { j.o.remove("exit_node"); j.o.remove("exit_node_allow_lan_access") }
        })
        if (j.has("exit_node")) {
            GlassTextField("Exit node", j.str("exit_node"), { j.setStr("exit_node", it, blankRemoves = false) })
            SwitchRow("Allow LAN access", j.bool("exit_node_allow_lan_access"),
                { j.o.put("exit_node_allow_lan_access", it) })
        }
        SwitchRow("Relay server", j.has("relay_server_port"), { on ->
            if (on) { j.o.put("relay_server_port", 0); j.o.put("relay_server_static_endpoints", jarr(emptyList<String>())) }
            else { j.o.remove("relay_server_port"); j.o.remove("relay_server_static_endpoints") }
        })
        if (j.has("relay_server_port")) {
            NumberField("Relay port", j.long("relay_server_port"), onChange = { j.o.put("relay_server_port", it ?: 0) })
            CsvField("Relay static endpoints", j.arr("relay_server_static_endpoints")?.strListX() ?: emptyList(), { parts ->
                if (parts.isEmpty()) j.o.remove("relay_server_static_endpoints") else j.o.put("relay_server_static_endpoints", jarr(parts))
            })
        }
        SwitchRow("System interface", j.bool("system_interface"), { on ->
            if (on) j.o.put("system_interface", true) else { j.o.remove("system_interface"); j.o.remove("system_interface_name"); j.o.remove("system_interface_mtu") }
        })
        if (j.bool("system_interface")) {
            GlassTextField("Interface name", j.str("system_interface_name"), { v -> j.setStr("system_interface_name", v) })
            NumberField("Interface MTU", j.long("system_interface_mtu").takeIf { it > 0 }, onChange = { j.setLong("system_interface_mtu", it) })
        }
        SwitchRow("Advertise routes", j.has("advertise_routes"), { on ->
            if (on) { j.o.put("advertise_routes", jarr(emptyList<String>())) ; j.o.put("advertise_exit_node", false) }
            else { j.o.remove("advertise_routes"); j.o.remove("advertise_exit_node") }
        })
        if (j.has("advertise_routes")) {
            CsvField("Routes", j.arr("advertise_routes")?.strListX() ?: emptyList(), { parts ->
                if (parts.isEmpty()) j.o.remove("advertise_routes") else j.o.put("advertise_routes", jarr(parts))
            })
            SwitchRow("Advertise exit node", j.bool("advertise_exit_node"), { j.o.put("advertise_exit_node", it) })
        }
        DurationField("UDP timeout", j.optStringOrNull("udp_timeout"), 's', fallbackSeconds = 30,
            onChange = { j.setOrRemove("udp_timeout", it) })
    }
}

@Composable
private fun ToggleText(j: J, key: String, label: String, defOn: String) {
    SwitchRow(label, j.has(key), { on -> if (on) j.o.put(key, defOn) else j.o.remove(key) })
    if (j.has(key)) GlassTextField(label, j.str(key), { v -> j.setStr(key, v, blankRemoves = false) })
}

@Composable
private fun OptionalNumE(j: J, key: String, label: String, def: Long, minutes: Boolean = false) {
    SwitchRow(label, j.has(key), { on -> if (on) j.o.put(key, if (minutes) "${def}m" else def) else j.o.remove(key) })
    if (j.has(key)) {
        if (minutes) DurationField(label, j.optStringOrNull(key), 'm', fallbackSeconds = 300,
            onChange = { j.setOrRemove(key, it) })
        else NumberField(label, j.long(key), onChange = { v -> j.o.put(key, v ?: def) })
    }
}

@Composable
private fun CardE(title: String, content: @Composable () -> Unit) {
    GlassCard(contentPadding = 14.dp) {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

private fun org.json.JSONArray.optLongOrR(idx: Int): Long = when (val v = opt(idx)) {
    is Number -> v.toLong(); is String -> v.toLongOrNull() ?: 0; else -> 0
}
