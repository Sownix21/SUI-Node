package com.sonix21.suinode.ui.screens.inbounds

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.ui.glass.ConfirmDialog
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.StatusDot
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.nav.Route
import com.sonix21.suinode.ui.screens.ItemsScroll
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.useSession

@Composable
fun InboundsScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()
    var deleteTag by remember { mutableStateOf<String?>(null) }
    var cloneId by remember { mutableStateOf<Long?>(null) }

    val onlines = data.onlines.inbound.toSet()

    PageScaffold(
        title = "Inbounds",
        subtitle = "${data.inbounds.size} listeners · protocol configuration",
        nav = nav,
        busy = runner.busy,
        actions = {
            IconGhostButton(Icons.Filled.Refresh, { runner.go { session.load(force = true) } }, contentDesc = "refresh")
        },
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("New inbound", type = com.sonix21.suinode.ui.glass.HeaderActionType.New) { nav.push(Route.InboundEditor(0)) }
        },
    ) {
        ItemsScroll(
            items = data.inbounds.sortedBy { it.optLongOr("id") },
            emptyIcon = Icons.Filled.CellTower,
            emptyTitle = "No inbounds",
            emptySubtitle = "Inbounds are the listening doors of your sing-box core.",
        ) { i ->
            val tag = i.optString("tag")
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tag in onlines) StatusDot(g.green, 8.dp)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(tag, modifier = Modifier.weight(1f), color = g.text, fontWeight = FontWeight.Medium, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TypeChip(i.optString("type"))
                            if (i.optLongOr("tls_id") > 0) Text("TLS", color = g.orange, fontSize = 9.5.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                        }
                        val listen = i.optStringOrNull("listen") ?: "::"
                        val port = i.optLongOr("listen_port")
                        val users = i.optJSONArray("users")?.length() ?: -1
                        val sub2 = buildString {
                            append(if (':' in listen) "[$listen]:$port" else "$listen:$port")
                            if (users >= 0) append("  ·  $users user${if (users == 1) "" else "s"}")
                        }
                        Text(sub2, color = g.textFaint, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconGhostButton(Icons.AutoMirrored.Filled.ShowChart, {
                        nav.push(Route.TrafficChart("inbound", tag, tag))
                    }, tint = g.teal, contentDesc = "traffic")
                    IconGhostButton(Icons.Filled.ContentCopy, {
                        cloneId = i.optLongOr("id")
                    }, contentDesc = "clone")
                    IconGhostButton(Icons.Rounded.Edit, { nav.push(Route.InboundEditor(i.optLongOr("id"))) }, contentDesc = "edit")
                    IconGhostButton(Icons.Rounded.DeleteOutline, { deleteTag = tag }, tint = g.err, contentDesc = "delete")
                }
            }
        }
    }

    cloneId?.let { id ->
        ConfirmDialog("Clone inbound", "Create another inbound on this panel with a generated tag and port?",
            confirmText = "Clone", danger = false, onConfirm = {
                runner.go {
                    val full = session.fetchRecord("inbounds", id)
                    val newTag = full.optString("type") + "-" + com.sonix21.suinode.core.Rand.seq(3)
                    full.put("id", 0); full.put("tag", newTag)
                    if (full.optString("type") !in setOf("tun", "cloudflared")) full.put("listen_port", com.sonix21.suinode.core.Rand.int(10000, 60000).toLong())
                    session.save("inbounds", "new", full).getOrThrow()
                    ToastBus.show("Cloned as $newTag")
                }
            }, onDismiss = { cloneId = null })
    }

    deleteTag?.let { tag ->
        ConfirmDialog(title = "Delete inbound", message = "Delete \"$tag\"? Clients using it will be detached.", onConfirm = {
            runner.go {
                val r = session.save("inbounds", "del", tag)
                ToastBus.show(if (r.isSuccess) "deleted" else r.exceptionOrNull()?.message ?: "failed")
            }
        }, onDismiss = { deleteTag = null })
    }
}

@Composable
fun TypeChip(type: String) {
    val g = LocalGlass.current
    androidx.compose.foundation.layout.Box(
        Modifier
            .background(g.teal.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(type, color = g.teal, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}
