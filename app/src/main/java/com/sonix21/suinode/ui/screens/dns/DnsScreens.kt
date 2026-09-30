package com.sonix21.suinode.ui.screens.dns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.nav.Route
import com.sonix21.suinode.ui.screens.*
import com.sonix21.suinode.ui.screens.inbounds.TypeChip
import com.sonix21.suinode.ui.screens.shared.CsvField
import com.sonix21.suinode.ui.screens.shared.RuleReferenceFields
import com.sonix21.suinode.ui.screens.shared.DialSection
import com.sonix21.suinode.ui.screens.shared.HeadersCard
import com.sonix21.suinode.ui.screens.shared.OutTlsEditor
import org.json.JSONArray
import org.json.JSONObject

val DNS_TYPES = listOf("local", "mdns", "hosts", "tcp", "udp", "tls", "quic", "https", "h3", "dhcp", "fakeip", "tailscale", "resolved")
private val SERVER_TYPES = setOf("tcp", "udp", "tls", "quic", "https", "h3")
private val TLS_TYPES = setOf("tls", "quic", "https", "h3")

@Composable
fun DnsScreen(nav: NavController) {
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()
    val config = remember(session) { data.config.deepCopy() }
    val dns = config.optJSONObject("dns") ?: JSONObject().also {
        it.put("servers", JSONArray()); it.put("rules", JSONArray()); config.put("dns", it)
    }
    val servers = dns.optJSONArray("servers") ?: JSONArray().also { dns.put("servers", it) }
    val rules = dns.optJSONArray("rules") ?: JSONArray().also { dns.put("rules", it) }
    val g = LocalGlass.current

    fun save(label: String = "DNS saved") = runner.go {
        val result = session.save("config", "set", config.deepCopy())
        if (result.isFailure) throw Exception(result.exceptionOrNull()?.message)
        ToastBus.show(label)
    }
    fun remove(arr: JSONArray, index: Int) {
        val next = JSONArray(); for (i in 0 until arr.length()) if (i != index) next.put(arr.opt(i))
        if (arr === servers) dns.put("servers", next) else dns.put("rules", next)
        JsonEditSignal.bump()
    }
    fun move(arr: JSONArray, from: Int, to: Int) {
        if (to !in 0 until arr.length()) return
        val list = (0 until arr.length()).map { arr.opt(it) }.toMutableList()
        val item = list.removeAt(from); list.add(to, item)
        val next = JSONArray(); list.forEach(next::put)
        if (arr === servers) dns.put("servers", next) else dns.put("rules", next)
        JsonEditSignal.bump()
    }

    PageScaffold(title = "DNS", subtitle = "${servers.length()} servers · ${rules.length()} rules", nav = nav, busy = runner.busy,
        draftValue = { config },
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("Save DNS") { save() }
        }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(contentPadding = 14.dp) {
                SectionHeader("Global DNS")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SelectField("Final server", dns.optStringOrNull("final"), (0 until servers.length()).mapNotNull { servers.optJSONObject(it)?.optStringOrNull("tag") }.map { Opt(it, it) },
                        onChange = { dns.setOrRemove("final", it) })
                    SelectField("Strategy", dns.optStringOrNull("strategy"), listOf("prefer_ipv4", "prefer_ipv6", "ipv4_only", "ipv6_only").map { Opt(it, it) },
                        onChange = { dns.setOrRemove("strategy", it) })
                    SwitchRow("Disable cache", dns.optBoolOr("disable_cache"), { dns.setOrRemove("disable_cache", it.takeIf(Boolean::not)?.let { false } ?: if (it) true else null) })
                    SwitchRow("Disable expiry", dns.optBoolOr("disable_expire"), { dns.setOrRemove("disable_expire", if (it) true else null) })
                    SwitchRow("Independent cache", dns.optBoolOr("independent_cache"), { dns.setOrRemove("independent_cache", if (it) true else null) })
                    SwitchRow("Reverse mapping", dns.optBoolOr("reverse_mapping"), { dns.setOrRemove("reverse_mapping", if (it) true else null) })
                    NumberField("Cache capacity", dns.optLongOr("cache_capacity").takeIf { it > 0 }, onChange = { dns.setOrRemove("cache_capacity", it) })
                    GlassTextField("Client subnet", dns.optString("client_subnet"), { dns.setOrRemove("client_subnet", it.takeIf(String::isNotBlank)) })
                }
            }

            SectionHeader("Servers") { IconGhostButton(Icons.Filled.Add, { nav.push(Route.DnsServerEditor(-1)) }) }
            for (i in 0 until servers.length()) {
                val s = servers.optJSONObject(i) ?: continue
                GlassCard(contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(s.optString("tag").ifBlank { "Server ${i + 1}" }, color = g.text, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { TypeChip(s.optString("type")); Text(s.optString("server", "—"), color = g.textFaint, fontSize = 11.sp) }
                        }
                        IconGhostButton(Icons.Rounded.Edit, { nav.push(Route.DnsServerEditor(i)) })
                        IconGhostButton(Icons.Rounded.DeleteOutline, { remove(servers, i) }, tint = g.err)
                    }
                }
            }

            SectionHeader("Rules") { IconGhostButton(Icons.Filled.Add, { nav.push(Route.DnsRuleEditor(-1)) }) }
            for (i in 0 until rules.length()) {
                val r = rules.optJSONObject(i) ?: continue
                GlassCard(contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("#${i + 1}", color = g.violet, fontWeight = FontWeight.Black)
                        Column(Modifier.weight(1f)) {
                            Text(r.optString("action", "route"), color = g.text, fontWeight = FontWeight.Bold)
                            Text(if (r.optString("type") == "logical") "logical · ${r.optString("mode")}" else "${r.length()} fields", color = g.textFaint, fontSize = 11.sp)
                        }
                        if (i > 0) IconGhostButton(Icons.Filled.ArrowUpward, { move(rules, i, i - 1) })
                        if (i < rules.length() - 1) IconGhostButton(Icons.Filled.ArrowDownward, { move(rules, i, i + 1) })
                        IconGhostButton(Icons.Rounded.Edit, { nav.push(Route.DnsRuleEditor(i)) })
                        IconGhostButton(Icons.Rounded.DeleteOutline, { remove(rules, i) }, tint = g.err)
                    }
                }
            }
            AdvancedJsonCard(dns) { config.put("dns", it) }
        }
    }
}

private fun defaultDns(type: String, tag: String): JSONObject = when (type) {
    "hosts" -> jo("type" to type, "tag" to tag, "path" to jarr(listOf("/etc/hosts")))
    "tcp", "udp" -> jo("type" to type, "tag" to tag, "server" to "", "server_port" to 53)
    "tls", "quic" -> jo("type" to type, "tag" to tag, "server" to "", "server_port" to 853, "tls" to JSONObject())
    "https", "h3" -> jo("type" to type, "tag" to tag, "server" to "", "server_port" to 443, "path" to "", "tls" to JSONObject(), "headers" to JSONObject())
    "fakeip" -> jo("type" to type, "tag" to tag, "inet4_range" to "198.18.0.0/15", "inet6_range" to "fc00::/18")
    else -> jo("type" to type, "tag" to tag)
}

@Composable
fun DnsServerEditorScreen(nav: NavController, index: Int) {
    val session = useSession(); val data by session.data.collectAsState(); val runner = rememberRunner()
    val config = remember(session) { data.config.deepCopy() }
    val source = config.optJSONObject("dns")?.optJSONArray("servers")
    var obj by remember(index) { mutableStateOf(if (index >= 0) source?.optJSONObject(index)?.deepCopy() else defaultDns("local", "dns-${Rand.seq(3)}")) }
    val j = J(obj ?: defaultDns("local", "dns-${Rand.seq(3)}")); val type = j.str("type")
    PageScaffold(title = if (index < 0) "New DNS server" else "Edit DNS server", nav = nav, busy = runner.busy,
        draftValue = { obj },
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("Save server") {
            if (j.str("tag").isBlank()) { ToastBus.show("Tag is required"); return@HeaderPrimaryAction }
            val dns = config.optJSONObject("dns") ?: JSONObject().also { config.put("dns", it) }
            val arr = dns.optJSONArray("servers") ?: JSONArray()
            if ((0 until arr.length()).any { it != index && arr.optJSONObject(it)?.optString("tag") == j.str("tag") }) { ToastBus.show("Duplicate DNS tag"); return@HeaderPrimaryAction }
            val next = JSONArray(); for (i in 0 until arr.length()) next.put(if (i == index) j.o else arr.opt(i)); if (index < 0) next.put(j.o)
            dns.put("servers", next); runner.go { val r = session.save("config", "set", config.deepCopy()); if (r.isFailure) throw Exception(r.exceptionOrNull()?.message); nav.pop() }
        }
        }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(contentPadding = 14.dp) { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SelectField("Type", type, DNS_TYPES.map { Opt(it, it) }, clearable = false, onChange = { t -> t?.let { obj = defaultDns(it, j.str("tag")) } })
                GlassTextField("Tag", j.str("tag"), { j.setStr("tag", it, false) })
                if (type in SERVER_TYPES) { GlassTextField("Server", j.str("server"), { j.setStr("server", it, false) }); NumberField("Port", j.long("server_port"), onChange = { j["server_port"] = it ?: 0 }) }
                if (type in setOf("https", "h3")) GlassTextField("Path", j.str("path"), { j.setStr("path", it) })
                when (type) {
                    "local" -> SwitchRow("Prefer Go resolver", j.bool("prefer_go"), { j.setBool("prefer_go", it, true) })
                    "hosts" -> CsvField("Hosts file paths", j.strs("path"), { j.setStrs("path", it) })
                    "dhcp" -> GlassTextField("Interface", j.str("interface"), { j.setStr("interface", it) })
                    "fakeip" -> { GlassTextField("IPv4 range", j.str("inet4_range"), { j.setStr("inet4_range", it) }); GlassTextField("IPv6 range", j.str("inet6_range"), { j.setStr("inet6_range", it) }) }
                    "tailscale" -> SelectField("Endpoint", j.optStringOrNull("endpoint"), session.tailscaleEndpointTags(data).map { Opt(it, it) }, onChange = { j["endpoint"] = it })
                    "resolved" -> SelectField("Service", j.optStringOrNull("service"), session.resolvedServiceTags(data).map { Opt(it, it) }, onChange = { j["service"] = it })
                }
                if (type in setOf("tailscale", "resolved")) SwitchRow("Accept default resolvers", j.bool("accept_default_resolvers"), { j.setBool("accept_default_resolvers", it, true) })
            } }
            if (type !in setOf("hosts", "tailscale", "fakeip", "resolved")) DialSection(j, session.outboundTags(data), dnsTags = session.dnsServerTags(data))
            if (type in TLS_TYPES) OutTlsEditor(j.ensureObj("tls"))
            if (type in setOf("https", "h3")) HeadersCard(j)
            AdvancedJsonCard(j.o) { obj = it }
        }
    }
}

@Composable
fun DnsRuleEditorScreen(nav: NavController, index: Int) {
    val session = useSession(); val data by session.data.collectAsState(); val runner = rememberRunner()
    val config = remember(session) { data.config.deepCopy() }
    val source = config.optJSONObject("dns")?.optJSONArray("rules")
    var obj by remember(index) { mutableStateOf(if (index >= 0) source?.optJSONObject(index)?.deepCopy() else jo("action" to "route")) }
    val j = J(obj ?: jo("action" to "route")); val action = j.str("action", "route")
    PageScaffold(title = if (index < 0) "New DNS rule" else "Edit DNS rule", nav = nav, busy = runner.busy,
        draftValue = { obj },
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("Save rule") {
            val dns = config.optJSONObject("dns") ?: JSONObject().also { config.put("dns", it) }; val arr = dns.optJSONArray("rules") ?: JSONArray(); val next = JSONArray()
            for (i in 0 until arr.length()) next.put(if (i == index) j.o else arr.opt(i)); if (index < 0) next.put(j.o); dns.put("rules", next)
            runner.go { val r = session.save("config", "set", config.deepCopy()); if (r.isFailure) throw Exception(r.exceptionOrNull()?.message); nav.pop() }
        }
        }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(contentPadding = 14.dp) { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SwitchRow("Invert match", j.bool("invert"), { j.setBool("invert", it, true) })
                SelectField("Action", action, listOf("route", "route-options", "reject", "predefined").map { Opt(it, it) }, clearable = false, onChange = { j["action"] = it ?: "route" })
                when (action) {
                    "route" -> SelectField("DNS server", j.optStringOrNull("server"), session.dnsServerTags(data).map { Opt(it, it) }, onChange = { j["server"] = it })
                    "route-options" -> { SelectField("Strategy", j.optStringOrNull("strategy"), listOf("prefer_ipv4","prefer_ipv6","ipv4_only","ipv6_only").map { Opt(it,it) }, onChange = { j["strategy"] = it }); SwitchRow("Disable cache", j.bool("disable_cache"), { j.setBool("disable_cache", it, true) }); NumberField("Rewrite TTL", j.long("rewrite_ttl").takeIf { it > 0 }, onChange = { j.setLong("rewrite_ttl", it) }) }
                    "reject" -> { SelectField("Method", j.str("method", "default"), listOf("default","drop").map { Opt(it,it) }, clearable = false, onChange = { j["method"] = it }); SwitchRow("No drop", j.bool("no_drop"), { j.setBool("no_drop", it, true) }) }
                    "predefined" -> { GlassTextField("RCode", j.str("rcode", "NOERROR"), { j.setStr("rcode", it) }); CsvField("Answers", j.strs("answer"), { j.setStrs("answer", it) }); CsvField("Authority (NS)", j.strs("ns"), { j.setStrs("ns", it) }); CsvField("Extra", j.strs("extra"), { j.setStrs("extra", it) }) }
                }
            } }
            GlassCard(contentPadding = 14.dp) { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SectionHeader("Common match conditions")
                RuleReferenceFields(j, session.inboundTags(data), session.clientNames(data))
                CsvField("Domains", j.strs("domain"), { j.setStrs("domain", it) })
                CsvField("Domain suffixes", j.strs("domain_suffix"), { j.setStrs("domain_suffix", it) })
                CsvField("IP CIDRs", j.strs("ip_cidr"), { j.setStrs("ip_cidr", it) })
                CsvField("Query types", j.strs("query_type"), { j.setStrs("query_type", it) })
                SwitchRow("Private IP", j.bool("ip_is_private"), { j.setBool("ip_is_private", it, true) })
            } }
            AdvancedJsonCard(j.o) { obj = it }
        }
    }
}
