package com.sonix21.suinode.ui.screens.clients

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.Fmt
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.Rand
import com.sonix21.suinode.core.jarr
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.MultiSelectField
import com.sonix21.suinode.ui.glass.NumberField
import com.sonix21.suinode.ui.glass.PrimaryButton
import com.sonix21.suinode.ui.glass.SectionHeader
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.RecordLoadingPage
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.useSession
import org.json.JSONArray
import org.json.JSONObject

// ------------------------------------------------------------------ config gen

/** Mirrors types/clients.ts randomConfigs(user). */
fun randomConfigs(user: String): JSONObject {
    val mixedPw = Rand.seq(10)
    val uuid = Rand.uuid()
    val ss32 = Rand.ssPassword(32)
    val ss16 = Rand.ssPassword(16)
    return jo(
        "mixed" to jo("username" to user, "password" to mixedPw),
        "socks" to jo("username" to user, "password" to mixedPw),
        "http" to jo("username" to user, "password" to mixedPw),
        "shadowsocks" to jo("name" to user, "password" to ss32),
        "shadowsocks16" to jo("name" to user, "password" to ss16),
        "shadowtls" to jo("name" to user, "password" to ss32),
        "vmess" to jo("name" to user, "uuid" to uuid, "alterId" to 0),
        "vless" to jo("name" to user, "uuid" to uuid, "flow" to "xtls-rprx-vision"),
        "anytls" to jo("name" to user, "password" to mixedPw),
        "trojan" to jo("name" to user, "password" to mixedPw),
        "naive" to jo("username" to user, "password" to mixedPw),
        "hysteria" to jo("name" to user, "auth_str" to mixedPw),
        "tuic" to jo("name" to user, "uuid" to uuid, "password" to mixedPw),
        "hysteria2" to jo("name" to user, "password" to mixedPw),
    )
}

/** Mirrors shuffleConfigs semantics. */
fun shuffleKey(cfg: J, key: String) {
    val entry = cfg.obj(key)?.o ?: return
    when (key) {
        "mixed", "socks", "http", "anytls", "trojan", "naive", "hysteria2" -> entry.put("password", Rand.seq(10))
        "shadowsocks", "shadowtls" -> entry.put("password", Rand.ssPassword(32))
        "shadowsocks16" -> entry.put("password", Rand.ssPassword(16))
        "hysteria" -> entry.put("auth_str", Rand.seq(10))
        "tuic" -> { entry.put("password", Rand.seq(10)); entry.put("uuid", Rand.uuid()) }
        "vmess", "vless" -> entry.put("uuid", Rand.uuid())
    }
}

fun updateConfigs(config: JSONObject, newName: String): JSONObject {
    val out = config.deepCopy()
    for (k in out.keys()) {
        val e = out.optJSONObject(k) ?: continue
        if (e.has("name")) e.put("name", newName)
        else if (e.has("username")) e.put("username", newName)
    }
    return out
}

// ------------------------------------------------------------------ screen

@Composable
fun ClientEditorScreen(nav: NavController, id: Long) {
    val g = LocalGlass.current
    val session = useSession()
    val runner = rememberRunner()

    var loaded by remember { mutableStateOf(id <= 0L) }
    var tab by remember { mutableIntStateOf(0) }
    var obj by remember { mutableStateOf<JSONObject?>(null) }

    var loadAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(id, loadAttempt) {
        if (id > 0) {
            runner.go {
                obj = session.fetchRecord("clients", id)
                loaded = true
            }
        } else {
            val name = Rand.seq(8)
            obj = jo(
                "enable" to true,
                "name" to name,
                "config" to randomConfigs(name),
                "inbounds" to jarr(emptyList<Int>()),
                "links" to JSONArray(),
                "volume" to 0L,
                "expiry" to 0L,
                "up" to 0L,
                "down" to 0L,
                "desc" to "",
                "group" to "",
                "remark" to "",
            )
            loaded = true
        }
    }

    val o = obj
    if (o == null) {
        RecordLoadingPage("Client", nav, runner) { loadAttempt++ }
        return
    }
    val j = J(o)
    var volumeText by remember(o) { mutableStateOf(VolumeInput.format(j.long("volume"))) }

    PageScaffold(
        title = if (id == 0L) "New Client" else "Edit Client",
        draftValue = { jo("client" to o, "volumeInput" to volumeText) },
        subtitle = o.optStringOrNull("name"),
        nav = nav,
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction(
                    if (id == 0L) "Create client" else "Save changes",
                    loading = runner.busy,
                    onClick = {
                        val name = j.str("name")
                        if (name.isBlank()) { ToastBus.show("Name is required"); return@HeaderPrimaryAction }
                        val dup = session.data.value.clients.any { c ->
                            c.optString("name") == name && (if (id == 0L) true else c.optLongOr("id") != id)
                        }
                        if (dup) { ToastBus.show("duplicate client name"); return@HeaderPrimaryAction }

                        val volume = VolumeInput.parse(volumeText)
                        if (volume == null) { ToastBus.show("Enter a valid non-negative volume in GiB"); return@HeaderPrimaryAction }
                        runner.go {
                            j.o.put("volume", volume)
                            j.o.put("config", updateConfigs(j.o.optJSONObject("config") ?: JSONObject(), name))
                            val links = j.o.optJSONArray("links") ?: JSONArray()
                            val keep = JSONArray()
                            for (i in 0 until links.length()) {
                                val l = links.optJSONObject(i) ?: continue
                                if (l.optString("type") != "local" && l.optString("uri").isNotBlank()) keep.put(l)
                            }
                            j.o.put("links", keep)
                            if (j.bool("delayStart") && !j.bool("autoReset")) j.o.put("expiry", 0L)

                            val r = session.save("clients", if (id == 0L) "new" else "edit", j.o)
                            if (r.isSuccess) { ToastBus.show("saved ✓"); nav.pop() }
                            else throw Exception(r.exceptionOrNull()?.message ?: "save failed")
                        }
                    },
                )
        },
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                TabChip("Basics", tab == 0, Modifier.weight(1f)) { tab = 0 }
                TabChip("Config", tab == 1, Modifier.weight(1f)) { tab = 1 }
                TabChip("Links", tab == 2, Modifier.weight(1f)) { tab = 2 }
            }

            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                when (tab) {
                    0 -> BasicsTab(j, id, volumeText) { volumeText = it }
                    1 -> ConfigTab(j)
                    else -> LinksTab(j)
                }
                Spacer(Modifier.height(12.dp))
                com.sonix21.suinode.ui.screens.AdvancedJsonCard(j.o) { obj = it }
            }
        }
    }
}

@Composable
fun TabChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) g.teal.copy(alpha = 0.14f) else g.innerFill,
        contentColor = if (selected) g.teal else g.textFaint) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(UiLocale.text(label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ------------------------------------------------------------------ basics tab

@Composable
private fun BasicsTab(j: J, id: Long, volumeText: String, onVolumeChange: (String) -> Unit) {
    val g = LocalGlass.current
    val session = useSession()
    val data = session.data.collectAsState().value
    val nowSec = System.currentTimeMillis() / 1000

    // expiry text
    var expText by remember(id) { mutableStateOf(if (j.long("expiry") == 0L) "" else Fmt.dateTime(j.long("expiry"), "yyyy-MM-dd HH:mm")) }
    val expiryHidden = j.bool("delayStart") && !j.bool("autoReset")

    GlassCard(contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchRow("Enabled", j.bool("enable"), { j.o.put("enable", it) })

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField("Name", j.str("name"), { v -> j.o.put("name", v.trim()) }, modifier = Modifier.weight(1f))
                GlassTextField(
                    "Group", j.str("group"),
                    { v -> j.setStr("group", v, blankRemoves = false) },
                    modifier = Modifier.weight(1f),
                )
            }
            GlassTextField("Description", j.str("desc"), { v -> j.setStr("desc", v, blankRemoves = false) })
            GlassTextField("Remark (used in share links)", j.str("remark"), { v -> j.setStr("remark", v, blankRemoves = false) })

            GlassTextField(
                label = "Volume limit", value = volumeText, onValueChange = onVolumeChange,
                hint = "Unlimited", supporting = "GiB · leave empty for unlimited",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            )

            if (!expiryHidden) {
                Column {
                    GlassTextField("Expiry (yyyy-MM-dd HH:mm)", expText, { t ->
                        expText = t
                        val unix = Fmt.parseDateTimeToUnix(t.trim())
                        j.o.put("expiry", unix ?: 0L)
                    }, hint = "unlimited")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf("+1d" to (86400L), "+1w" to (604800L), "+1m" to (2592000L), "+1y" to (31536000L)).forEach { (label, secs) ->
                            GhostButton(label) {
                                val base = if (j.long("expiry") > nowSec) j.long("expiry") else nowSec
                                val nv = base + secs
                                j.o.put("expiry", nv)
                                expText = Fmt.dateTime(nv, "yyyy-MM-dd HH:mm")
                            }
                        }
                        GhostButton("∞", tint = g.err) {
                            j.o.put("expiry", 0L); expText = ""
                        }
                    }
                }
            }

            SwitchRow(
                "Delay start", j.bool("delayStart"),
                { on ->
                    if (j.long("up") + j.long("down") > 0) {
                        ToastBus.show("delay start locked after first traffic")
                        return@SwitchRow
                    }
                    j.o.put("delayStart", on)
                    if (on) {
                        j.o.put("resetDays", if (j.int("resetDays") < 1) 1 else j.int("resetDays"))
                        if (!j.bool("autoReset")) j.o.put("expiry", 0L)
                    }
                },
                subtitle = "count limits from first use",
            )
            SwitchRow(
                "Auto reset traffic", j.bool("autoReset"),
                { on ->
                    j.o.put("autoReset", on)
                    if (on) j.o.put("resetDays", if (j.int("resetDays") < 1) 1 else j.int("resetDays"))
                    else j.o.put("nextReset", 0L)
                },
                subtitle = "periodically reset usage every N days",
            )
            if (j.bool("autoReset") || j.bool("delayStart")) {
                NumberField(
                    label = "Reset period",
                    value = if (j.int("resetDays") >= 1) j.int("resetDays").toLong() else 1L,
                    suffix = "days",
                    onChange = { d ->
                        val old = j.int("resetDays")
                        val nd = ((d ?: 0L).toInt()).coerceAtLeast(1)
                        j.o.put("resetDays", nd)
                        val nr = j.long("nextReset")
                        if (nr > 0 && old in 1..364) j.o.put("nextReset", nr + (nd - old) * 86400L)
                    },
                )
            }
        }
    }

    // usage block
    if (id > 0) {
        Spacer(Modifier.height(12.dp))
        val up = j.long("up"); val down = j.long("down"); val volume = j.long("volume")
        val usage = up + down
        val pct = if (volume > 0) (usage * 100f / volume).coerceIn(0f, 100f) else 0f
        GlassCard(contentPadding = 14.dp) {
            SectionHeader("Usage") {
                IconGhostButton(Icons.Filled.Refresh, {
                    j.o.put("totalUp", j.long("totalUp") + up)
                    j.o.put("totalDown", j.long("totalDown") + down)
                    j.o.put("up", 0L); j.o.put("down", 0L)
                    ToastBus.show("usage will reset on save")
                }, tint = g.orange, contentDesc = "reset usage")
            }
            Spacer(Modifier.height(6.dp))
            Text("${Fmt.size(usage)} / ${if (volume == 0L) "∞" else Fmt.size(volume)}", color = g.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).padding(top = 6.dp),
                color = when { pct > 90 -> g.err; pct > 70 -> g.warn; else -> g.teal },
                trackColor = g.strokeLo.copy(alpha = 0.35f),
            )
            if (j.bool("autoReset")) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "next reset: ${Fmt.dateTime(j.long("nextReset"))} · lifetime ${Fmt.size(j.long("totalUp") + up + j.long("totalDown") + down)}",
                    color = g.textFaint, fontSize = 11.5.sp,
                )
            }
        }
    }

    // inbounds selection
    Spacer(Modifier.height(12.dp))
    GlassCard(contentPadding = 14.dp) {
        SectionHeader("Inbounds") {
            IconGhostButton(Icons.Filled.Refresh, {
                val all = data.inbounds.filter { it.optStringOrNull("tag")?.isNotBlank() == true && it.has("users") }
                    .map { it.optLongOr("id") }.sorted()
                j.o.put("inbounds", jarr(all.map { it }))
            }, contentDesc = "select all")
        }
        Spacer(Modifier.height(8.dp))
        val options = data.inbounds
            .filter { it.optStringOrNull("tag")?.isNotBlank() == true && it.has("users") }
            .map { com.sonix21.suinode.ui.glass.Opt(it.optString("tag"), it.optLongOr("id").toString()) }
        MultiSelectField(
            label = "Assigned inbounds",
            selected = j.arr("inbounds")?.longList()?.map { it.toString() }?.toSet() ?: emptySet(),
            options = options,
            onChange = { sel ->
                j.o.put("inbounds", jarr(sel.mapNotNull { it.toLongOrNull() }.sorted()))
            },
        )
    }
}

private fun org.json.JSONArray.longList(): List<Long> =
    (0 until length()).map { when (val v = opt(it)) { is Number -> v.toLong(); is String -> v.toLongOrNull() ?: 0L; else -> 0L } }
