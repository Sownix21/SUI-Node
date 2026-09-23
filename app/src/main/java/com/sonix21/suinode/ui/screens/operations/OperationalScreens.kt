package com.sonix21.suinode.ui.screens.operations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.Panel
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.*
import com.sonix21.suinode.ui.screens.home.BackupRestoreSheet
import com.sonix21.suinode.ui.shared.LineChart
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun LogsScreen(nav: NavController) {
    val session = useSession()
    var level by remember { mutableStateOf("info") }
    var count by remember { mutableStateOf(30L) }
    val request = rememberReadRequest(session, level, count) {
        val e = session.client.get("logs", mapOf("c" to count.toString(), "l" to level))
        if (!e.success) throw Exception(e.msg)
        e.objStrList()
    }
    val lines = request.value.orEmpty()
    PageScaffold("Logs", nav, subtitle = "${lines.size} lines", busy = request.busy && request.value != null, actions = { IconGhostButton(Icons.Filled.Refresh, { request.refresh() }, contentDesc = "Refresh") }) {
        if (request.value == null) {
            RequestState(if (request.error == null) "Loading details" else "Details unavailable",
                "Fetching the latest information from your panel.", error = request.error, onRetry = { request.refresh() })
            return@PageScaffold
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReadError(request)
            GlassCard(contentPadding = 12.dp) { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SelectField("Level", level, listOf("debug","info","warning","err").map { Opt(it.uppercase(),it) }, { level = it ?: "info" }, Modifier.weight(1f), clearable = false)
                SelectField("Count", count, listOf(10L,20L,30L,50L,100L).map { Opt(it.toString(),it) }, { count = it ?: 30 }, Modifier.weight(1f), clearable = false)
            } }
            GlassCard(contentPadding = 12.dp) { Text(lines.joinToString("\n").ifBlank { "No log lines" }, color = LocalGlass.current.textDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp) }
        }
    }
}

@Composable
fun TrafficChartScreen(nav: NavController, resource: String, tag: String, title: String) {
    val session = useSession()
    var hours by remember { mutableStateOf(24L) }
    val request = rememberReadRequest(session, resource, tag, hours) {
        val e = session.client.get("stats", mapOf("resource" to if (resource == "client") "user" else resource, "tag" to tag, "limit" to hours.toString()))
        if (!e.success) throw Exception(e.msg)
        e.objObj() ?: throw java.io.IOException("The panel returned invalid traffic data.")
    }
    val payload = request.value
    val stats = payload?.optJSONObject("stats"); val count = (payload?.optIntOr("numBuckets", 0) ?: 0).coerceIn(0, 360)
    val up = remember(payload) { (0 until count).map { stats?.optJSONArray(it.toString())?.optLong(0)?.toFloat() ?: 0f } }
    val down = remember(payload) { (0 until count).map { stats?.optJSONArray(it.toString())?.optLong(1)?.toFloat() ?: 0f } }
    val g = LocalGlass.current
    PageScaffold(title, nav, subtitle = "$resource · $tag", busy = request.busy && request.value != null, actions = { IconGhostButton(Icons.Filled.Refresh, { request.refresh() }, contentDesc = "Refresh") }) {
        if (request.value == null) {
            RequestState(if (request.error == null) "Loading details" else "Details unavailable",
                "Fetching the latest information from your panel.", error = request.error, onRetry = { request.refresh() })
            return@PageScaffold
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReadError(request)
            SelectField("Range", hours, listOf(1L,6L,12L,24L,72L,168L,720L).map { Opt(if (it < 24) "$it hours" else "${it/24} days", it) }, { hours = it ?: 24 }, clearable = false)
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                SectionHeader("Traffic")
                LineChart(listOf(up to g.orange, down to g.green), Modifier.fillMaxWidth().height(210.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Upload ${Fmt.size((0 until count).sumOf { stats?.optJSONArray(it.toString())?.optLong(0) ?: 0L })}", color = g.orange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Download ${Fmt.size((0 until count).sumOf { stats?.optJSONArray(it.toString())?.optLong(1) ?: 0L })}", color = g.green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun RulesetEditorScreen(nav: NavController, tag: String?) {
    val session = useSession(); val data by session.data.collectAsState(); val runner = rememberRunner()
    val config = remember(session) { data.config.deepCopy() }
    val route = config.optJSONObject("route") ?: JSONObject().also { config.put("route", it) }; val sets = route.optJSONArray("rule_set") ?: JSONArray().also { route.put("rule_set", it) }
    val originalIndex = (0 until sets.length()).firstOrNull { sets.optJSONObject(it)?.optString("tag") == tag } ?: -1
    var obj by remember(tag) { mutableStateOf(if (originalIndex >= 0) sets.getJSONObject(originalIndex).deepCopy() else jo("type" to "local", "tag" to "rs-${Rand.seq(3)}", "format" to "binary")) }
    val j = J(obj); val type = j.str("type", "local")
    PageScaffold(if (tag == null) "New rule set" else "Edit rule set", nav, busy = runner.busy,
        draftValue = { obj },
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("Save rule set") {
            if (j.str("tag").isBlank()) { ToastBus.show("Tag is required"); return@HeaderPrimaryAction }
            if ((0 until sets.length()).any { it != originalIndex && sets.optJSONObject(it)?.optString("tag") == j.str("tag") }) { ToastBus.show("Duplicate rule-set tag"); return@HeaderPrimaryAction }
            val next = JSONArray(); for (i in 0 until sets.length()) next.put(if (i == originalIndex) j.o else sets.opt(i)); if (originalIndex < 0) next.put(j.o); route.put("rule_set", next)
            runner.go { val r = session.save("config", "set", config.deepCopy()); if (r.isFailure) throw Exception(r.exceptionOrNull()?.message); nav.pop() }
        }
        }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SelectField("Type", type, listOf(Opt("Local","local"),Opt("Remote","remote")), clearable = false, onChange = { v -> j["type"] = v ?: "local" })
                GlassTextField("Tag", j.str("tag"), { j.setStr("tag", it, false) })
                SelectField("Format", j.str("format", "binary"), listOf("source","binary").map { Opt(it,it) }, clearable = false, onChange = { j["format"] = it })
                if (type == "local") GlassTextField("Path", j.str("path"), { j.setStr("path", it) }) else {
                    GlassTextField("URL", j.str("url"), { j.setStr("url", it) })
                    com.sonix21.suinode.ui.screens.shared.HttpClientReference(j, session)
                    DurationField("Update interval", j.optStringOrNull("update_interval"), 'd', onChange = { j["update_interval"] = it })
                }
            } }
            AdvancedJsonCard(j.o) { obj = it }
        }
    }
}

@Composable
fun AdminsScreen(nav: NavController) {
    val session = useSession()
    var actor by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableStateOf(50L) }
    val request = rememberReadRequest(session, actor, count) {
        val u = session.client.get("users")
        if (!u.success) throw Exception(u.msg)
        val c = session.client.get("changes", mapOf("a" to actor, "c" to count.toString()))
        if (!c.success) throw Exception(c.msg)
        (u.objArr()?.objList() ?: emptyList()) to (c.objArr()?.objList() ?: emptyList())
    }
    val users = request.value?.first.orEmpty()
    val changes = request.value?.second.orEmpty()
    PageScaffold("Activity", nav, subtitle = "Administrators and change history", busy = request.busy && request.value != null,
        actions = { IconGhostButton(Icons.Filled.Refresh, { request.refresh() }, contentDesc = "Refresh activity") }) {
        if (request.value == null) {
            RequestState(if (request.error == null) "Loading details" else "Details unavailable",
                "Fetching the latest information from your panel.", error = request.error, onRetry = { request.refresh() })
            return@PageScaffold
        }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReadError(request)
            SectionHeader("Administrators")
            if (users.isEmpty()) Text("No administrators returned by this panel.", color = LocalGlass.current.textDim)
            users.forEach { u -> GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Text(u.optString("username"), color = LocalGlass.current.text, fontWeight = FontWeight.Bold)
                Text(u.optString("lastLogin").ifBlank { "Never logged in" }, color = LocalGlass.current.textDim, fontSize = 12.sp)
            } }
            SectionHeader("Recent changes")
            SelectField("Administrator", actor, users.map { Opt(it.optString("username"), it.optString("username")) }, onChange = { actor = it })
            SelectField("Count", count, listOf(20L,50L,100L,200L).map { Opt(it.toString(),it) }, onChange = { count = it ?: 50L }, clearable = false)
            if (changes.isEmpty()) Text("No changes for this selection.", color = LocalGlass.current.textDim)
            changes.forEach { c -> GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Text("${c.optString("actor")} · ${c.optString("action")} ${c.optString("key")}", color = LocalGlass.current.text, fontSize = 13.sp)
                Text(Fmt.dateTime(c.optLongOr("dateTime")), color = LocalGlass.current.textDim, fontSize = 11.sp)
            } }
        }
    }
}

@Composable
fun BackupScreen(nav: NavController) {
    val session = useSession(); var open by remember { mutableStateOf(true) }
    PageScaffold("Backup & restore", nav) { GlassCard { Text("Download or restore the panel database. Backups contain credentials; store them securely.", color = LocalGlass.current.textDim); Spacer(Modifier.height(10.dp)); PrimaryButton("Open backup tools") { open = true } } }
    if (open) BackupRestoreSheet(session) { open = false; nav.pop() }
}
