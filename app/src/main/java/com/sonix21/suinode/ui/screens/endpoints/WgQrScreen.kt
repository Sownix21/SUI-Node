package com.sonix21.suinode.ui.screens.endpoints

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.J
import com.sonix21.suinode.core.optStringOrNull
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.shared.QrImage
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.rememberRunner
import com.sonix21.suinode.ui.screens.useSession

@Composable
fun WgQrScreen(nav: NavController, endpointId: Long) {
    val g = LocalGlass.current
    val session = useSession()
    val runner = rememberRunner()
    val clipboard = LocalClipboardManager.current

    var ep by remember { mutableStateOf<org.json.JSONObject?>(null) }

    var loadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(endpointId, loadAttempt) {
        runner.go { ep = session.fetchRecord("endpoints", endpointId) }
    }
    if (ep == null) {
        com.sonix21.suinode.ui.screens.RecordLoadingPage("WireGuard share", nav, runner) { loadAttempt++ }
        return
    }
    PageScaffold(title = "WireGuard share", subtitle = ep?.optStringOrNull("tag"), nav = nav, busy = runner.busy) {
        val e = ep ?: return@PageScaffold
        val links = remember(ep) {
            val host = runCatching { java.net.URI(session.panel.url).host }.getOrNull().orEmpty()
            (0 until (e.optJSONArray("peers")?.length() ?: 0)).mapNotNull { i ->
                WireGuardShare.peerConfig(e, i, host)?.let { i to it }
            }
        }
        Column(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (links.isEmpty()) Text(
                    "No shareable peers. A client QR requires its saved peer keypair and the endpoint’s public key; the server private key is never shared.",
                    color = g.textDim,
                )
                for ((i, cfg) in links) {
                    GlassCard(contentPadding = 12.dp, onClick = {
                        clipboard.setText(AnnotatedString(cfg)); ToastBus.show("config copied")
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Peer ${i + 1}", color = g.violet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            IconGhostButton(Icons.Filled.ContentCopy, {
                                clipboard.setText(AnnotatedString(cfg)); ToastBus.show("copied")
                            }, contentDesc = "copy")
                        }
                        QrImage(cfg, 240.dp, Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

private fun org.json.JSONArray.strListX(): List<String> =
    (0 until length()).mapNotNull { runCatching { optString(it) }.getOrNull() }
