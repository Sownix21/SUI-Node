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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.Fmt
import com.sonix21.suinode.data.PanelData
import com.sonix21.suinode.ui.glass.ConfirmDialog
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GhostButton
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.StatusDot
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.nav.Route
import com.sonix21.suinode.ui.screens.SaveBar
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.ItemsScroll
import com.sonix21.suinode.ui.screens.useSession
import org.json.JSONObject

@Composable
fun ClientsScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = com.sonix21.suinode.ui.screens.rememberRunner()

    var filterState by rememberSaveable { mutableStateOf("") }
    var filterGroup by rememberSaveable { mutableStateOf("-") }
    var query by rememberSaveable { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var pendingToggle by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var sortKey by rememberSaveable { mutableStateOf("id") }
    var sortDescending by rememberSaveable { mutableStateOf(false) }
    var showResetTraffic by remember { mutableStateOf(false) }

    val groups = session.groups(data)
    val nowSec = System.currentTimeMillis() / 1000

    val filtered = remember(data.clients, filterState, filterGroup, query, data.onlines, data.inbounds, sortKey, sortDescending) {
        val matching = data.clients.filter { c ->
            val st = when (filterState) {
                "disable" -> !c.optBoolean("enable")
                "expired" -> c.optLongOr("expiry") in 1 until nowSec ||
                    (c.optLongOr("volume") > 0 && c.optLongOr("up") + c.optLongOr("down") >= c.optLongOr("volume"))
                "online" -> c.optString("name") in data.onlines.user
                else -> true
            }
            val gr = filterGroup == "-" || c.optString("group") == filterGroup
            val q = query.isBlank() ||
                c.optString("name").contains(query, true) || c.optString("desc").contains(query, true)
            st && gr && q
        }
        ClientOrdering.sort(matching, sortKey, sortDescending, data.onlines.user.toSet(), data.inbounds.associate { it.optLong("id") to it.optString("tag") })
    }

    PageScaffold(
        title = "Clients",
        subtitle = "${filtered.size} of ${data.clients.size}",
        nav = nav,
        actions = {
            IconGhostButton(Icons.Filled.Refresh, { runner.go { session.load(force = true) } }, contentDesc = "refresh")
            IconGhostButton(Icons.Rounded.FilterList, { showFilters = !showFilters }, contentDesc = "filters")
        },
        busy = runner.busy,
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("New client", type = com.sonix21.suinode.ui.glass.HeaderActionType.New) { nav.push(Route.ClientEditor(0)) }
        },
    ) {
        Column(Modifier.weight(1f)) {
            GlassTextField("Search clients", query, { query = it }, hint = "Name or description",
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                trailing = { Icon(Icons.Filled.Search, null, tint = g.textFaint) })
            if (showFilters) {
                GlassCard(Modifier.padding(bottom = 12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectField("Sort by", sortKey, ClientOrdering.fields.map { Opt(it.value, it.key) },
                            clearable = false, onChange = { sortKey = it ?: "id" })
                        SwitchRow("Descending order", sortDescending, { sortDescending = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SelectField(
                                label = "State",
                                value = filterState.ifBlank { "all" },
                                options = listOf(
                                    com.sonix21.suinode.ui.glass.Opt("All", "all"),
                                    com.sonix21.suinode.ui.glass.Opt("Disabled", "disable"),
                                    com.sonix21.suinode.ui.glass.Opt("Expired", "expired"),
                                    com.sonix21.suinode.ui.glass.Opt("Online", "online"),
                                ),
                                onChange = { filterState = if (it == "all") "" else (it ?: "") },
                                modifier = Modifier.weight(1f),
                            )
                            SelectField(
                                label = "Group",
                                value = filterGroup,
                                options = (listOf("-") + groups).map { com.sonix21.suinode.ui.glass.Opt(if (it == "-") "all" else it, it) },
                                onChange = { filterGroup = it ?: "-" },
                                clearable = false,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton("Bulk add") { nav.push(Route.ClientBulkAdd) }
                            GhostButton("Bulk edit") { nav.push(Route.ClientBulkEdit) }
                            IconGhostButton(Icons.Filled.RestartAlt, { showResetTraffic = true }, tint = g.err, contentDesc = "Reset all traffic")
                        }
                    }
                }
            }

            ItemsScroll(
                items = filtered,
                emptyIcon = Icons.Filled.Person,
                emptyTitle = "No clients",
                emptySubtitle = "Create your first client to hand out connections.",
            ) { c ->
                ClientRow(
                    c = c,
                    data = data,
                    nowSec = nowSec,
                    onToggle = { enable -> pendingToggle = c.optLongOr("id") to enable },
                    onEdit = { nav.push(Route.ClientEditor(c.optLongOr("id"))) },
                    onDelete = { deleteId = c.optLongOr("id") },
                    onQr = { nav.push(Route.ClientQr(c.optLongOr("id"))) },
                    onChart = { nav.push(Route.TrafficChart("user", c.optString("name"), c.optString("name"))) },
                    onSessions = { nav.push(Route.Sessions("user", c.optString("name"))) },
                )
            }
        }
    }

    pendingToggle?.let { (id, enable) ->
        ConfirmDialog(if (enable) "Enable client" else "Disable client",
            if (enable) "Allow this client to connect to the panel's inbounds?" else "This client will no longer be able to connect.",
            confirmText = if (enable) "Enable" else "Disable", danger = !enable,
            onConfirm = { runner.go {
                val full = session.fetchRecord("clients", id)
                full.put("enable", enable)
                session.save("clients", "edit", full).getOrThrow()
                ToastBus.show(if (enable) "Client enabled" else "Client disabled")
            } }, onDismiss = { pendingToggle = null })
    }

    deleteId?.let { id ->
        ConfirmDialog(title = "Delete client", message = "Remove this client from all its inbounds?", onConfirm = {
            runner.go {
                val r = session.save("clients", "del", id)
                ToastBus.show(if (r.isSuccess) "deleted" else r.exceptionOrNull()?.message ?: "failed")
            }
        }, onDismiss = { deleteId = null })
    }

    if (showResetTraffic) {
        ConfirmDialog(title = "Reset all traffic", confirmText = "Reset traffic", message = "Zero up/down for every client and re-enable all of them, including manually disabled clients? Lifetime totals are preserved. s-ui 1.6.4 updates inbound users without restarting the core; older panels may restart it.", onConfirm = {
            runner.go {
                val env = session.client.postEmpty("resetTraffic")
                if (!env.success) throw Exception(env.msg)
                session.load(force = true)
                ToastBus.show("all traffic reset ✓")
            }
        }, onDismiss = { showResetTraffic = false })
    }
}

@Composable
private fun ClientRow(
    c: JSONObject,
    data: PanelData,
    nowSec: Long,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQr: () -> Unit,
    onChart: () -> Unit,
    onSessions: () -> Unit,
) {
    val g = LocalGlass.current
    val online = c.optString("name") in data.onlines.user
    val up = c.optLongOr("up"); val down = c.optLongOr("down")
    val volume = c.optLongOr("volume")
    val usage = up + down
    val usagePct = if (volume > 0) (usage * 100f / volume).coerceIn(0f, 100f) else 0f
    val expired = volume > 0 && usage >= volume || (c.optLongOr("expiry") in 1 until nowSec)

    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(g.teal.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center) {
                Text(c.optString("name").take(1).uppercase(), color = g.teal, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(c.optString("name"), color = g.text, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = listOfNotNull(c.optString("desc").takeIf { it.isNotBlank() },
                    c.optString("group").takeIf { it.isNotBlank() }?.let { "#$it" }).joinToString(" · ")
                if (detail.isNotEmpty()) Text(detail, color = g.textFaint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (online) Text("Online", color = g.teal, fontSize = 10.sp)
                else if (expired) Text("Quota or time expired", color = g.warn, fontSize = 10.sp)
            }
            Switch(c.optBoolean("enable"), onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = "Enable " + c.optString("name") })
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Fmt.size(usage) + if (volume > 0) " / " + Fmt.size(volume) else " / Unlimited",
                color = g.textDim, fontSize = 11.sp)
            Text(if (c.optLongOr("expiry") == 0L) "No expiry" else Fmt.remainedDays(c.optLongOr("expiry"), nowSec),
                color = if (c.optLongOr("expiry") in 1 until nowSec) g.err else g.textFaint, fontSize = 10.sp)
        }
        if (volume > 0) LinearProgressIndicator(progress = { usagePct / 100f },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(3.dp),
            color = if (usagePct > 90) g.err else g.teal, trackColor = g.strokeLo, drawStopIndicator = {})
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            com.sonix21.suinode.ui.screens.operations.SessionShortcut(onSessions)
            IconGhostButton(Icons.AutoMirrored.Filled.ShowChart, onChart, tint = g.textFaint, contentDesc = "Traffic")
            IconGhostButton(Icons.Filled.QrCode2, onQr, tint = g.textFaint, contentDesc = "QR code")
            IconGhostButton(Icons.Rounded.Edit, onEdit, tint = g.teal, contentDesc = "Edit client")
            IconGhostButton(Icons.Rounded.DeleteOutline, onDelete, tint = g.err, contentDesc = "Delete client")
        }
    }
}
