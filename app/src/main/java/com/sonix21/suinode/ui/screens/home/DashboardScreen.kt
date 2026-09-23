package com.sonix21.suinode.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.APP
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.StatusStore
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.nav.Route
import com.sonix21.suinode.ui.screens.*
import com.sonix21.suinode.ui.shared.LineChart

@Composable
fun DashboardScreen(nav: NavController) {
    val g = LocalGlass.current
    val status by StatusStore.status.collectAsState()
    val history by StatusStore.history.collectAsState()
    val down by StatusStore.downSpeed.collectAsState()
    val up by StatusStore.upSpeed.collectAsState()
    val statusError by StatusStore.error.collectAsState()
    val session = useSession()
    val data by session.data.collectAsState()
    val runner = rememberRunner()
    var showTiles by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    var restart by remember { mutableStateOf(false) }
    var tiles by remember { mutableStateOf(APP.prefs.defaultTiles) }
    val sbd = status?.optJSONObject("sbd")
    val running = sbd?.optBoolean("running") == true
    val maintenance = sbd?.opt("maintenance") as? Boolean ?: data.maintenance
    val stats = sbd?.optJSONObject("stats")
    val sys = status?.optJSONObject("sys")
    val cpu = (status?.opt("cpu") as? Number)?.toFloat()
    val mem = status?.optJSONObject("mem")

    PageScaffold("Overview", nav, subtitle = "Your network, at a glance",
        busy = runner.busy,
        actions = { IconGhostButton(Icons.Rounded.Tune, { showTiles = true }, contentDesc = "Customize dashboard") },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (statusError != null) GlassCard(Modifier.fillMaxWidth()) {
                Text("Status unavailable", color = g.err, fontWeight = FontWeight.SemiBold)
                Text(statusError.orEmpty(), color = g.textFaint, fontSize = 12.sp)
            }

            Box(Modifier.fillMaxWidth().glassSurface(32.dp).background(g.teal.copy(alpha = 0.035f))) {
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width * 0.98f, size.height * 0.30f)
                    for (i in 1..4) drawCircle(g.teal.copy(alpha = 0.05f), size.width * i * 0.13f, center, style = Stroke(1.dp.toPx()))
                    drawCircle(g.teal.copy(alpha = 0.45f), 4.dp.toPx(), Offset(size.width * 0.83f, size.height * 0.2f))
                }
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatusDot(if (statusError != null) g.warn else if (running) g.teal else g.textFaint)
                        Text(if (statusError != null) "STATUS STALE" else if (maintenance) "MAINTENANCE" else if (running) "SYSTEM ONLINE" else "SYSTEM STATUS",
                            color = g.teal, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.6.sp)
                    }
                    Spacer(Modifier.height(15.dp))
                    Text(if (maintenance) "Paused for\nmaintenance." else if (running) "Connected.\nIn control." else if (sbd == null) "Connecting\nthe dots." else "Core is\nnot running.",
                        color = g.text, fontSize = 31.sp, lineHeight = 35.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    Text(if (running) "Uptime  " + Fmt.duration(stats?.optLongOr("Uptime") ?: 0) else "Live status from your selected panel",
                        color = g.textFaint, fontSize = 11.sp)
                    HorizontalDivider(Modifier.padding(vertical = 18.dp), color = g.strokeLo)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CountMetric(data.clients.size, "Clients", Modifier.weight(1f)) { nav.tab(Route.Clients) }
                        CountMetric(data.inbounds.size, "Inbounds", Modifier.weight(1f)) { nav.tab(Route.Inbounds) }
                        CountMetric(data.onlines.user.size, "Online", Modifier.weight(1f)) { nav.tab(Route.Clients) }
                    }
                }
            }

            GlassCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
                SectionHeader("Traffic now") { Text("SERVER I/O", color = g.textFaint, fontFamily = FontFamily.Monospace, fontSize = 9.sp) }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SpeedMetric("Download", down, Icons.Rounded.SouthWest, g.teal, Modifier.weight(1f))
                    SpeedMetric("Upload", up, Icons.Rounded.NorthEast, g.blue, Modifier.weight(1f))
                }
                if ("h-net" in tiles) {
                    Spacer(Modifier.height(12.dp))
                    LineChart(listOf((history["h-net-down"] ?: emptyList()) to g.teal,
                        (history["h-net-up"] ?: emptyList()) to g.blue), Modifier.fillMaxWidth().height(66.dp))
                }
            }

            if ("g-cpu" in tiles || "g-mem" in tiles) {
                SectionHeader("Resources")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if ("g-cpu" in tiles) ResourceTile("Processor", cpu, cpu?.let { "%.1f%%".format(it) } ?: "—",
                        "CPU utilization", Modifier.weight(1f))
                    if ("g-mem" in tiles) ResourceTile("Memory", ratio(mem), mem?.let { Fmt.size(it.optLongOr("current"), 1) } ?: "—",
                        mem?.let { "of " + Fmt.size(it.optLongOr("total"), 0) } ?: "Waiting for status", Modifier.weight(1f))
                }
            }
            if ("g-dsk" in tiles || "g-swp" in tiles) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if ("g-dsk" in tiles) {
                    val d = status?.optJSONObject("dsk")
                    ResourceTile("Storage", ratio(d), d?.let { Fmt.size(it.optLongOr("current"), 1) } ?: "—",
                        d?.let { "of " + Fmt.size(it.optLongOr("total"), 0) } ?: "Waiting for status", Modifier.weight(1f))
                }
                if ("g-swp" in tiles) {
                    val d = status?.optJSONObject("swp")
                    ResourceTile("Swap", ratio(d), d?.let { Fmt.size(it.optLongOr("current"), 1) } ?: "—",
                        d?.let { "of " + Fmt.size(it.optLongOr("total"), 0) } ?: "Waiting for status", Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.AutoMirrored.Filled.ListAlt, "Logs", Modifier.weight(1f)) { nav.push(Route.Logs) }
                QuickAction(Icons.Rounded.Backup, "Backup", Modifier.weight(1f)) { showBackup = true }
                QuickAction(Icons.Rounded.DonutLarge, "Usage", Modifier.weight(1f)) { showUsage = true }
            }

            if ("i-sbd" in tiles) GlassCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, null, tint = g.blue)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("sing-box", color = g.text, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(if (maintenance) "Stopped for maintenance" else if (running) "Core is running" else if (sbd == null) "Waiting for status" else "Core is stopped", color = g.textFaint, fontSize = 11.sp)
                    }
                    IconGhostButton(Icons.Rounded.RestartAlt, { restart = true }, tint = g.textFaint, contentDesc = "Restart core")
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = g.strokeLo)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(Fmt.size(stats?.optLongOr("Alloc") ?: 0) + " memory", color = g.textDim, fontSize = 11.sp)
                    Text("${stats?.optLongOr("NumGoroutine") ?: 0} threads", color = g.textDim, fontSize = 11.sp)
                }
            }
            if ("h-cpu" in tiles) ChartCard("Processor history", listOf((history["h-cpu"] ?: emptyList()) to g.teal), 100f)
            if ("h-mem" in tiles) ChartCard("Memory history", listOf((history["h-mem"] ?: emptyList()) to g.blue), 100f)
            if ("h-dio" in tiles) ChartCard("Disk I/O · MB/s", listOf(
                (history["h-dio-read"] ?: emptyList()) to g.teal, (history["h-dio-write"] ?: emptyList()) to g.blue))
            if ("i-sys" in tiles && sys != null) GlassCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
                SectionHeader("System details")
                Spacer(Modifier.height(12.dp))
                InfoRow("Host", sys.optStringOrNull("hostName") ?: "—")
                InfoRow("CPU", "${sys.optIntOr("cpuCount")} cores · ${sys.optStringOrNull("cpuType") ?: "—"}")
                InfoRow("S-UI", sys.optStringOrNull("appVersion") ?: "—")
                for (key in listOf("ipv4", "ipv6")) sys.optJSONArray(key)?.let { a ->
                    if (a.length() > 0) InfoRow(key.uppercase(), (0 until a.length()).joinToString(", ") { a.optString(it) })
                }
                InfoRow("Boot time", Fmt.dateTime(sys.optLongOr("bootTime")))
            }
            Text("S-UI NODE  /  SONIX", color = g.textFaint.copy(alpha = 0.65f), fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp))
        }
    }
    if (restart) ConfirmDialog("Restart core", "Active connections may be interrupted. Restart sing-box on this panel?",
        confirmText = "Restart", onConfirm = {
            runner.go {
                val env = session.client.postEmpty("restartSb")
                if (!env.success) throw Exception(env.msg)
                ToastBus.show("Core restarted")
            }
        }, onDismiss = { restart = false })
    if (showBackup) BackupRestoreSheet(session, onDismiss = { showBackup = false })
    if (showUsage) UsageCountsSheet(status, onDismiss = { showUsage = false })
    if (showTiles) ModalBottomSheet(onDismissRequest = { showTiles = false }, containerColor = g.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Make it yours", color = g.text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Text("Choose what appears on your overview.", color = g.textFaint, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
            listOf("g-cpu" to "Processor", "g-mem" to "Memory", "g-dsk" to "Storage", "g-swp" to "Swap",
                "h-cpu" to "Processor history", "h-mem" to "Memory history", "h-net" to "Network history",
                "h-dio" to "Disk I/O history", "i-sys" to "System details", "i-sbd" to "Core status").forEach { (key, label) ->
                SwitchRow(label, key in tiles, { checked ->
                    tiles = (if (checked) tiles + key else tiles - key).toMutableSet()
                    APP.prefs.defaultTiles = tiles
                })
            }
        }
    }
}

private fun ratio(o: org.json.JSONObject?): Float? {
    o ?: return null
    val total = o.optLongOr("total")
    return if (total > 0) (o.optLongOr("current") * 100f / total).coerceIn(0f, 100f) else 0f
}

@Composable
private fun CountMetric(count: Int, title: String, modifier: Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    androidx.compose.material3.Surface(onClick = onClick, modifier = modifier, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(UiLocale.digits(count.toString()), color = g.text, fontSize = 27.sp, fontWeight = FontWeight.Medium)
            Text(UiLocale.text(title), color = g.textFaint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SpeedMetric(label: String, speed: Long, icon: ImageVector, color: Color, modifier: Modifier) {
    val g = LocalGlass.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Text(label, color = g.textFaint, fontSize = 11.sp)
        }
        Text(UiLocale.digits(Fmt.speed(speed, 1)), color = g.text, fontSize = 21.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp), maxLines = 1)
    }
}

@Composable
private fun ResourceTile(title: String, percent: Float?, value: String, detail: String, modifier: Modifier) {
    val g = LocalGlass.current
    GlassCard(modifier, contentPadding = 16.dp) {
        Text(title, color = g.textFaint, fontSize = 11.sp)
        Text(UiLocale.digits(value), color = g.text, fontSize = 23.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 14.dp), maxLines = 1)
        Text(UiLocale.digits(detail), color = g.textFaint, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1)
        LinearProgressIndicator(progress = { (percent ?: 0f).coerceIn(0f, 100f) / 100f },
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(4.dp),
            color = if ((percent ?: 0f) > 90) g.err else g.teal, trackColor = g.strokeLo, drawStopIndicator = {})
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    GlassCard(modifier, corner = 22.dp, onClick = onClick, contentPadding = 14.dp) {
        Icon(icon, null, tint = g.blue, modifier = Modifier.size(22.dp).align(Alignment.CenterHorizontally))
        Text(label, color = g.textDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
    }
}

@Composable
private fun ChartCard(title: String, series: List<Pair<List<Float>, Color>>, max: Float? = null) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        SectionHeader(title)
        LineChart(series, Modifier.fillMaxWidth().padding(top = 16.dp).height(100.dp), max)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val g = LocalGlass.current
    Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = g.textFaint, fontSize = 11.sp, modifier = Modifier.width(72.dp))
        Text(value, color = g.textDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}



