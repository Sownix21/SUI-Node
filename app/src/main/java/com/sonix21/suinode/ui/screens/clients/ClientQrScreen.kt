package com.sonix21.suinode.ui.screens.clients

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import org.json.JSONObject

@Composable
fun ClientQrScreen(nav: NavController, id: Long) {
    val g = LocalGlass.current
    val session = useSession()
    val runner = rememberRunner()
    val clipboard = LocalClipboardManager.current

    var client by remember { mutableStateOf<JSONObject?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    var loadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, loadAttempt) {
        runner.go { client = session.fetchRecord("clients", id) }
    }
    if (client == null) {
        com.sonix21.suinode.ui.screens.RecordLoadingPage("Client share", nav, runner) { loadAttempt++ }
        return
    }

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        ToastBus.show("copied")
    }

    PageScaffold(title = "Share", subtitle = client?.optStringOrNull("name"), nav = nav, busy = runner.busy) {
        val c = client ?: return@PageScaffold
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                TabChip("Subscription", tab == 0, Modifier.weight(1f)) { tab = 0 }
                TabChip("Links", tab == 1, Modifier.weight(1f)) { tab = 1 }
            }

            if (tab == 0) {
                val subUri = session.data.collectAsState().value.subURI.trimEnd('/')
                val name = c.optString("name")
                val subs = listOf(
                    "Subscription" to "$subUri/$name",
                    "JSON format" to "$subUri/$name?format=json",
                    "Clash format" to "$subUri/$name?format=clash",
                )
                Column(
                    Modifier.scrollableColumn(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    subs.forEach { (label, url) ->
                        GlassCard(contentPadding = 12.dp, onClick = { copy(url) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = g.violet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                IconGhostButton(Icons.Filled.ContentCopy, { copy(url) }, contentDesc = "copy")
                            }
                            QrImage(url, 230.dp, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                        }
                    }
                    // sing-box deep link (scan only)
                    val deepLink = "sing-box://import-remote-profile?url=" +
                        java.net.URLEncoder.encode("$subUri/$name?format=json", "UTF-8") + "#" + name
                    GlassCard(contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("SING-BOX import", color = g.teal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                        }
                        QrImage(deepLink, 230.dp, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                        Text("scan only — not a copyable URL", color = g.textFaint, fontSize = 10.5.sp)
                    }
                }
            } else {
                val links = c.optJSONArray("links") ?: org.json.JSONArray()
                if (links.length() == 0) {
                    com.sonix21.suinode.ui.glass.EmptyState(
                        androidx.compose.material.icons.Icons.Filled.LinkOff, "No links",
                        "This client has no share links yet.",
                    )
                }
                Column(Modifier.scrollableColumn(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in 0 until links.length()) {
                        val l = links.optJSONObject(i) ?: continue
                        val uri = l.optString("uri")
                        if (uri.isBlank()) continue
                        val remark = l.optStringOrNull("remark")?.takeIf { it.isNotBlank() }
                        GlassCard(contentPadding = 12.dp, onClick = { copy(uri) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    remark ?: l.optString("type").replaceFirstChar { it.uppercase() },
                                    color = g.violet, fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.weight(1f))
                                IconGhostButton(Icons.Filled.ContentCopy, { copy(uri) }, contentDesc = "copy")
                            }
                            QrImage(uri, 220.dp, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun Modifier.scrollableColumn(): Modifier =
    this.verticalScroll(rememberScrollState())
