package com.sonix21.suinode.ui.screens.clients

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.*
import org.json.JSONObject

private data class RenewalRow(val panel: Panel, val client: JSONObject) {
    val key get() = panel.id + ":" + client.optLong("id")
}
private data class RenewalPreview(val row: RenewalRow, val before: JSONObject, val after: JSONObject)

@Composable
fun RenewalScreen(nav: NavController) {
    val panels by Panels.list.collectAsState()
    val runner = rememberRunner()
    var rows by remember { mutableStateOf<List<RenewalRow>>(emptyList()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var panelFilter by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var urgency by remember { mutableStateOf("attention") }
    var query by remember { mutableStateOf("") }
    var days by remember { mutableLongStateOf(30) }
    var gib by remember { mutableLongStateOf(0) }
    var enable by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<RenewalPreview>?>(null) }
    var confirmation by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    suspend fun record(api: SuiClient, id: Long): JSONObject {
        val response = api.get("clients", mapOf("id" to id.toString()))
        check(response.success) { "Unable to fetch the full client record" }
        return response.objObj()?.optJSONArray("clients")?.objList()?.firstOrNull { it.optLong("id") == id }
            ?: throw IllegalStateException("Client no longer exists. Refresh and build a new preview.")
    }
    fun refresh() = runner.go {
        val loaded = mutableListOf<RenewalRow>()
        val failed = mutableListOf<String>()
        panels.forEach { panel ->
            val api = SuiClient(panel)
            try {
                val clients = api.fullClients()
                loaded += clients.objList().map { RenewalRow(panel, it) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { failed += panel.name }
            finally { api.logout() }
        }
        rows = loaded; selected = emptySet(); preview = null
        result = if (failed.isEmpty()) "Lists refreshed through APIv2" else "Could not load: ${failed.joinToString()}. Unavailable panels are not included."
    }
    LaunchedEffect(Unit) { refresh() }
    val now = System.currentTimeMillis() / 1000
    val visible = rows.filter { row ->
        val c = row.client
        val expiry = c.optLong("expiry").takeUnless { c.optBoolean("delayStart") } ?: 0
        val limit = c.optLong("volume")
        val expired = expiry > 0 && expiry <= now
        val exhausted = limit > 0 && ClientHealth.usage(c) >= limit
        val near = expiry > now && expiry - now <= 72 * 3600 || limit > 0 && ClientHealth.usage(c).toDouble() / limit >= .9
        (panelFilter.isEmpty() || row.panel.id == panelFilter) && (group.isEmpty() || c.optString("group") == group) &&
            c.optString("name").contains(query, true) && when (urgency) {
                "expired" -> expired || exhausted
                "soon" -> near && !expired && !exhausted
                "all" -> true
                else -> near || expired || exhausted
            }
    }.sortedWith(compareBy<RenewalRow> { it.client.optLong("expiry").takeIf { x -> x > 0 } ?: Long.MAX_VALUE }.thenBy { it.client.optString("name") })
    if (confirmation) ConfirmDialog("Apply reviewed renewals", "Update ${preview?.size ?: 0} clients exactly as shown? This changes the selected live panels. No backup or traffic-reset operation is performed.",
        onConfirm = { runner.go {
            val planned = preview.orEmpty()
            check(planned.isNotEmpty())
            var completed = 0
            try {
                for ((panel, changes) in planned.groupBy { it.row.panel }) {
                    val api = SuiClient(panel)
                    try {
                        val payload = changes.map { p ->
                            val current = record(api, p.before.optLong("id"))
                            check(RenewalPlan.sameConfiguration(p.before, current)) { "Client settings changed since preview. Refresh and review again." }
                            listOf("expiry", "volume", "enable").forEach { key -> if (p.after.has(key)) current.put(key, p.after.get(key)) }
                            current
                        }
                        val saved = api.postForm("save", mapOf("object" to "clients", "action" to "editbulk", "data" to jarr(payload).toString()))
                        check(saved.success) { "The panel rejected the renewal. Review its settings before retrying." }
                        for (p in changes) {
                            val verified = record(api, p.before.optLong("id"))
                            check(listOf("expiry", "volume", "enable").all { verified.opt(it) == p.after.opt(it) }) {
                                "Save was accepted, but GET-back verification did not match. Inspect this panel before retrying."
                            }
                            completed++
                        }
                    } finally { api.logout() }
                }
                result = "Verified $completed renewed clients through GET. Refresh the dashboard to see their current limits."
                selected = emptySet()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { result = "$completed clients verified before stopping. ${e.message}" }
            finally { preview = null }
        } }, onDismiss = { confirmation = false })
    PageScaffold("Renewal dashboard", nav, busy = runner.busy,
        draftValue = { jo("selected" to jarr(selected.sorted()), "days" to days, "gib" to gib, "enable" to enable) }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton("Refresh all panels") { refresh() }
            Text(result, color = LocalGlass.current.textFaint)
            runner.error?.let { Text(it, color = LocalGlass.current.err) }
            if (preview == null) {
                SelectField("Panel", panelFilter, listOf(Opt("All panels", "")) + panels.map { Opt(it.name, it.id) }, clearable = false, onChange = { panelFilter = it.orEmpty() })
                SelectField("Group", group, listOf(Opt("All groups", "")) + rows.map { it.client.optString("group") }.filter { it.isNotBlank() }.distinct().map { Opt(it, it) }, clearable = false, onChange = { group = it.orEmpty() })
                SelectField("Urgency", urgency, listOf(Opt("Needs attention", "attention"), Opt("Expired / exhausted", "expired"), Opt("Near limit (72h / 90%)", "soon"), Opt("All clients", "all")), clearable = false, onChange = { urgency = it ?: urgency })
                GlassTextField("Search clients", query, { query = it })
                Text("${visible.size} matches · ${selected.size} selected (maximum 50 per preview)", color = LocalGlass.current.textFaint)
                GhostButton("Select visible (first 50)") { selected = visible.take(50).map { it.key }.toSet() }
                MultiSelectField("Clients to renew", selected, rows.map { Opt("${it.panel.name} · ${it.client.optString("name")}", it.key) },
                    { if (it.size <= 50) selected = it else ToastBus.show("Select at most 50 clients") }, searchable = true)
                GlassCard { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    NumberField("Extend expiry", days, suffix = "days", onChange = { days = (it ?: 0).coerceIn(0, 3650) })
                    NumberField("Add traffic quota", gib, suffix = "GiB", onChange = { gib = (it ?: 0).coerceIn(0, 1_000_000) })
                    SwitchRow("Re-enable selected clients", enable, { enable = it })
                    Text("Expired clients extend from now; active clients extend from their expiry. Unlimited quotas, no-expiry and first-use expiry remain unchanged. Traffic counters are not reset.", color = LocalGlass.current.textFaint)
                    PrimaryButton("Build read-only preview", enabled = selected.isNotEmpty()) { runner.go {
                        val nowPreview = System.currentTimeMillis() / 1000
                        val built = mutableListOf<RenewalPreview>()
                        rows.filter { it.key in selected }.groupBy { it.panel }.forEach { (panel, targets) ->
                            val api = SuiClient(panel)
                            try { targets.forEach { row ->
                                val full = record(api, row.client.optLong("id"))
                                val after = RenewalPlan.apply(full, days, gib, enable, nowPreview)
                                if (!RenewalPlan.sameConfiguration(full, after)) built += RenewalPreview(row, full, after)
                            } } finally { api.logout() }
                        }
                        if (built.isEmpty()) ToastBus.show("The selected settings would not change these clients")
                        else preview = built
                    } }
                } }
                visible.take(200).forEach { row -> GlassCard {
                    Text("${row.panel.name} · ${row.client.optString("name")}", color = LocalGlass.current.text)
                    Text("Expiry: ${expiryText(row.client)} · Quota: ${quotaText(row.client)}", color = LocalGlass.current.textFaint)
                } }
            } else {
                Text("Preview only — nothing has been sent to the panel", color = LocalGlass.current.teal)
                preview.orEmpty().forEach { p -> GlassCard { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${p.row.panel.name} · ${p.before.optString("name")}", color = LocalGlass.current.text)
                    Text("Expiry: ${expiryText(p.before)} → ${expiryText(p.after)}", color = LocalGlass.current.textFaint)
                    Text("Quota: ${quotaText(p.before)} → ${quotaText(p.after)}\nExact bytes: ${p.before.optLong("volume")} → ${p.after.optLong("volume")}", color = LocalGlass.current.textFaint)
                    Text("Enabled: ${p.before.optBoolean("enable")} → ${p.after.optBoolean("enable")}", color = LocalGlass.current.textFaint)
                } } }
                PrimaryButton("Confirm reviewed changes") { confirmation = true }
                GhostButton("Back to selection") { preview = null }
            }
        }
    }
}

private fun expiryText(c: JSONObject): String = when {
    c.optBoolean("delayStart") -> "Starts on first use"
    c.optLong("expiry") <= 0 -> "No expiry"
    else -> java.time.Instant.ofEpochSecond(c.optLong("expiry")).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString()
}
private fun quotaText(c: JSONObject) = if (c.optLong("volume") <= 0) "Unlimited" else Fmt.size(c.optLong("volume"))
