package com.sonix21.suinode

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.Panels
import com.sonix21.suinode.data.SuiClient
import com.sonix21.suinode.ui.glass.ToastBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Activity-owned contracts survive removal of authenticated Compose screens. */
class DocumentTransfers(private val activity: MainActivity, state: Bundle?) {
    private val queue = DocumentQueue()
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    private var inFlight = false
    private val create = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { selected(it) }
    private val open = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { selected(it) }
    init {
        state?.getString("document.backup.panel")?.let { id ->
            queue.begin(DocumentTicket(id, DocumentKind.BACKUP, state.getString("document.backup.exclude").orEmpty()))
            state.getString("document.backup.uri")?.let { queue.selected(it) }
            busy = true
        }
        if (state?.getBoolean("document.interrupted", false) == true)
            status = "Document transfer interrupted. Check the destination or panel state before trying again."
    }
    fun saveState(out: Bundle) {
        // Restores are never replayed after activity/process recreation.
        queue.ticket?.takeIf { it.kind == DocumentKind.BACKUP }?.let {
            out.putString("document.backup.panel", it.panelId)
            out.putString("document.backup.exclude", it.exclude)
            out.putString("document.backup.uri", queue.uri)
        }
        out.putBoolean("document.interrupted", inFlight || queue.ticket?.kind == DocumentKind.RESTORE)
    }
    fun backup(panelId: String, exclude: String) = launch(DocumentTicket(panelId, DocumentKind.BACKUP, exclude))
    fun restore(panelId: String) = launch(DocumentTicket(panelId, DocumentKind.RESTORE))
    private fun launch(ticket: DocumentTicket) {
        if (busy) return
        if (!activity.access.unlocked || !Panels.ready.value) { status = "Unlock the app first"; ToastBus.show(status!!); return }
        queue.begin(ticket); busy = true; status = "Choose a document location. If the app locks, unlock it to continue."
        try {
            if (ticket.kind == DocumentKind.BACKUP) create.launch("s-ui-backup.db") else open.launch(arrayOf("*/*"))
        } catch (_: Exception) { queue.cancel(); busy = false; status = "No document picker is available"; ToastBus.show(status!!) }
    }
    private fun selected(uri: Uri?) {
        queue.selected(uri?.toString())
        if (uri == null) { busy = false; status = "Document selection cancelled"; return }
        status = "Unlock the app to continue the document transfer."
        resumeAfterUnlock()
    }
    fun resumeAfterUnlock() {
        if (inFlight) return
        val (ticket, target) = queue.claim(activity.access.unlocked, Panels.ready.value) ?: return
        val panel = Panels.list.value.firstOrNull { it.id == ticket.panelId }
        if (panel == null) { busy = false; status = "The selected panel profile is no longer available"; ToastBus.show(status!!); return }
        inFlight = true; busy = true; status = "Transferring database…"
        activity.lifecycleScope.launch {
            val client = SuiClient(panel)
            try {
                val uri = Uri.parse(target)
                if (ticket.kind == DocumentKind.BACKUP) {
                    val count = client.downloadDatabase(mapOf("exclude" to ticket.exclude)) {
                        activity.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open the selected destination")
                    }
                    status = "Backup saved (${Fmt.size(count)})"
                } else {
                    check(!panel.readOnly) { "Read-only safety mode blocks database restore" }
                    val tmp = withContext(Dispatchers.IO) { File.createTempFile("restore_", ".db", activity.cacheDir) }
                    try {
                        withContext(Dispatchers.IO) {
                            activity.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                                ?: error("Cannot open the selected backup")
                            tmp.inputStream().use { input ->
                                val header = ByteArray(16)
                                check(input.read(header) == 16 && header.contentEquals("SQLite format 3\u0000".toByteArray())) { "This is not a SQLite database backup" }
                            }
                        }
                        val response = client.uploadDb(tmp)
                        check(response.success) { response.msg }
                        status = "Database restored — panel restarts itself"
                    } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { tmp.delete() } }
                }
                ToastBus.show(status!!)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                status = "Transfer interrupted. Check the destination or panel state before retrying."
                throw cancelled
            } catch (error: Exception) {
                status = error.message ?: "Document transfer failed"
                ToastBus.show(status!!)
            } finally { client.logout(); busy = false; inFlight = false }
        }
    }
}
