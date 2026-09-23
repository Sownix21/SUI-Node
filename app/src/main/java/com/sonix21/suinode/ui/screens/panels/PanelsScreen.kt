package com.sonix21.suinode.ui.screens.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.core.Rand
import com.sonix21.suinode.data.Panel
import com.sonix21.suinode.data.Panels
import com.sonix21.suinode.data.SuiClient
import com.sonix21.suinode.data.Urls
import com.sonix21.suinode.ui.glass.ConfirmDialog
import com.sonix21.suinode.ui.glass.EmptyState
import com.sonix21.suinode.ui.glass.GhostButton
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.GlassTopBar
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.Opt
import com.sonix21.suinode.ui.glass.PrimaryButton
import com.sonix21.suinode.ui.glass.SelectField
import com.sonix21.suinode.ui.glass.StatusDot
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.PageScaffold
import com.sonix21.suinode.ui.screens.rememberRunner

private val palette = listOf(
    Color(0xFF3DE8C8), Color(0xFF5EA2FF), Color(0xFFA78BFA),
    Color(0xFFF472B6), Color(0xFFFFB454), Color(0xFF4ADE80),
)

/** Panel manager: list of panels; doubles as onboarding when [nav] == null. */
@Composable
fun PanelsScreen(nav: NavController?) {
    val g = LocalGlass.current
    val runner = rememberRunner()
    val panels by Panels.list.collectAsState()
    val activeId by Panels.activeId.collectAsState()
    var editingId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Panel?>(null) }
    var search by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var group by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var favoritesOnly by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }

    if (editingId != null || adding) {
        PanelEditorScreen(
            panel = panels.firstOrNull { it.id == editingId },
            onDone = { saved ->
                adding = false; editingId = null
            },
        )
        return
    }

    PageScaffold(
        title = "Panels",
        subtitle = "${panels.size} saved · switch your workspace",
        busy = runner.busy,
        onBack = if (nav != null) ({ nav.pop() }) else null,
    ) {
        Column(Modifier.weight(1f)) {
            if (panels.isNotEmpty()) {
                GlassTextField("Search panels / memos", search, { search = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { groupMenu = true }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = g.textDim)) {
                            Text(UiLocale.text(group.ifBlank { "All groups" }), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            Icon(Icons.Rounded.ExpandMore, contentDescription = "Select panel group", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                            (listOf("") + panels.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()).forEach { option ->
                                DropdownMenuItem(text = { Text(UiLocale.text(option.ifBlank { "All groups" })) }, onClick = { group = option; groupMenu = false })
                            }
                        }
                    }
                    FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text(UiLocale.text("Favorites"), fontSize = 12.sp) }, shape = RoundedCornerShape(14.dp))
                }
            }
            if (panels.isEmpty()) {
                EmptyState(
                    Icons.Filled.Dns, "No panels yet",
                    "Add your first s-ui panel to start managing it from your phone.",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    panels.filter { (group.isEmpty() || it.group == group) && (!favoritesOnly || it.favorite) &&
                        listOf(it.name, it.url, it.group, it.memo).any { text -> text.contains(search, true) } }
                        .sortedWith(compareByDescending<Panel> { it.favorite }.thenBy { it.name.lowercase() }).forEach { p ->
                        val active = p.id == activeId
                        GlassCard(modifier = Modifier.fillMaxWidth(), onClick = {
                            runner.go {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Panels.activate(p.id) }
                                ToastBus.show("Switched to ${p.name}")
                                nav?.pop()
                            }
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val c = palette[Math.floorMod(p.colorIndex, palette.size)]
                                Box(
                                    Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                                        .background(c.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(p.name.take(1).uppercase(), color = c, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (active) StatusDot(g.teal, 7.dp)
                                        Text((if (p.favorite) "★ " else "") + p.name, color = g.text, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (active) Text("active", color = g.teal, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(
                                        p.url.removePrefix("https://").removePrefix("http://"),
                                        color = g.textFaint, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconGhostButton(Icons.Filled.Edit, { editingId = p.id }, contentDesc = "edit")
                                IconGhostButton(Icons.Rounded.DeleteOutline, { deleteTarget = p }, tint = g.err, contentDesc = "delete")
                            }
                            if (p.group.isNotBlank()) Text(p.group, color = g.teal, fontSize = 12.sp)
                            if (p.memo.isNotBlank()) Text(p.memo, color = g.textFaint, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            if (p.readOnly) Text("Read-only safety mode", color = g.teal, fontSize = 12.sp)
                            if(nav!=null) Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                                if(p.offlineOverview) TextButton(onClick = {nav.push(com.sonix21.suinode.ui.nav.Route.OfflineOverview(p.id))}, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text(UiLocale.text("Offline overview"), color = g.textDim, fontSize = 12.sp)
                                }
                                TextButton(onClick = {nav.push(com.sonix21.suinode.ui.nav.Route.PendingSave(p.id))}, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text(UiLocale.text("Pending changes"), color = g.teal, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (panels.isEmpty()) "Add your first panel" else "Add another panel", modifier = Modifier.fillMaxWidth(), onClick = { adding = true })
        if (nav == null) {
            Spacer(Modifier.height(10.dp))
            com.sonix21.suinode.ui.screens.settings.AboutSection()
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Remove panel",
            message = "Delete \"${target.name}\" from this app? The panel itself is not touched.",
            onConfirm = {
                runner.go {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Panels.remove(target.id) }
                    ToastBus.show("Panel removed")
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** Add / edit a panel with mandatory connection test before saving. */
@Composable
fun PanelEditorScreen(panel: Panel?, onDone: (saved: Boolean) -> Unit) {
    val runner = rememberRunner()
    val scope = rememberCoroutineScope()
    val g = LocalGlass.current

    var name by remember { mutableStateOf(panel?.name ?: "") }
    var url by remember { mutableStateOf(panel?.url ?: "") }
    var token by remember { mutableStateOf(panel?.token ?: "") }
    var insecure by remember { mutableStateOf(panel?.allowInsecure ?: false) }
    var group by remember { mutableStateOf(panel?.group.orEmpty()) }
    var favorite by remember { mutableStateOf(panel?.favorite ?: false) }
    var memo by remember { mutableStateOf(panel?.memo.orEmpty()) }
    var readOnly by remember { mutableStateOf(panel?.readOnly ?: false) }
    var offlineOverview by remember { mutableStateOf(panel?.offlineOverview ?: false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    fun buildPanel(id: String): Panel? {
        val normUrl = Urls.normalize(url) ?: return null
        return Panel(
            id = id,
            name = name.trim().ifBlank { Urls.host(normUrl) ?: "Panel" },
            url = normUrl,
            authMode = Panel.AuthMode.TOKEN,
            token = token.trim(),
            allowInsecure = insecure,
            colorIndex = panel?.colorIndex ?: Rand.int(0, palette.size - 1),
            group = group.trim(), favorite = favorite, memo = memo.trim(), readOnly = readOnly,
            offlineOverview = offlineOverview,
        )
    }

    suspend fun test(p: Panel): String {
        val c = SuiClient(p)
        return try {
            val env = c.testConnection()
            if (env.success) "connected ✓" else "failed: ${env.msg.take(120)}"
        } catch (e: Exception) {
            "failed: ${e.message?.take(120)}"
        } finally { c.logout() }
    }

    PageScaffold(
        title = if (panel == null) "Add Panel" else "Edit Panel",
        draftValue = { jo("name" to name, "url" to url, "token" to token, "insecure" to insecure,
            "group" to group, "favorite" to favorite, "memo" to memo, "readOnly" to readOnly, "offlineOverview" to offlineOverview) },
        subtitle = Urls.host(url) ?: "s-ui connection",
        onBack = { onDone(false) },
        busy = runner.busy,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassTextField("Display name", name, { name = it }, hint = "My server")
            GlassTextField("Panel group", group, { group = it.take(80) }, hint = "Provider, region or purpose")
            GlassTextField("Memo / description", memo, { memo = it.take(1000) }, singleLine = false, minLines = 2, maxLines = 5,
                hint = "Provider, billing notes, location…", supporting = "Private local note, encrypted with this panel profile")
            SwitchRow("Favorite panel", favorite, { favorite = it })
            SwitchRow("Read-only safety mode", readOnly, { readOnly = it }, subtitle = "Blocks every APIv2 POST and active latency probes; browsing and database download remain available")
            SwitchRow("Encrypted offline overview",offlineOverview,{offlineOverview=it},subtitle="Optional saved counts and client/inbound summaries; refreshes at most once per minute. Turning off removes its cached snapshot.")
            GlassTextField("Panel URL", url, { url = it }, hint = "https://1.2.3.4:2095/app/",
                supporting = "Full URL including the web base path")

            GlassTextField("API Token", token, { token = it }, obscure = true, supporting = "Create a token in the web panel under Admin → API Tokens")
            buildPanel(panel?.id ?: "connection-preview")?.let {
                com.sonix21.suinode.ui.screens.settings.CertificateReviewCard(it, testResult)
            }

            SwitchRow("Allow self-signed / invalid TLS", insecure, { insecure = it },
                subtitle = "Skips certificate verification for this panel only")

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Test connection", enabled = url.isNotBlank() && !runner.busy) {
                    val p = buildPanel("probe")
                    when {
                        p == null -> testResult = "name + valid URL required"
                        p.authMode == Panel.AuthMode.TOKEN && p.token.isBlank() -> testResult = "fill token first"
                        else -> runner.go { testResult = test(p) }
                    }
                }
                testResult?.let {
                    Text(it, color = if ("✓" in it) g.green else g.err, fontSize = 13.sp, maxLines = 2, modifier = Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(4.dp))
            PrimaryButton("Save panel", loading = runner.busy, onClick = {
                val p = buildPanel(panel?.id ?: Rand.seq(10))
                when {
                    p == null -> ToastBus.show("Name and valid URL are required")
                    p.authMode == Panel.AuthMode.TOKEN && p.token.isBlank() -> ToastBus.show("Token is required")
                    else -> runner.go {
                        if (panel == null || p.url != panel.url || p.token != panel.token || p.allowInsecure != panel.allowInsecure) {
                            val res = test(p)
                            if (!res.contains("✓")) throw Exception(res)
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Panels.upsert(p) }
                        DraftRegistry.saved()
                        onDone(true)
                    }
                }
            })
        }
    }
}
