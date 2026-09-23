package com.sonix21.suinode.ui.screens.routing

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.MultiSelectField
import com.sonix21.suinode.ui.glass.NumberField
import com.sonix21.suinode.ui.glass.Opt
import com.sonix21.suinode.ui.glass.PrimaryButton
import com.sonix21.suinode.ui.glass.SectionHeader
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.useSession
import org.json.JSONArray
import org.json.JSONObject

val SNIFF_PROTOCOLS = listOf("http", "tls", "quic", "stun", "dns", "bittorrent", "dtls", "ssh", "rdp", "ntp")

@Composable
fun RuleEditorScreen(nav: NavController, index: Int) {
    val g = LocalGlass.current
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()

    JsonEditSignal.revision
    val isNew = index < 0
    val draftResult = remember(session, index) {
        runCatching { RoutingDraft(data.config, index, session.outboundTags().firstOrNull() ?: "direct") }
    }
    val draft = draftResult.getOrNull()
    if (draft == null) {
        PageScaffold(title = "Routing rule", nav = nav) {
            Text(draftResult.exceptionOrNull()?.message ?: "Unable to load the rule", color = g.err)
        }
        return
    }
    val cfg = draft.config

    PageScaffold(
        title = if (isNew) "New Rule" else "Edit Rule #${index + 1}",
        draftValue = { cfg },
        nav = nav,
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("Save rule", loading = runner.busy) {
                    runner.go {
                        val r = session.save("config", "set", cfg)
                        if (r.isSuccess) { ToastBus.show("saved ✓"); nav.pop() }
                        else throw Exception(r.exceptionOrNull()?.message ?: "failed")
                    }
                }
        },
    ) {
        Column(Modifier.weight(1f)) {
            Column(Modifier.androidScrollR(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                val ruleObj = draft.rule
                val rJ = J(ruleObj)

                GlassCard(contentPadding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SwitchRow("Logical rule (multiple sub-rules)", ruleObj.optString("type") == "logical", draft::setLogical)
                        if (ruleObj.optString("type") == "logical") {
                            SelectField(label = "Mode", value = ruleObj.optString("mode"),
                                options = listOf(Opt("and", "and"), Opt("or", "or")),
                                clearable = false,
                                onChange = { v -> if (v != null) ruleObj.put("mode", v) })
                            SectionHeader("Sub-rules") {
                                IconGhostButton(Icons.Filled.Add, {
                                    ruleObj.getJSONArray("rules").put(JSONObject())
                                }, contentDesc = "add sub-rule")
                            }
                            val subs = ruleObj.getJSONArray("rules")
                            for (si in 0 until subs.length()) {
                                val sub = subs.optJSONObject(si)?.let(::J) ?: continue
                                GlassCard(corner = 14.dp, contentPadding = 12.dp) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row {
                                            Text("Condition ${si + 1}", color = LocalGlass.current.violet, fontSize = 12.sp)
                                            Spacer(Modifier.weight(1f))
                                            if (subs.length() > 1) IconGhostButton(Icons.Rounded.DeleteOutline, {
                                                val nl = JSONArray()
                                                for (x in 0 until subs.length()) if (x != si) nl.put(subs.getJSONObject(x))
                                                ruleObj.put("rules", nl)
                                            }, tint = g.err)
                                        }
                                        ConditionEditor(sub, data)
                                    }
                                }
                            }
                        } else {
                            ConditionEditor(rJ, data)
                        }

                        SwitchRow("Invert match", ruleObj.bool("invert"), { on ->
                            if (on) ruleObj.put("invert", true) else ruleObj.remove("invert")
                        })
                    }
                }

                // ---- action card
                GlassCard(contentPadding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("Action")
                        SelectField(
                            label = "Action",
                            value = ruleObj.optString("action").ifBlank { "route" },
                            options = listOf(
                                Opt("Route", "route"), Opt("Route Options", "route-options"),
                                Opt("Bypass", "bypass"), Opt("Reject", "reject"),
                                Opt("Hijack DNS", "hijack-dns"), Opt("Sniff", "sniff"),
                                Opt("Resolve", "resolve"),
                            ),
                            clearable = false,
                            onChange = { v ->
                                val nv = v ?: return@SelectField
                                ruleObj.put("action", nv)
                                when (nv) {
                                    "route" -> ruleObj.put("outbound", session.outboundTags().firstOrNull() ?: "direct")
                                    else -> ruleObj.remove("outbound")
                                }
                                listOf("override_address", "override_port", "udp_disable_domain_unmapping", "udp_connect", "udp_timeout",
                                    "method", "no_drop", "sniffer", "timeout", "strategy", "server").forEach { ruleObj.remove(it) }
                            },
                        )
                        when (ruleObj.optString("action").ifBlank { "route" }) {
                            "route" -> SelectField(label = "Outbound", value = ruleObj.str("outbound"),
                                options = remember(data) { session.outboundTags(data) }.map { Opt(it, it) },
                                clearable = false, onChange = { v -> if (v != null) ruleObj.put("outbound", v) })
                            "route-options" -> {
                                GlassTextField2("Override address", ruleObj.str("override_address")) { v -> ruleObj.setStr2("override_address", v) }
                                NumberField("Override port", ruleObj.long("override_port").takeIf { it in 1..65534 },
                                    onChange = { v -> if (v == null) ruleObj.remove("override_port") else ruleObj.put("override_port", v) })
                                SwitchRow("UDP disable domain unmapping", ruleObj.bool("udp_disable_domain_unmapping"),
                                    { on -> if (on) ruleObj.put("udp_disable_domain_unmapping", true) else ruleObj.remove("udp_disable_domain_unmapping") })
                                SwitchRow("UDP connect", ruleObj.bool("udp_connect"),
                                    { on -> if (on) ruleObj.put("udp_connect", true) else ruleObj.remove("udp_connect") })
                                GlassTextField2("UDP timeout", ruleObj.str("udp_timeout")) { v -> ruleObj.setStr2("udp_timeout", v) }
                            }
                            "reject" -> {
                                SelectField(label = "Method", value = ruleObj.str("method"),
                                    options = listOf(Opt("Default", "default"), Opt("Drop", "drop")),
                                    onChange = { v ->
                                        if (v == null || v == "default") ruleObj.remove("method") else ruleObj.put("method", v)
                                    })
                                SwitchRow("No drop", ruleObj.bool("no_drop"),
                                    { on -> if (on) ruleObj.put("no_drop", true) else ruleObj.remove("no_drop") })
                            }
                            "sniff" -> {
                                MultiSelectField("Sniffers", ruleObj.arr("sniffer")?.strListX()?.toSet() ?: emptySet(),
                                    SNIFF_PROTOCOLS.map { Opt(it, it) },
                                    { sel -> ruleObj.setStrs("sniffer", sel.toList(), emptyRemoves = false) })
                                GlassTextField2("Timeout", ruleObj.str("timeout")) { v -> ruleObj.setStr2("timeout", v) }
                            }
                            "resolve" -> {
                                SelectField(label = "Strategy", value = ruleObj.str("strategy"),
                                    options = listOf(Opt("Prefer IPv4", "prefer_ipv4"), Opt("Prefer IPv6", "prefer_ipv6"),
                                        Opt("IPv4 only", "ipv4_only"), Opt("IPv6 only", "ipv6_only")),
                                    onChange = { v -> ruleObj.setStr2("strategy", v ?: "") })
                                GlassTextField2("Server", ruleObj.str("server")) { v -> ruleObj.setStr2("server", v) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun org.json.JSONObject.setStr2(key: String, v: String) {
    if (v.isBlank()) remove(key) else put(key, v)
}

@Composable
private fun ConditionEditor(cond: J, data: com.sonix21.suinode.data.PanelData) {
    JsonEditSignal.revision
    val session = useSession()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.sonix21.suinode.ui.screens.shared.RuleReferenceFields(cond, session.inboundTags(data), session.clientNames(data))

        SelectField(label = "IP version", value = cond.int("ip_version").takeIf { it == 4 || it == 6 } ?: 0,
            options = listOf(Opt("any", 0), Opt("4", 4), Opt("6", 6)),
            onChange = { v -> if (v == null || v == 0) cond.o.remove("ip_version") else cond.o.put("ip_version", v) })

        MultiSelectField("Network", cond.arr("network")?.strListX()?.toSet() ?: emptySet(),
            listOf("tcp", "udp", "icmp").map { Opt(it, it) },
            { sel -> if (sel.isEmpty()) cond.o.remove("network") else cond.o.put("network", jarr(sel.toList())) })

        MultiSelectField("Protocol", cond.arr("protocol")?.strListX()?.toSet() ?: emptySet(),
            SNIFF_PROTOCOLS.map { Opt(it, it) },
            { sel -> if (sel.isEmpty()) cond.o.remove("protocol") else cond.o.put("protocol", jarr(sel.toList())) })

        DomainGroupSelector(cond)

        PortsGroup(cond, "port", "port_range", "Ports", "Port ranges")
        SourceIpGroup(cond)
        PortsGroup(cond, "source_port", "source_port_range", "Source ports", "Source port ranges")

        ChipsFieldX("Preferred by", cond, "preferred_by")

        SwitchRow("Match by rule sets", cond.has("rule_set") || cond.has("rule_set_ip_cidr_match_source"), { on ->
            if (on) { cond.o.put("rule_set", jarr(emptyList<String>())); cond.o.put("rule_set_ip_cidr_match_source", false) }
            else { cond.o.remove("rule_set"); cond.o.remove("rule_set_ip_cidr_match_source") }
        })
        if (cond.has("rule_set")) {
            val setTags = data.config.optJSONObject("route")?.optJSONArray("rule_set")
                ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optStringOrNullR("tag") } } ?: emptyList()
            MultiSelectField("Rule sets", cond.arr("rule_set")?.strListX()?.toSet() ?: emptySet(),
                setTags.map { Opt(it, it) },
                { sel -> cond.setStrs("rule_set", sel.toList(), emptyRemoves = false) })
            SwitchRow("IP CIDR matches source", cond.bool("rule_set_ip_cidr_match_source"),
                { on -> cond.o.put("rule_set_ip_cidr_match_source", on) })
        }
    }
}

private fun org.json.JSONObject.optStringOrNullR(k: String): String? =
    if (has(k) && !isNull(k)) optString(k).takeIf { it.isNotBlank() } else null

/** single-select among domain/ip keys like the panel's Domain/IP group. */
@Composable
private fun DomainGroupSelector(cond: J) {
    val groupKeys = listOf("domain", "domain_suffix", "domain_keyword", "domain_regex", "ip_cidr", "ip_is_private")
    val active = groupKeys.firstOrNull { cond.has(it) }
    var mode by remember { mutableStateOf(active ?: "") }
    var bump by remember { mutableIntStateOf(0) }

    SelectField(label = "Domain / IP matching",
        value = mode,
        options = listOf(Opt("none", "")) + groupKeys.map { Opt(it, it) },
        onChange = { sel ->
            val nv = sel ?: ""
            groupKeys.forEach { cond.o.remove(it) }
            mode = nv; bump++
            when (nv) {
                "" -> Unit
                "ip_is_private" -> cond.o.put("ip_is_private", true)
                else -> cond.o.put(nv, jarr(emptyList<String>()))
            }
        },
        clearable = false)

    key(bump) {
        when (mode) {
            "ip_is_private" -> SwitchRow("Private IP ranges", cond.bool("ip_is_private"),
                { cond.o.put("ip_is_private", it) })
            "" -> {}
            else -> LinesArea(cond, mode)
        }
    }
}

@Composable
private fun LinesArea(holder: J, key: String) {
    var text by remember { mutableStateOf((holder.arr(key)?.let { a -> (0 until a.length()).mapNotNull { x -> a.optString(x).takeIf(String::isNotEmpty) } } ?: emptyList()).joinToString("\n")) }
    GlassTextField("$key (one per line)", text, { t ->
        text = t
        val lines = t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) holder.o.remove(key) else holder.o.put(key, jarr(lines))
    }, singleLine = false, minLines = 3, maxLines = 10)
}

@Composable
private fun PortsGroup(cond: J, intKey: String, rangeKey: String, label: String, rangeLabel: String) {
    val hasAny = cond.has(intKey) || cond.has(rangeKey)
    SwitchRow(label, hasAny, { on ->
        if (on) cond.o.put(intKey, jarr(emptyList<Long>())) else { cond.o.remove(intKey); cond.o.remove(rangeKey) }
    })
    if (!hasAny) return
    var intText by remember { mutableStateOf((cond.arr(intKey)?.longListX() ?: emptyList()).joinToString("\n")) }
    var rangeText by remember { mutableStateOf((cond.arr(rangeKey)?.strListX() ?: emptyList()).joinToString("\n")) }
    GlassTextField("$label (one per line)", intText, { t ->
        intText = t
        if (!t.endsWith("\n")) {
            val nums = t.split("\n").mapNotNull { it.trim().toLongOrNull() }
            if (nums.isEmpty()) cond.o.remove(intKey) else cond.o.put(intKey, jarr(nums))
        }
    }, singleLine = false, minLines = 2, maxLines = 8)
    GlassTextField("$rangeLabel e.g. 1000:2000", rangeText, { t ->
        rangeText = t
        val lines = t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) cond.o.remove(rangeKey) else cond.o.put(rangeKey, jarr(lines))
    }, singleLine = false, minLines = 2, maxLines = 8)
}

@Composable
private fun SourceIpGroup(cond: J) {
    val hasAny = cond.has("source_ip_cidr") || cond.has("source_ip_is_private")
    SwitchRow("Source IP", hasAny, { on ->
        if (on) cond.o.put("source_ip_cidr", jarr(emptyList<String>())) else { cond.o.remove("source_ip_cidr"); cond.o.remove("source_ip_is_private") }
    })
    if (hasAny) {
        LinesArea(cond, "source_ip_cidr")
        SwitchRow("Private source IPs", cond.bool("source_ip_is_private"), { cond.o.put("source_ip_is_private", it) })
    }
}

@Composable
private fun ChipsFieldX(label: String, holder: J, key: String, suggestions: List<String> = emptyList()) {
    val values = holder.arr(key)?.strListX() ?: emptyList()
    com.sonix21.suinode.ui.glass.ChipsField(label, values, { newVals ->
        if (newVals.isEmpty()) holder.o.remove(key) else holder.o.put(key, jarr(newVals))
    }, suggestions = suggestions)
}

private fun org.json.JSONArray.strListX(): List<String> =
    (0 until length()).mapNotNull { runCatching { optString(it) }.getOrNull() }

private fun org.json.JSONArray.longListX(): List<Long> =
    (0 until length()).map { when (val v = opt(it)) { is Number -> v.toLong(); is String -> v.toLongOrNull() ?: 0; else -> 0 } }
