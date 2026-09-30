package com.sonix21.suinode.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.ui.glass.SectionHeader
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.ui.glass.GhostButton
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.nav.Route
import com.sonix21.suinode.ui.screens.clients.*
import com.sonix21.suinode.ui.screens.endpoints.*
import com.sonix21.suinode.ui.screens.home.DashboardScreen
import com.sonix21.suinode.ui.screens.inbounds.*
import com.sonix21.suinode.ui.screens.outbounds.*
import com.sonix21.suinode.ui.screens.panels.*
import com.sonix21.suinode.ui.screens.routing.*
import com.sonix21.suinode.ui.screens.services.*
import com.sonix21.suinode.ui.screens.tls.*
import com.sonix21.suinode.ui.screens.dns.*
import com.sonix21.suinode.ui.screens.operations.*
import com.sonix21.suinode.ui.screens.settings.*

@Composable
fun screenFor(route: Route, nav: NavController, requestThemeRefresh: () -> Unit) {
    when (route) {
        Route.Panels -> PanelsScreen(nav)
        is Route.PanelEditor -> PanelEditorScreen(
            panel = com.sonix21.suinode.data.Panels.list.value.firstOrNull { it.id == route.id },
            onDone = { nav.pop() },
        )
        Route.Home -> DashboardScreen(nav)
        Route.Clients -> ClientsScreen(nav)
        is Route.ClientEditor -> ClientEditorScreen(nav, route.id)
        Route.ClientBulkAdd -> ClientBulkAddScreen(nav)
        Route.ClientBulkEdit -> ClientBulkEditScreen(nav)
        is Route.ClientQr -> ClientQrScreen(nav, route.id)
        Route.Inbounds -> InboundsScreen(nav)
        is Route.InboundEditor -> InboundEditorScreen(nav, route.id)
        Route.Outbounds -> OutboundsScreen(nav)
        is Route.OutboundEditor -> OutboundEditorScreen(nav, route.id)
        Route.OutboundImport -> OutboundImportScreen(nav)
        Route.Endpoints -> EndpointsScreen(nav)
        is Route.EndpointEditor -> EndpointEditorScreen(nav, route.id)
        is Route.WgQr -> WgQrScreen(nav, route.endpointId)
        Route.Services -> ServicesScreen(nav)
        is Route.ServiceEditor -> ServiceEditorScreen(nav, route.id)
        Route.Tls -> TlsScreen(nav)
        is Route.TlsEditor -> TlsEditorScreen(nav, route.id)
        Route.CertProviders -> CertProvidersScreen(nav)
        is Route.CertProviderEditor -> CertProviderEditorScreen(nav, route.index)
        Route.Routing -> RoutingScreen(nav)
        is Route.RuleEditor -> RuleEditorScreen(nav, route.index)
        is Route.RulesetEditor -> RulesetEditorScreen(nav, route.tagOrNull)
        Route.Dns -> DnsScreen(nav)
        is Route.DnsServerEditor -> DnsServerEditorScreen(nav, route.index)
        is Route.DnsRuleEditor -> DnsRuleEditorScreen(nav, route.index)
        Route.CoreBasics -> CoreBasicsScreen(nav)
        Route.HttpClients -> com.sonix21.suinode.ui.screens.shared.HttpClientsScreen(nav)
        Route.PanelSettings -> PanelSettingsScreen(nav)
        Route.Admins -> AdminsScreen(nav)
        Route.AppSettings -> AppSettingsScreen(nav, requestThemeRefresh)
        Route.Monitoring -> MonitoringScreen(nav)
        is Route.VpsQuota -> com.sonix21.suinode.ui.screens.settings.VpsQuotaScreen(nav, route.panelId)
        is Route.VpsRenewal -> com.sonix21.suinode.ui.screens.settings.VpsRenewalScreen(nav, route.panelId)
        is Route.PendingSave -> com.sonix21.suinode.ui.screens.settings.PendingSaveScreen(nav, route.panelId)
        is Route.OfflineOverview -> com.sonix21.suinode.ui.screens.settings.OfflineOverviewScreen(nav, route.panelId)
        Route.AlertHistory -> AlertHistoryScreen(nav)
        Route.Renewals -> RenewalScreen(nav)
        Route.ConnectionDiagnostics -> ConnectionDiagnosticsScreen(nav)
        Route.Logs -> LogsScreen(nav)
        is Route.Sessions -> SessionsScreen(nav, route.resource, route.tag)
        is Route.TrafficChart -> TrafficChartScreen(nav, route.resource, route.tag, route.title)
        is Route.Backup -> BackupScreen(nav)
        Route.Tools -> ToolsScreen(nav)
    }
}

private data class ToolDestination(val title: String, val detail: String, val icon: ImageVector, val route: Route)

@Composable
private fun ToolsScreen(nav: NavController) {
    val g = LocalGlass.current
    val network = listOf(
        ToolDestination("Outbounds", "How traffic leaves", Icons.AutoMirrored.Rounded.CallMade, Route.Outbounds),
        ToolDestination("Routing", "Rules & destinations", Icons.AutoMirrored.Rounded.AltRoute, Route.Routing),
        ToolDestination("DNS", "Resolvers & DNS rules", Icons.Rounded.Language, Route.Dns),
        ToolDestination("TLS", "Security templates", Icons.Rounded.VerifiedUser, Route.Tls),
        ToolDestination("Endpoints", "VPN interfaces & tunnels", Icons.Rounded.Hub, Route.Endpoints),
        ToolDestination("Services", "Additional listeners", Icons.Rounded.Extension, Route.Services),
    )
    val configuration = listOf(
        ToolDestination("Core settings", "Logging, NTP & experimental", Icons.Rounded.Memory, Route.CoreBasics),
        ToolDestination("HTTP clients", "Shared connection settings", Icons.Rounded.Http, Route.HttpClients),
        ToolDestination("Certificates", "Certificate providers", Icons.Rounded.Key, Route.CertProviders),
        ToolDestination("Panel settings", "Web & subscription settings", Icons.Rounded.Tune, Route.PanelSettings),
    )
    val management = listOf(
        ToolDestination("Live sessions", "Connections by client, inbound or endpoint", Icons.Rounded.NetworkCheck, Route.Sessions()),
        ToolDestination("Connection diagnostics", "Connectivity and certificate identity", Icons.Rounded.Security, Route.ConnectionDiagnostics),
        ToolDestination("Renewals", "Expiry, quota and safe renewal previews", Icons.Rounded.Event, Route.Renewals),
        ToolDestination("Monitoring & alerts", "Read-only checks and alert history", Icons.Rounded.Notifications, Route.Monitoring),
        ToolDestination("Activity & admins", "Administrators and change history", Icons.Rounded.History, Route.Admins),
        ToolDestination("Logs", "Inspect panel and core events", Icons.AutoMirrored.Rounded.Subject, Route.Logs),
        ToolDestination("Backup & restore", "Database exports and imports", Icons.Rounded.Backup, Route.Backup(false)),
        ToolDestination("App settings", "Appearance and device preferences", Icons.Rounded.Settings, Route.AppSettings),
    )
    PageScaffold("Tools", nav, subtitle = "A place for every control") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader("Network")
            network.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { item ->
                        GlassCard(Modifier.weight(1f), onClick = { nav.push(item.route) }, contentPadding = 18.dp) {
                            Icon(item.icon, null, tint = g.teal, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(20.dp))
                            Text(item.title, color = g.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(item.detail, color = g.textFaint, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            SectionHeader("Configuration", Modifier.padding(top = 8.dp))
            ToolGroup(configuration, nav)
            SectionHeader("Management", Modifier.padding(top = 8.dp))
            ToolGroup(management, nav)
            AboutSection()
        }
    }
}

@Composable
private fun ToolGroup(items: List<ToolDestination>, nav: NavController) {
    val g = LocalGlass.current
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 6.dp) {
        items.forEachIndexed { index, item ->
            androidx.compose.material3.Surface(onClick = { nav.push(item.route) },
                color = androidx.compose.ui.graphics.Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(item.icon, null, tint = g.blue, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = g.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(item.detail, color = g.textFaint, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = g.textFaint, modifier = Modifier.size(16.dp))
                }
            }
            if (index < items.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 50.dp, end = 16.dp), color = g.strokeLo)
        }
    }
}
