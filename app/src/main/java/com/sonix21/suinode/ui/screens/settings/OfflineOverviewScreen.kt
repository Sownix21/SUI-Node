package com.sonix21.suinode.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun OfflineOverviewScreen(nav: NavController,panelId: String){
    val context=LocalContext.current
    val runner=rememberRunner()
    var cache by remember { mutableStateOf<JSONObject?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val panel=Panels.list.collectAsState().value.firstOrNull{it.id==panelId}
    LaunchedEffect(panelId){runner.go{cache=withContext(Dispatchers.IO){PanelStore(context.applicationContext).localRecord("offlineOverviews",panelId)};loaded=true}}
    PageScaffold("Offline overview",nav,subtitle=panel?.name,busy=runner.busy){
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){
            item { Text("Saved snapshot · not live · editing unavailable",color=LocalGlass.current.teal)
                runner.error?.let{Text(it,color=LocalGlass.current.err)} }
            val snapshot=cache
            if(snapshot==null)item{Text(if(loaded)"No saved snapshot. Enable Offline overview in this panel's profile, then connect successfully." else "Opening encrypted snapshot…",color=LocalGlass.current.textFaint)}
            else{
                item{Text("Last synchronized: ${java.time.Instant.ofEpochSecond(snapshot.optLong("time"))}",color=LocalGlass.current.text)
                    Text("Counts and status describe that moment, not the current server. Only bounded summaries are stored, never client passwords/configuration. Reconnect before editing.",color=LocalGlass.current.textFaint)}
                val counts=snapshot.optJSONObject("counts")?:JSONObject()
                counts.keys().forEach { label -> item { Text("$label: ${counts.optLong(label)}",color=LocalGlass.current.text) } }
                item { SectionHeader("Client summaries (up to 500)") }
                val clients=snapshot.optJSONArray("clients")?.objList().orEmpty()
                items(clients.size){val c=clients[it];GlassCard{
                    Text(c.optString("name"),color=LocalGlass.current.text)
                    Text("${if(c.optBoolean("enable"))"Enabled" else "Disabled"} · Used ${Fmt.size(c.optLong("used"))} · Quota ${if(c.optLong("volume")>0)Fmt.size(c.optLong("volume")) else "Unlimited"}",color=LocalGlass.current.textFaint)
                    Text("Expiry: "+if(c.optLong("expiry")>0)java.time.Instant.ofEpochSecond(c.optLong("expiry")).toString() else "No fixed expiry",color=LocalGlass.current.textFaint)
                }}
                item { SectionHeader("Inbound summaries (up to 100)") }
                val inbounds=snapshot.optJSONArray("inbounds")?.objList().orEmpty()
                items(inbounds.size){Text("${inbounds[it].optString("tag")} · ${inbounds[it].optString("type")}",color=LocalGlass.current.textFaint)}
            }
        }
    }
}
