package com.sonix21.suinode.ui.screens.clients

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.Fmt
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.Rand
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.core.longList
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.ui.glass.GhostButton
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.MultiSelectField
import com.sonix21.suinode.ui.glass.NumberField
import com.sonix21.suinode.ui.glass.Opt
import com.sonix21.suinode.ui.glass.PrimaryButton
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.SectionHeader
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.useSession
import org.json.JSONArray

// ------------------------------------------------------------- pattern field

@Composable
private fun PatternField(label: String, tokens: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    GlassCard(modifier, corner = 16.dp, contentPadding = 10.dp) {
        SectionHeader(label) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GhostButton("random") { onChange(tokens + "random") }
                GhostButton("order") { onChange(tokens + "order") }
                IconGhostButton(Icons.Filled.Add, { onChange(tokens + "-") }, contentDesc = "literal")
            }
        }
        Text(
            if (tokens.isEmpty()) "(empty)" else tokens.joinToString(""),
            color = g.text, fontSize = 13.sp,
        )
        Text("random → 8 random chars · order → 1,2,3… · other text is literal", color = g.textFaint, fontSize = 10.5.sp)
    }
}

private fun genByPattern(tokens: List<String>, i: Int): String =
    tokens.joinToString("") { t ->
        when (t) {
            "random" -> Rand.seq(8)
            "order" -> (i + 1).toString()
            else -> t
        }
    }

// ------------------------------------------------------------------ bulk add

@Composable
fun ClientBulkAddScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val runner = rememberRunner()
    val data = session.data.collectAsState().value
    val nowSec = System.currentTimeMillis() / 1000

    var count by remember { mutableStateOf(1L) }
    val nameTokens = remember { mutableStateListOf("order", "-", "random") }
    val descTokens = remember { mutableStateListOf<String>() }
    var group by remember { mutableStateOf("") }
    var volumeGb by remember { mutableStateOf(0L) }
    var expiryText by remember { mutableStateOf("") }
    var delayStart by remember { mutableStateOf(false) }
    var autoReset by remember { mutableStateOf(false) }
    var resetDays by remember { mutableStateOf(1L) }
    var inbounds by remember { mutableStateOf(emptySet<String>()) }

    val inboundOptions = data.inbounds
        .filter { it.optStringOrNull("tag")?.isNotBlank() == true && it.has("users") }
        .map { Opt(it.optString("tag"), it.optLongOr("id").toString()) }

    PageScaffold(title = "Bulk Add Clients", nav = nav, busy = runner.busy,
        draftValue = { jo("count" to count, "names" to jarr(nameTokens.toList()), "descriptions" to jarr(descTokens.toList()),
            "group" to group, "volume" to volumeGb, "expiry" to expiryText, "delay" to delayStart,
            "reset" to autoReset, "days" to resetDays, "inbounds" to jarr(inbounds.sorted())) }, primaryAction = {
            HeaderPrimaryAction("Create $count clients", loading = runner.busy, onClick = {
                val n = count.coerceIn(1, 100).toInt()
                if (nameTokens.none { it == "random" || it == "order" }) {
                    ToastBus.show("pattern needs at least one random/order token"); return@HeaderPrimaryAction
                }
                runner.go {
                    val expiryUnix = Fmt.parseDateTimeToUnix(expiryText.trim()) ?: 0L
                    val clients = JSONArray()
                    val names = mutableListOf<String>()
                    for (i in 0 until n) {
                        val name = genByPattern(nameTokens, i)
                        if (names.contains(name) || data.clients.any { it.optString("name") == name })
                            throw Exception("duplicate name generated: $name")
                        names.add(name)
                        val desc = if (descTokens.isEmpty()) Rand.seq(8) else genByPattern(descTokens, i)
                        val c = jo(
                            "enable" to true,
                            "name" to name,
                            "config" to randomConfigs(name),
                            "inbounds" to jarr(inbounds.mapNotNull { it.toLongOrNull() }.sorted()),
                            "links" to JSONArray(),
                            "volume" to volumeGb * 1073741824L,
                            "expiry" to (if (delayStart && !autoReset) 0L else expiryUnix),
                            "up" to 0L, "down" to 0L,
                            "desc" to desc,
                            "group" to group,
                            "delayStart" to delayStart,
                            "autoReset" to autoReset,
                            "resetDays" to if (delayStart || autoReset) resetDays.toInt().coerceAtLeast(1) else 0,
                        )
                        clients.put(c)
                    }
                    val r = session.save("clients", "addbulk", clients)
                    if (r.isSuccess) { ToastBus.show("$n clients created ✓"); nav.pop() }
                    else throw Exception(r.exceptionOrNull()?.message ?: "failed")
                }
            })
        }) {
        Column(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("How many clients", count.takeIf { it > 0 }, suffix = "clients", onChange = { count = it ?: 1 })
                PatternField("Name pattern", nameTokens, { nameTokens.clear(); nameTokens.addAll(it) })
                PatternField("Description pattern", descTokens.toList(), { descTokens.clear(); descTokens.addAll(it) })

                GlassCard(contentPadding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassTextField("Group", group, { group = it })
                        NumberField("Volume", volumeGb.takeIf { it > 0 }, suffix = "GiB", hint = "unlimited", onChange = { volumeGb = it ?: 0 })
                        GlassTextField("Expiry (yyyy-MM-dd HH:mm)", expiryText, {
                            expiryText = it
                        }, hint = "unlimited")
                        SwitchRow("Delay start", delayStart, {
                            delayStart = it
                            if (it) resetDays = 1
                        })
                        SwitchRow("Auto reset traffic", autoReset, {
                            autoReset = it
                            if (it) resetDays = 1
                        })
                        if (delayStart || autoReset)
                            NumberField("Reset period", resetDays.takeIf { it >= 1 }, suffix = "days", onChange = { resetDays = (it ?: 1).coerceAtLeast(1) })
                        MultiSelectField("Inbounds", inbounds, inboundOptions, { inbounds = it })
                    }
                }

                // preview names
                val n = count.coerceIn(1, 100).toInt()
                Text("Preview:", color = g.textFaint, fontSize = 11.sp)
                Text(
                    (0 until minOf(n, 6)).joinToString(", ") { genByPattern(nameTokens, it) } + if (n > 6) " …" else "",
                    color = g.teal, fontSize = 12.sp,
                )
            }
        }

    }
}

// ----------------------------------------------------------------- bulk edit

@Composable
fun ClientBulkEditScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val runner = rememberRunner()
    val data = session.data.collectAsState().value

    var mode by remember { mutableStateOf("change_limits") }
    var selModel by remember { mutableStateOf("none") }
    var selValues by remember { mutableStateOf(emptySet<String>()) }

    var enableRecompute by remember { mutableStateOf(false) }
    var addVolume by remember { mutableStateOf(0L) }
    var addDays by remember { mutableStateOf(0L) }
    var inbounds by remember { mutableStateOf(emptySet<String>()) }

    val groups = session.groups(data)
    val clientOptions = data.clients.map { Opt(it.optString("name"), it.optLongOr("id").toString()) }
    val inboundOptions = data.inbounds
        .filter { it.optStringOrNull("tag")?.isNotBlank() == true && it.has("users") }
        .map { Opt(it.optString("tag"), it.optLongOr("id").toString()) }
    val nowSec = System.currentTimeMillis() / 1000

    fun targetClients(): List<org.json.JSONObject> = when (selModel) {
        "all" -> data.clients
        "group" -> data.clients.filter { it.optString("group") in selValues }
        "client" -> data.clients.filter { it.optLongOr("id").toString() in selValues }
        else -> emptyList()
    }

    PageScaffold(title = "Bulk Edit Clients", nav = nav, busy = runner.busy,
        draftValue = { jo("mode" to mode, "selection" to selModel, "values" to jarr(selValues.sorted()),
            "enable" to enableRecompute, "volume" to addVolume, "days" to addDays, "inbounds" to jarr(inbounds.sorted())) }, primaryAction = {
            HeaderPrimaryAction(
                when (mode) { "delete_bulk" -> "Delete selected"; else -> "Apply changes" },
                type = if (mode == "delete_bulk") HeaderActionType.Delete else HeaderActionType.Save,
                loading = runner.busy,
                onClick = {
                    val targets = targetClients()
                    if (targets.isEmpty()) { ToastBus.show("no clients selected"); return@HeaderPrimaryAction }
                    runner.go {
                        when (mode) {
                            "delete_bulk" -> {
                                val ids = jarr(targets.map { it.optLongOr("id") })
                                val r = session.save("clients", "delbulk", ids)
                                if (r.isSuccess) { ToastBus.show("deleted ✓"); nav.pop() } else throw Exception(r.exceptionOrNull()?.message)
                            }
                            else -> {
                                val out = JSONArray()
                                targets.forEach { c ->
                                    val copy = session.fetchRecord("clients", c.optLongOr("id"))
                                    when (mode) {
                                        "change_limits" -> {
                                            if (addVolume != 0L && copy.optLongOr("volume") > 0)
                                                copy.put("volume", (copy.optLongOr("volume") + addVolume * 1073741824L).coerceAtLeast(0))
                                            if (addDays != 0L && copy.optLongOr("expiry") > 0)
                                                copy.put("expiry", copy.optLongOr("expiry") + addDays * 86400)
                                            if (enableRecompute) {
                                                val vol = copy.optLongOr("volume")
                                                val exp = copy.optLongOr("expiry")
                                                val used = copy.optLongOr("up") + copy.optLongOr("down")
                                                copy.put("enable", (vol == 0L || used < vol) && (exp == 0L || exp > nowSec))
                                            }
                                        }
                                        "add_inbounds" -> {
                                            val cur = copy.optJSONArray("inbounds")?.longList()?.toMutableSet() ?: mutableSetOf()
                                            inbounds.mapNotNull { it.toLongOrNull() }.forEach { cur.add(it) }
                                            copy.put("inbounds", jarr(cur.sorted()))
                                        }
                                        "remove_inbounds" -> {
                                            val rm = inbounds.mapNotNull { it.toLongOrNull() }.toSet()
                                            val cur = copy.optJSONArray("inbounds")?.longList()?.filterNot { it in rm } ?: emptyList()
                                            copy.put("inbounds", jarr(cur.sorted()))
                                        }
                                    }
                                    out.put(copy)
                                }
                                val r = session.save("clients", "editbulk", out)
                                if (r.isSuccess) { ToastBus.show("updated ✓"); nav.pop() } else throw Exception(r.exceptionOrNull()?.message)
                            }
                        }
                    }
                }
            )
        }) {
        Column(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectField(
                    label = "Action",
                    value = mode,
                    options = listOf(
                        Opt("Change limits", "change_limits"),
                        Opt("Add inbounds", "add_inbounds"),
                        Opt("Remove inbounds", "remove_inbounds"),
                        Opt("Delete selected", "delete_bulk"),
                    ),
                    onChange = { mode = it ?: mode },
                    clearable = false,
                )

                GlassCard(contentPadding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectField(
                            label = "Select clients",
                            value = selModel,
                            options = listOf(
                                Opt("None", "none"), Opt("All clients", "all"),
                                Opt("By group", "group"), Opt("Pick clients", "client"),
                            ),
                            onChange = { selModel = it ?: "none"; selValues = emptySet() },
                            clearable = false,
                        )
                        when (selModel) {
                            "group" -> MultiSelectField("Groups", selValues, groups.map { Opt(it, it) }, { selValues = it })
                            "client" -> MultiSelectField("Clients", selValues, clientOptions, { selValues = it })
                        }
                        val targets = targetClients()
                        Text("${targets.size} client(s) targeted", color = g.violet, fontSize = 12.sp)
                    }
                }

                when (mode) {
                    "change_limits" -> GlassCard(contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SwitchRow("Re-enable depleted clients", enableRecompute, { enableRecompute = it },
                                subtitle = "enable if usage/expiry still valid")
                            NumberField("Add volume", addVolume.takeIf { it != 0L }, allowNegative = true, suffix = "GiB",
                                hint = "0", onChange = { addVolume = it ?: 0 })
                            NumberField("Extend expiry", addDays.takeIf { it != 0L }, allowNegative = true, suffix = "days",
                                hint = "0", onChange = { addDays = it ?: 0 })
                        }
                    }
                    "add_inbounds", "remove_inbounds" -> GlassCard(contentPadding = 14.dp) {
                        MultiSelectField("Inbounds", inbounds, inboundOptions, { inbounds = it })
                    }
                }
            }
        }

    }
}
