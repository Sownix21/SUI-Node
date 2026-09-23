package com.sonix21.suinode.data

import android.content.Context
import com.sonix21.suinode.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OfflineOverview {
    private var store: PanelStore? = null
    fun init(context: Context){store=PanelStore(context.applicationContext)}
    suspend fun capture(panel: Panel,data: PanelData) {
        if(!panel.offlineOverview)return
        val storage=store?:return
        withContext(Dispatchers.IO){
            val now=System.currentTimeMillis()/1000
            if(now-(storage.localRecord("offlineOverviews",panel.id)?.optLong("time")?:0)<60)return@withContext
            val clients=data.clients.take(500).map { c -> jo("name" to c.optString("name"),"enable" to c.optBoolean("enable"),
                "expiry" to c.optLong("expiry"),"volume" to c.optLong("volume"),"used" to ClientHealth.usage(c)) }
            val inbounds=data.inbounds.take(100).map { jo("tag" to it.optString("tag"),"type" to it.optString("type")) }
            storage.saveLocalRecord("offlineOverviews",panel.id,jo("time" to now,"clients" to jarr(clients),"inbounds" to jarr(inbounds),
                "counts" to jo("Clients" to data.clients.size,"Inbounds" to data.inbounds.size,"Outbounds" to data.outbounds.size,
                    "Endpoints" to data.endpoints.size,"Services" to data.services.size,"TLS templates" to data.tlsConfigs.size,"Online clients" to data.onlines.user.size)))
        }
    }
}
