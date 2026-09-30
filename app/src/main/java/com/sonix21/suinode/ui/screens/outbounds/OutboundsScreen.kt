package com.sonix21.suinode.ui.screens.outbounds

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
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
import com.sonix21.suinode.ui.screens.inbounds.TypeChip
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.useSession

data class CheckState(val state: Int, val delay: Long? = null, val error: String? = null) // 0 pending 1 ok 2 fail

@Composable
fun OutboundsScreen(nav: NavController) {
    val g = LocalGlass.current
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()
    var deleteTag by remember { mutableStateOf<String?>(null) }
    var checks by remember { mutableStateOf(mapOf<String, CheckState>()) }
    var testingAll by remember { mutableStateOf(false) }

    val onlines = data.onlines.outbound.toSet()

    suspend fun check(tag: String): CheckState = try {
        val env = session.client.get("checkOutbound", mapOf("tag" to tag))
        if (!env.success) CheckState(2, error = env.msg) else {
            val o = env.objObj() ?: throw java.io.IOException("The panel returned an invalid test result.")
            CheckState(
                state = if (o.optBoolean("OK")) 1 else 2,
                delay = o.optLongOrX("Delay").takeIf { o.optBoolean("OK") },
                error = o.optStringOrNull("Error"),
            )
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        CheckState(2, error = e.message ?: "Test failed")
    }

    PageScaffold(
        title = "Outbounds",
        subtitle = "${data.outbounds.size} total",
        nav = nav,
        busy = runner.busy || testingAll,
        actions = {
            IconGhostButton(Icons.Filled.Refresh, { runner.go { session.load(force = true) } }, contentDesc = "refresh")
        },
        primaryAction = {
            com.sonix21.suinode.ui.glass.HeaderPrimaryAction("+ New outbound", type = com.sonix21.suinode.ui.glass.HeaderActionType.New) { nav.push(Route.OutboundEditor(-1)) }
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.sonix21.suinode.ui.glass.GhostButton("Import") { nav.push(Route.OutboundImport) }
                if (data.outbounds.isNotEmpty())
                    com.sonix21.suinode.ui.glass.GhostButton("Test all", tint = g.teal) {
                        val tags = data.outbounds.map { it.optString("tag") }
                        runner.go {
                            testingAll = true
                            checks = emptyMap()
                            try {
                                for (tag in tags) checks = checks + (tag to check(tag))
                            } finally {
                                testingAll = false
                            }
                        }
                    }
        }
        Spacer(Modifier.height(10.dp))
        ItemsScroll(
            items = data.outbounds.sortedBy { it.optLongOrX("id") },
            emptyIcon = Icons.Filled.CloudUpload,
            emptyTitle = "No outbounds",
            emptySubtitle = "Outbounds define where traffic exits.",
        ) { ob ->
            val tag = ob.optString("tag")
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (checks[tag]?.state ?: -1) {
                        1 -> StatusDot(g.green, 8.dp)
                        2 -> StatusDot(g.err, 8.dp)
                        else -> if (tag in onlines) StatusDot(g.blue, 8.dp)
                    }
                    Text(tag, modifier = Modifier.weight(1f), color = g.text, fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TypeChip(ob.optString("type"))
                }
                Spacer(Modifier.height(8.dp))
                val server = ob.optStringOrNull("server")
                Text(if (server.isNullOrBlank()) "No remote server" else
                    "${if (':' in server) "[$server]" else server}:${ob.optLongOrX("server_port")}",
                    color = g.textFaint, fontSize = 12.sp)
                checks[tag]?.delay?.let { Text("${it}ms", color = g.teal, fontSize = 12.sp) }
                checks[tag]?.error?.takeIf { it.isNotBlank() }?.let {
                    Text(it.take(180), color = g.err, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = g.strokeLo)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    com.sonix21.suinode.ui.screens.operations.SessionShortcut { nav.push(Route.Sessions("outbound", tag)) }
                    IconGhostButton(Icons.AutoMirrored.Filled.ShowChart, {
                        nav.push(Route.TrafficChart("outbound", tag, tag))
                    }, tint = g.teal, contentDesc = "Traffic")
                    IconGhostButton(Icons.Filled.Speed, {
                        runner.go { checks = checks + (tag to check(tag)) }
                    }, contentDesc = "Test outbound")
                    IconGhostButton(Icons.Rounded.Edit, { nav.push(Route.OutboundEditor(ob.optLongOrX("id").toInt())) }, contentDesc = "Edit outbound")
                    IconGhostButton(Icons.Rounded.DeleteOutline, { deleteTag = tag }, tint = g.err, contentDesc = "Delete outbound")
                }
            }
        }
    }

    deleteTag?.let { tag ->
        ConfirmDialog(title = "Delete outbound", message = "Delete \"$tag\"?", onConfirm = {
            runner.go {
                val r = session.save("outbounds", "del", tag)
                ToastBus.show(if (r.isSuccess) "deleted" else r.exceptionOrNull()?.message ?: "failed")
            }
        }, onDismiss = { deleteTag = null })
    }
}

fun org.json.JSONObject.optLongOrX(key: String, def: Long = 0): Long =
    if (has(key) && !isNull(key)) when (val v = opt(key)) {
        is Number -> v.toLong(); is String -> v.toLongOrNull() ?: def; else -> def
    } else def
