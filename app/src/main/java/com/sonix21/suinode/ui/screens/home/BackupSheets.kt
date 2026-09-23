package com.sonix21.suinode.ui.screens.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.Fmt
import com.sonix21.suinode.data.PanelSession
import com.sonix21.suinode.ui.glass.GhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.SwitchRow

@Composable
fun BackupRestoreSheet(session: PanelSession, onDismiss: () -> Unit) {
    val g = LocalGlass.current
    val activity = androidx.activity.compose.LocalActivity.current as com.sonix21.suinode.MainActivity
    val transfers = activity.documents
    var confirmRestore by remember { mutableStateOf(false) }
    var exclStats by remember { mutableStateOf(false) }
    var exclChanges by remember { mutableStateOf(false) }

    if (confirmRestore) ConfirmDialog("Restore database",
        "This replaces panel data and restarts the panel. Only continue with a trusted backup.",
        onConfirm = {
            transfers.restore(session.panel.id)
        }, onDismiss = { confirmRestore = false })

    ModalBottomSheetHost({ if (!transfers.busy) onDismiss() }) {
        Text("Backup & Restore", color = g.textDim, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
        Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchRow("Exclude traffic graphs", exclStats, { exclStats = it }, subtitle = "stats table")
            SwitchRow("Exclude change history", exclChanges, { exclChanges = it }, subtitle = "changes table")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Download backup", enabled = !transfers.busy) {
                    val exclude = listOfNotNull(if (exclChanges) "changes" else null, if (exclStats) "stats" else null).joinToString(",")
                    transfers.backup(session.panel.id, exclude)
                }
                GhostButton("Restore backup", tint = g.orange, enabled = !transfers.busy && !session.panel.readOnly) {
                    confirmRestore = true
                }
            }
            if (transfers.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = g.teal)
                Text("Document operation pending. Unlock the app after choosing a file if prompted.", color = g.textDim, fontSize = 12.sp)
            }
            transfers.status?.let { Text(it, color = g.textDim, fontSize = 12.sp) }
            Text("Database backups contain panel credentials and are not encrypted.", color = g.textDim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(26.dp))
    }

}

@Composable
private fun ModalBottomSheetHost(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalGlass.current.surface,
    ) { content() }
}

@Composable
fun UsageCountsSheet(status: org.json.JSONObject?, onDismiss: () -> Unit) {
    val g = LocalGlass.current
    val db = status?.optJSONObject("db")
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = g.surface,
    ) {
        Text("Usage & Counts", color = g.textDim, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            modifier = Modifier.padding(horizontal = 22.dp))
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            if (db == null) {
                Spacer(Modifier.height(10.dp))
                Text("not available yet", color = g.textFaint, fontSize = 13.sp)
            } else {
                Spacer(Modifier.height(8.dp))
                CountRow("Clients", "${db.optLongOr2("clients")}", g.violet)
                CountRow("Inbounds", "${db.optLongOr2("inbounds")}", g.teal)
                CountRow("Outbounds", "${db.optLongOr2("outbounds")}", g.blue)
                CountRow("Services", "${db.optLongOr2("services")}", g.pink)
                CountRow("Endpoints", "${db.optLongOr2("endpoints")}", g.orange)
                CountRow("Total upload", Fmt.size(db.optLongOr2("clientUp") ?: 0), g.orange)
                CountRow("Total download", Fmt.size(db.optLongOr2("clientDown") ?: 0), g.green)
                CountRow(
                    "Total usage",
                    Fmt.size((db.optLongOr2("clientUp") ?: 0L) + (db.optLongOr2("clientDown") ?: 0L)),
                    g.blue,
                )
            }
        }
    }
}

private fun org.json.JSONObject.optLongOr2(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

@Composable
private fun CountRow(label: String, value: String, color: Color) {
    val g = LocalGlass.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = g.textFaint, fontSize = 13.sp)
        Spacer(Modifier.height(0.dp))
        androidx.compose.foundation.layout.Box(Modifier.weight(1f))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
