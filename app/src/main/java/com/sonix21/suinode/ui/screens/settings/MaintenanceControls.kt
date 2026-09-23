package com.sonix21.suinode.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.jo
import com.sonix21.suinode.data.PanelSession
import com.sonix21.suinode.data.StatusStore
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.screens.rememberRunner

@Composable
fun MaintenanceControls(session: PanelSession) {
    val runner = rememberRunner()
    var enabled by remember(session) { mutableStateOf<Boolean?>(null) }
    var confirm by remember { mutableStateOf<Boolean?>(null) }
    var statusLoaded by remember(session) { mutableStateOf(false) }
    suspend fun refresh() {
        val e = session.client.get("status", mapOf("r" to "sbd"))
        check(e.success) { e.msg }
        enabled = e.objObj()?.optJSONObject("sbd")?.opt("maintenance") as? Boolean
        statusLoaded = true
        enabled?.let { session.setNewData(jo("maintenance" to it)) }
    }
    LaunchedEffect(session) { runner.go { refresh() } }
    confirm?.let { target -> ConfirmDialog(if (target) "Stop core for maintenance?" else "Start core?",
        if (target) "All active clients will disconnect. The core stays stopped until maintenance is turned off." else "Leave maintenance and start the VPN core with its current configuration?",
        onDismiss = { confirm = null }, onConfirm = { runner.go {
            val e = session.client.postForm("maintenance", mapOf("enable" to target.toString()))
            check(e.success) { e.msg }
            refresh()
            StatusStore.poll(session)
            check(enabled == target) { "Maintenance response could not be verified. Refresh the status before retrying." }
            ToastBus.show(if (target) "Maintenance enabled" else "Maintenance disabled")
        } }) }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Core maintenance")
            runner.error?.let { Text(it, color = LocalGlass.current.err) }
            Text(when {
                !statusLoaded -> if (runner.error != null) "Maintenance status unavailable. Refresh to retry." else "Checking maintenance status…"
                enabled == true -> "Core intentionally stopped; clients cannot connect"
                enabled == false -> "Maintenance is off"
                else -> "Maintenance requires a panel that reports this capability (1.6.1+)"
            }, color = LocalGlass.current.textFaint)
            if (enabled != null) GhostButton(if (enabled == true) "Start core" else "Stop core for maintenance", enabled = !runner.busy && !session.panel.readOnly) { confirm = enabled != true }
            GhostButton("Refresh maintenance status", enabled = !runner.busy) { runner.go { refresh() } }
        }
    }
}
