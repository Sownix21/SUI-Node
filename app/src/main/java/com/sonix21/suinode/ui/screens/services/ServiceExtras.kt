package com.sonix21.suinode.ui.screens.services

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.PanelSession
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.screens.shared.CsvField
import com.sonix21.suinode.ui.screens.shared.DialSection
import org.json.JSONObject

@Composable
internal fun DerpVerificationUrls(j: J, session: PanelSession) {
    val data by session.data.collectAsState()
    SwitchRow("Verify client URLs", j.has("verify_client_url"), { on ->
        j.o.setOrRemove("verify_client_url", if (on) jarr(listOf(jo("url" to ""))) else null)
    })
    j.arr("verify_client_url")?.let { urls ->
        SectionHeader("Verification URLs") {
            IconGhostButton(Icons.Filled.Add, { urls.put(jo("url" to "")) }, contentDesc = "Add verification URL")
        }
        urls.objList().forEachIndexed { index, value ->
            val entry = J(value)
            GlassCard(contentPadding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTextField("URL", entry.str("url"), { entry.setStr("url", it, false) })
                    DialSection(entry, session.outboundTags(data), dnsTags = session.dnsServerTags(data))
                    IconGhostButton(Icons.Rounded.DeleteOutline, {
                        j.o.put("verify_client_url", jarr(urls.objList().filterIndexed { i, _ -> i != index }))
                    }, tint = LocalGlass.current.err, contentDesc = "Remove verification URL")
                }
            }
        }
    }
}

@Composable
internal fun CoreApiFields(j: J, session: PanelSession) {
    val data by session.data.collectAsState()
    val g = LocalGlass.current
    val dashboard = j.o.opt("dashboard")
    val enabled = ServiceConfig.dashboardEnabled(j.o)
    val download = dashboard is JSONObject && listOf("download_url", "http_client", "update_interval").any(dashboard::has)
    GlassCard(contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Core API service")
            Text("Configures a sing-box service through panel APIv2. S-UI Node still communicates only through APIv2. Keep this service private or protect it with a secret.", color = g.textFaint)
            GlassTextField("Secret", j.str("secret"), { j.setStr("secret", it) }, obscure = true)
            SwitchRow("Allow private network", j.bool("access_control_allow_private_network"), { j.setBool("access_control_allow_private_network", it, true) })
            CsvField("Allowed origins", j.strs("access_control_allow_origin"), { j.setStrs("access_control_allow_origin", it) })
            SwitchRow("Dashboard", enabled, { on -> j.o.setOrRemove("dashboard", if (on) true else null) })
            if (enabled) {
                val path = if (dashboard is String) dashboard else (dashboard as? JSONObject)?.optString("path").orEmpty()
                GlassTextField("Dashboard path", path, { ServiceConfig.setDashboardPath(j.o, it) })
                SwitchRow("Download dashboard", download, { ServiceConfig.setDashboardDownload(j.o, it) })
                if (download) {
                    val d = J(j.o.getJSONObject("dashboard"))
                    GlassTextField("Download URL", d.str("download_url"), { d.setStr("download_url", it, false) })
                    val clients = data.config.optJSONArray("http_clients")?.objList()?.map { it.optString("tag") }.orEmpty()
                    val client = d.o.opt("http_client")
                    SelectField("HTTP client", if (client is String) client else (client as? JSONObject)?.optString("tag"),
                        clients.map { Opt(it, it) }, onChange = { d.setOrRemove("http_client", it) })
                    GlassTextField("Update interval", d.str("update_interval"), { d.setStr("update_interval", it) }, hint = "1d")
                }
            }
        }
    }
}

@Composable
internal fun OomKillerFields(j: J) {
    GlassCard(contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Memory protection")
            Text("Requires OOM-killer support in the panel core. This service has no listener or TLS configuration.", color = LocalGlass.current.textFaint)
            GlassTextField("Memory limit", j.str("memory_limit"), { j.setStr("memory_limit", it) }, hint = "1gb")
            GlassTextField("Safety margin", j.str("safety_margin"), { j.setStr("safety_margin", it) }, hint = "128mb")
            GlassTextField("Minimum interval", j.str("min_interval"), { j.setStr("min_interval", it) }, hint = "10s")
            GlassTextField("Maximum interval", j.str("max_interval"), { j.setStr("max_interval", it) }, hint = "1m")
        }
    }
}
