package com.sonix21.suinode.ui.nav

/** Lightweight route stack navigation. */
sealed interface Route {
    data object Panels : Route                       // panel manager / onboarding
    data class PanelEditor(val id: String?) : Route  // null = add new panel

    data object Home : Route
    data object Logs : Route

    data object Clients : Route
    data class ClientEditor(val id: Long) : Route    // 0 = new
    data object ClientBulkAdd : Route
    data object ClientBulkEdit : Route
    data class ClientQr(val id: Long) : Route
    data class TrafficChart(val resource: String, val tag: String, val title: String) : Route

    data object Inbounds : Route
    data class InboundEditor(val id: Long) : Route   // 0 = new

    data object Outbounds : Route
    data class OutboundEditor(val id: Int) : Route   // -1 = new
    data object OutboundImport : Route

    data object Endpoints : Route
    data class EndpointEditor(val id: Long) : Route  // 0 = new
    data class WgQr(val endpointId: Long) : Route

    data object Services : Route
    data class ServiceEditor(val id: Long) : Route   // 0 = new

    data object Tls : Route
    data class TlsEditor(val id: Long) : Route       // 0 = new
    data object CertProviders : Route                // base config certificate_providers
    data class CertProviderEditor(val index: Int) : Route  // -1 = new

    data object Routing : Route                      // rules + rulesets
    data class RuleEditor(val index: Int) : Route    // -1 new rule
    data class RulesetEditor(val tagOrNull: String?) : Route

    data object Dns : Route
    data class DnsServerEditor(val index: Int) : Route
    data class DnsRuleEditor(val index: Int) : Route

    data object CoreBasics : Route                   // log/ntp/experimental
    data object HttpClients : Route
    data object PanelSettings : Route
    data object Admins : Route
    data object AppSettings : Route
    data object Monitoring : Route
    data class VpsQuota(val panelId: String) : Route
    data class VpsRenewal(val panelId: String) : Route
    data class PendingSave(val panelId: String) : Route
    data class OfflineOverview(val panelId: String) : Route
    data object AlertHistory : Route
    data object Renewals : Route
    data object ConnectionDiagnostics : Route
    data object Tools : Route                        // grid of remaining sections
    data class Backup(val restore: Boolean) : Route  // backup & restore sheet/screen
}

class NavController(val stack: androidx.compose.runtime.snapshots.SnapshotStateList<Route>) {
    val current: Route get() = stack.last()
    var draft: com.sonix21.suinode.core.DraftCheckpoint? = null
    var pendingNavigation = androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null)

    private fun navigate(action: () -> Unit) {
        if (draft?.changed() == true) pendingNavigation.value = action else action()
    }
    fun discardAndContinue() {
        val action = pendingNavigation.value
        pendingNavigation.value = null
        draft?.accept()
        action?.invoke()
    }
    fun push(r: Route) { if (current != r) navigate { stack.add(r) } }
    fun pop() { if (stack.size > 1) navigate { stack.removeAt(stack.size - 1) } }
    fun reset(r: Route) { navigate { stack.clear(); stack.add(r) } }

    /** switch root tab keeping a fresh stack */
    fun tab(r: Route) {
        if (stack.size == 1 && stack.first() == r) return
        reset(r)
    }
}
