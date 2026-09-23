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
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DownloadForOffline
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
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.inbounds.TypeChip
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.useSession

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutingScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()

    val config = remember(session) { data.config.deepCopy() }
    val routeObj = config.optJSONObject("route") ?: org.json.JSONObject().also {
        config.put("route", it)
    }
    val rJ = J(routeObj)
    val rules = routeObj.optJSONArray("rules")
    val ruleSets = routeObj.optJSONArray("rule_set")?.objListX() ?: emptyList()
    var deleteSetTag by remember { mutableStateOf<String?>(null) }

    fun saveConfig(label: String) {
        runner.go {
            val r = session.save("config", "set", config.deepCopy())
            if (r.isSuccess) ToastBus.show("$label ✓ (core restarts automatically)")
            else throw Exception(r.exceptionOrNull()?.message ?: "save failed")
        }
    }

    PageScaffold(
        title = "Routing",
        draftValue = { config },
        subtitle = "${rules?.length() ?: 0} rules · ${ruleSets.size} rulesets",
        nav = nav,
        busy = runner.busy,
        actions = {
            IconGhostButton(Icons.Filled.Add, { nav.push(Route.RuleEditor(-1)) }, contentDesc = "add rule")
        },
    ) {
        Column(Modifier.weight(1f)) {
            Column(Modifier.androidScrollR(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ---------- header
                GlassCard(contentPadding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader("Defaults") {
                            PrimaryButton("Save") { saveConfig("routing saved") }
                        }
                        SelectField(
                            label = "Default outbound (final)",
                            value = rJ.str("final"),
                            options = (listOf("") + remember(data) { session.outboundTags(data) }).map { Opt(it.ifBlank { "none" }, it) },
                            onChange = { v -> rJ.setStr("final", v?.takeIf { it.isNotBlank() }) },
                        )
                        GlassTextField2("Default NIC", rJ.str("default_interface")) { v ->
                            if (v.isBlank()) rJ.o.remove("default_interface") else rJ.o.put("default_interface", v)
                        }
                        NumberField("Default routing mark", rJ.long("default_mark").takeIf { it > 0 },
                            onChange = { v -> if (v == null || v <= 0) rJ.o.remove("default_mark") else rJ.o.put("default_mark", v) })
                        SwitchRow("Auto bind NIC", rJ.bool("auto_detect_interface"), { on ->
                            rJ.setBool("auto_detect_interface", on, onlyTrue = true)
                        })
                    }
                }

                // ---------- rulesets
                SectionHeader("Rule sets") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconGhostButton(Icons.Filled.Add, { nav.push(Route.RulesetEditor(null)) }, contentDesc = "add ruleset")
                    }
                }
                ruleSets.forEach { rs ->
                    GlassCard(contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(rs.optString("tag"), color = g.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    TypeChip(if (rs.optString("type") == "local") "local" else "remote")
                                }
                                Text(
                                    buildString {
                                        append("format ${rs.optStringOrNull("format") ?: "source"} · ")
                                        append("detour ${rs.optStringOrNull("download_detour") ?: "-"} · ")
                                        append("interval ${rs.optStringOrNull("update_interval") ?: "-"}")
                                    },
                                    color = g.textFaint, fontSize = 11.sp,
                                )
                            }
                            IconGhostButton(Icons.Rounded.Edit, { nav.push(Route.RulesetEditor(rs.optString("tag"))) }, contentDesc = "Edit rule set")
                            IconGhostButton(Icons.Rounded.DeleteOutline, { deleteSetTag = rs.optString("tag") }, tint = g.err)
                        }
                    }
                }

                // ---------- rules
                SectionHeader("Rules") {}
                val n = rules?.length() ?: 0
                for (i in 0 until n) {
                    val rule = rules!!.optJSONObject(i)?.let(::J) ?: continue
                    GlassCard(contentPadding = 12.dp) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("#${i + 1}", color = g.violet, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.weight(0.14f))
                                Column(Modifier.weight(1f)) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TypeChip(rule.str("action").ifBlank { "route" })
                                        if (rule.str("type") == "logical") TypeChip("logical:${rule.str("mode")}")
                                        if (rule.bool("invert")) TypeChip("invert")
                                        if (!rule.has("action") || rule.str("action") == "route")
                                            Text("→ ${rule.str("outbound")}", color = g.teal, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(conditionSummary(rule), color = g.textFaint, fontSize = 11.sp, maxLines = 2)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    if (i > 0) IconGhostButton(Icons.Filled.ArrowUpward, {
                                        moveRule(routeObj, i, i - 1); saveConfig("reordered")
                                    }, contentDesc = "up")
                                    if (i < n - 1) IconGhostButton(Icons.Filled.ArrowDownward, {
                                        moveRule(routeObj, i, i + 1); saveConfig("reordered")
                                    }, contentDesc = "down")
                                IconGhostButton(Icons.Rounded.Edit, { nav.push(Route.RuleEditor(i)) }, contentDesc = "Edit rule")
                                IconGhostButton(Icons.Rounded.DeleteOutline, {
                                    val nr = org.json.JSONArray()
                                    for (x in 0 until n) if (x != i) nr.put(rules.getJSONObject(x))
                                    routeObj.put("rules", nr); saveConfig("rule deleted")
                                }, tint = g.err)
                            }
                        }
                    }
                }
            }
        }
    }

    deleteSetTag?.let { tag ->
        ConfirmDialog(title = "Delete ruleset", message = "Remove \"$tag\"?", onConfirm = {
            val arr = routeObj.optJSONArray("rule_set") ?: return@ConfirmDialog
            val nl = org.json.JSONArray()
            for (x in 0 until arr.length()) {
                val o = arr.optJSONObject(x) ?: continue
                if (o.optString("tag") != tag) nl.put(o)
            }
            routeObj.put("rule_set", nl)
            saveConfig("ruleset deleted")
        }, onDismiss = { deleteSetTag = null })
    }
}

private fun moveRule(routeObj: org.json.JSONObject, from: Int, to: Int) {
    val arr = routeObj.optJSONArray("rules") ?: return
    val moved = arr.getJSONObject(from)
    val nl = org.json.JSONArray()
    for (x in 0 until arr.length()) {
        if (x == to) {
            if (to > from) { nl.put(arr.getJSONObject(x)); nl.put(moved) } else { nl.put(moved); nl.put(arr.getJSONObject(x)) }
        } else if (x != from) nl.put(arr.getJSONObject(x))
    }
    routeObj.put("rules", nl)
}

fun conditionSummary(rule: J): String {
    val keys = listOf(
        "inbound", "auth_user", "ip_version", "network", "protocol",
        "domain", "domain_suffix", "domain_keyword", "domain_regex", "ip_cidr", "ip_is_private",
        "port", "port_range", "source_ip_cidr", "source_ip_is_private",
        "source_port", "source_port_range", "rule_set", "clash_mode",
    )
    return keys.mapNotNull { k ->
        when (val v = rule.o.opt(k)) {
            null -> null
            is org.json.JSONArray -> "$k(${v.length()})"
            org.json.JSONObject.NULL -> null
            is Boolean -> if (v) k else null
            else -> "$k=${v.toString().take(16)}"
        }
    }.joinToString(", ").ifBlank { "no conditions" }
}

private fun org.json.JSONArray.objListX(): List<org.json.JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

@Composable
fun GlassTextField2(label: String, value: String, onChange: (String) -> Unit) =
    com.sonix21.suinode.ui.glass.GlassTextField(label, value, onChange)

@Composable
fun Modifier.androidScrollR(): Modifier =
    this.verticalScroll(rememberScrollState())
