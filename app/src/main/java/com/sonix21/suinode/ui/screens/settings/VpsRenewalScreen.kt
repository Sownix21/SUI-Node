package com.sonix21.suinode.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VpsRenewalScreen(nav: NavController, panelId: String) {
    val context = LocalContext.current
    val store = remember { PanelStore(context.applicationContext) }
    val runner = rememberRunner()
    val zones = remember { java.time.ZoneId.getAvailableZoneIds().sorted().map { Opt(it, it) } }
    var config by remember { mutableStateOf<VpsRenewal?>(null) }
    var saved by remember { mutableStateOf<VpsRenewal?>(null) }
    var renew by remember { mutableStateOf(false) }
    fun load() = runner.go { config = withContext(Dispatchers.IO) { store.vpsRenewal(panelId) }; saved = config }
    LaunchedEffect(panelId) { load() }
    val c = config
    if (c == null) { RecordLoadingPage("VPS renewal reminder", nav, runner, ::load); return }
    if (renew) ConfirmDialog("Mark VPS renewed?", "Advance this local reminder by one billing period? This records no payment and makes no provider or panel changes. Check the new date before saving.",
        onConfirm = { runCatching { c.next() }.onSuccess { config = it }.onFailure { ToastBus.show("Enter a valid due date first") } }, onDismiss = { renew = false })
    PageScaffold("VPS renewal reminder", nav, subtitle = Panels.list.collectAsState().value.firstOrNull { it.id == panelId }?.name,
        busy = runner.busy, draftValue = { config?.toJson() }, primaryAction = { HeaderPrimaryAction("Save local reminder") { runner.go {
            val next = if (c.enabled) c else requireNotNull(saved).copy(enabled = false)
            next.validate()
            withContext(Dispatchers.IO) { store.saveVpsRenewal(panelId,next) }
            DraftRegistry.saved(); ToastBus.show("Local reminder saved; background reminders use Monitoring & alerts settings"); nav.pop()
        } } }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            runner.error?.let { Text(it, color = LocalGlass.current.err) }
            SwitchRow("VPS renewal reminders", c.enabled, { config = c.copy(enabled=it) })
            if (c.enabled) {
                GlassTextField("Next renewal (yyyy-MM-dd)", c.due, { config = c.copy(due=UnlockPolicy.normalizePin(it),anchorDay=0) })
                Text("Use a Gregorian date. The reminder follows the selected billing timezone.", color = LocalGlass.current.textFaint)
                SelectField("Billing period", c.schedule, listOf(Opt("Monthly","monthly"), Opt("Quarterly","quarterly"), Opt("Yearly","yearly"), Opt("Custom days","custom")),
                    clearable=false, onChange={ config=c.copy(schedule=it ?: c.schedule) })
                if(c.schedule=="custom") NumberField("Custom billing period",c.customDays.toLong(),suffix="days",onChange={config=c.copy(customDays=(it?:30).coerceIn(1,3650).toInt())})
                GlassTextField("Price per renewal (optional)",c.price,{config=c.copy(price=UnlockPolicy.normalizePin(it).replace('٫', '.').take(30))})
                GlassTextField("Currency",c.currency,{config=c.copy(currency=it.take(12))})
                NumberField("Remind before renewal",c.warningDays.toLong(),suffix="days",onChange={config=c.copy(warningDays=(it?:7).coerceIn(0,90).toInt())})
                SelectField("Billing timezone",c.zone,zones,clearable=false,onChange={config=c.copy(zone=it?:c.zone)})
                GhostButton("Mark renewed · advance draft date") { renew=true }
            }
            Text("Optional, local and encrypted. Enable background Monitoring & alerts and include this panel to receive reminders. Quiet hours, repeat settings and Android/OEM delays apply. Dates never advance automatically: mark renewed or edit the next date after paying your provider. Price is a note, not a payment instruction.",color=LocalGlass.current.textFaint)
        }
    }
}
