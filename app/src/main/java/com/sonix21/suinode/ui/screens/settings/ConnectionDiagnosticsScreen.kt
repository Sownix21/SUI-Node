package com.sonix21.suinode.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.data.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun ConnectionDiagnosticsScreen(nav: NavController) {
    val panels by Panels.list.collectAsState()
    var selected by remember { mutableStateOf(Panels.activeId.value) }
    var result by remember { mutableStateOf("Run a read-only APIv2 connection check.") }
    var checked by remember { mutableIntStateOf(0) }
    val runner = rememberRunner()
    val panel = panels.firstOrNull { it.id == selected }
    PageScaffold("Connection diagnostics", nav, busy = runner.busy) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectField("Panel", selected, panels.map { Opt(it.name, it.id) }, clearable = false, onChange = { selected = it.orEmpty(); result = "Not checked yet" })
            if (panel != null) {
                Text(if (panel.url.startsWith("https://")) "HTTPS · " + if (panel.allowInsecure) "Certificate/hostname validation DISABLED" else "Certificate and hostname validation enabled"
                    else "WARNING: HTTP sends the API token without transport encryption. Use verified HTTPS before managing a panel over an untrusted network.", color = LocalGlass.current.textFaint)
                PrimaryButton("Run read-only check") { runner.go {
                    val api = SuiClient(panel)
                    try {
                        val response = api.testConnection()
                        result = if (response.success) "Connected. DNS, connection and APIv2 response checks passed." else when {
                            response.msg.contains("token", true) || response.msg.contains("401") || response.msg.contains("403") -> "API access rejected. Check token validity, expiry and permissions."
                            response.msg.contains("JSON", true) || response.msg.contains("load", true) -> "Unexpected response format. Check the web base path and panel/APIv2 version."
                            else -> response.msg
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        result = e.message ?: "Connection check failed"
                    } finally { api.logout(); checked++ }
                } }
                Text(result, color = LocalGlass.current.text)
                CertificateReviewCard(panel, checked)
            }
        }
    }
}

@Composable
fun CertificateReviewCard(panel: Panel, refreshKey: Any?) {
    if (!panel.url.startsWith("https://")) return
    val context = LocalContext.current
    val store = remember { PanelStore(context.applicationContext) }
    val runner = rememberRunner()
    var identity by remember(panel.url) { mutableStateOf(JSONObject()) }
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(panel.url, refreshKey) {
        runner.go { identity = withContext(Dispatchers.IO) { store.inspectIdentity(panel.url) } }
    }
    val reviewed = confirm
    if (reviewed != null) ConfirmDialog("Trust this certificate?",
        "Only continue after verifying this SHA-256 fingerprint with your server administrator through a separate trusted channel. Normal renewal may change it, but interception can too.\n\n$reviewed\n\nThis changes local trust only; it does not alter the panel or bypass normal HTTPS verification.",
        confirmText = "Trust reviewed certificate", danger = true,
        onConfirm = { runner.go {
            withContext(Dispatchers.IO) { store.trustIdentity(panel.url, reviewed) }
            Panels.session?.client?.logout()
            identity = withContext(Dispatchers.IO) { store.inspectIdentity(panel.url) }
            ToastBus.show("Reviewed fingerprint trusted locally. Retry the connection check.")
        } }, onDismiss = { confirm = null })
    GlassCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Panel certificate identity")
        Text("Trusted SHA-256: " + identity.optString("trusted").ifBlank { "Not established yet" }, color = LocalGlass.current.textFaint)
        val pending = identity.optString("pending")
        if (pending.isNotBlank()) {
            Text("Certificate review required. Authenticated requests are blocked.", color = LocalGlass.current.err)
            Text("Presented SHA-256: $pending", color = LocalGlass.current.text)
            GhostButton("Review certificate change", enabled = !runner.busy) { confirm = pending }
        } else Text("The first certificate on normally verified HTTPS establishes the local baseline. Subsequent changes require review. An invalid/expired certificate must still be repaired on the server.", color = LocalGlass.current.textFaint)
    } }
}
