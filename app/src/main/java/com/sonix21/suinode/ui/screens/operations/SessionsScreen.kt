package com.sonix21.suinode.ui.screens.operations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.*
import kotlinx.coroutines.delay

@Composable
fun SessionShortcut(onClick: () -> Unit) {
    IconGhostButton(Icons.Rounded.NetworkCheck, onClick, contentDesc = "Live sessions")
}

@Composable
fun SessionsScreen(nav: NavController, initialResource: String, initialTag: String?) {
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()
    var resource by rememberSaveable { mutableStateOf(initialResource) }
    var tag by rememberSaveable { mutableStateOf(initialTag) }
    var auto by rememberSaveable { mutableStateOf(false) }
    var closingUser by remember { mutableStateOf<String?>(null) }
    val request = rememberReadRequest(session, resource, tag) { session.client.sessions(resource, tag) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(auto, lifecycle, request) {
        if (auto) lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(5_000); if (!request.busy && !runner.busy) request.refresh() }
        }
    }
    val tags = when (resource) {
        "inbound" -> session.inboundTags(data)
        "outbound" -> session.outboundTags(data)
        "endpoint" -> data.endpoints.map { it.optString("tag") }
        else -> session.clientNames(data)
    }.filter(String::isNotBlank).distinct()
    PageScaffold("Live sessions", nav, subtitle = "APIv2 · s-ui 1.6.3+",
        actions = { IconGhostButton(Icons.Filled.Refresh, { request.refresh() }, contentDesc = "Refresh sessions") }) {
        GlassCard {
            SelectField("Resource", resource, listOf("user", "inbound", "outbound", "endpoint").map { Opt(if (it == "user") "Client" else it.replaceFirstChar(Char::uppercase), it) },
                clearable = false, onChange = { resource = it ?: "user"; tag = null })
            SelectField("Filter (empty shows all)", tag, (tags + listOfNotNull(tag)).distinct().map { Opt(it, it) }, onChange = { tag = it })
            SwitchRow("Refresh every 5 seconds", auto, { auto = it })
        }
        runner.error?.let { Text(it, color = LocalGlass.current.err) }
        if (request.value == null) {
            RequestState("Loading live sessions", "Reading active connections. Older panels may not support this action.",
                error = request.error, onRetry = { request.refresh() })
        } else {
            ReadError(request)
            val rows = request.value.orEmpty()
            if (resource == "user" && !tag.isNullOrBlank()) GhostButton("Disconnect this client's sessions",
                enabled = !session.panel.readOnly && !runner.busy && !request.busy && request.error == null && rows.isNotEmpty(), tint = LocalGlass.current.err) {
                closingUser = tag
            }
            Text("${rows.size} active connections", color = LocalGlass.current.textDim)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(rows) { row -> GlassCard {
                    Text(row.optString("domain").ifBlank { row.optString("destination") }, color = LocalGlass.current.text)
                    Text("${row.optString("source")} → ${row.optString("destination")}", color = LocalGlass.current.textDim, fontSize = 12.sp)
                    Text("Client: ${row.optString("user", "—")} · ${row.optString("network")}", color = LocalGlass.current.textDim)
                    Text("${row.optString("inbound", "—")} → ${row.optString("outbound", "—")}", color = LocalGlass.current.textDim)
                    if (row.has("rule")) Text("Rule: ${row.optString("rule")}", color = LocalGlass.current.textFaint)
                    Text("↑ ${Fmt.size(row.optLong("up"))}  ↓ ${Fmt.size(row.optLong("down"))}", color = LocalGlass.current.teal)
                    Text("Started ${Fmt.dateTime(row.optLong("createdAt"))}", color = LocalGlass.current.textFaint, fontSize = 11.sp)
                } }
            }
        }
    }
    closingUser?.let { user -> ConfirmDialog("Disconnect client?",
        "Close all active sessions for $user? The client stays enabled and can reconnect. This immediately affects the panel.",
        danger = true, confirmText = "Disconnect", onDismiss = { closingUser = null }, onConfirm = {
            closingUser = null
            runner.go {
                val response = session.client.closeUserSessions(user)
                check(response.success) { response.msg }
                request.refresh()
                ToastBus.show("Session disconnect requested; refreshing connections")
            }
        }) }
}
