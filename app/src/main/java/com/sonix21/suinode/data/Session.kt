package com.sonix21.suinode.data

import com.sonix21.suinode.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class Onlines(
    val inbound: List<String> = emptyList(),
    val outbound: List<String> = emptyList(),
    val user: List<String> = emptyList(),
)

/** Immutable snapshot of everything a panel exposes through load/save. */
data class PanelData(
    val loaded: Boolean = false,
    val lastLoad: Long = 0,
    val subURI: String = "",
    val os: String = "",
    val enableTraffic: Boolean = false,
    val maintenance: Boolean = false,
    val onlines: Onlines = Onlines(),
    val config: JSONObject = JSONObject(),
    val clients: List<JSONObject> = emptyList(),
    val inbounds: List<JSONObject> = emptyList(),
    val outbounds: List<JSONObject> = emptyList(),
    val endpoints: List<JSONObject> = emptyList(),
    val services: List<JSONObject> = emptyList(),
    val tlsConfigs: List<JSONObject> = emptyList(),
)

/** Live session with one panel. */
class PanelSession(val panel: Panel) {

    val client = SuiClient(panel)

    private val _data = MutableStateFlow(PanelData())
    val data: StateFlow<PanelData> = _data

    var lastError: String? = null
    val syncError = MutableStateFlow<String?>(null)
    val syncing = MutableStateFlow(false)
    private val dataMutex = Mutex()

    private fun mutate(block: (PanelData) -> PanelData) { _data.value = block(_data.value) }

    /** Mirrors the web store `setNewData`. */
    fun setNewData(obj: JSONObject?) {
        if (obj == null) return
        mutate { d ->
            var nd = d
            if (obj.has("subURI")) nd = nd.copy(subURI = obj.optString("subURI"))
            if (obj.has("os")) nd = nd.copy(os = obj.optString("os"))
            if (obj.has("enableTraffic")) nd = nd.copy(enableTraffic = obj.optBoolean("enableTraffic"))
            if (obj.has("maintenance")) nd = nd.copy(maintenance = obj.optBoolean("maintenance"))
            obj.optObj("onlines")?.let {
                nd = nd.copy(onlines = Onlines(
                    inbound = it.optArr("inbound")?.strList() ?: emptyList(),
                    outbound = it.optArr("outbound")?.strList() ?: emptyList(),
                    user = it.optArr("user")?.strList() ?: emptyList(),
                ))
            }
            if (obj.has("lastLoad")) nd = nd.copy(lastLoad = obj.optLongOr("lastLoad"))
            obj.optObj("config")?.let { nd = nd.copy(config = it.deepCopy()) }
            if (obj.has("clients")) nd = nd.copy(clients = obj.optArr("clients")?.objList()?.map { it.deepCopy() } ?: emptyList())
            if (obj.has("inbounds")) nd = nd.copy(inbounds = obj.optArr("inbounds")?.objList()?.map { it.deepCopy() } ?: emptyList())
            if (obj.has("outbounds")) nd = nd.copy(outbounds = obj.optArr("outbounds")?.objList()?.map { it.deepCopy() } ?: emptyList())
            if (obj.has("services")) nd = nd.copy(services = obj.optArr("services")?.objList()?.map { it.deepCopy() } ?: emptyList())
            if (obj.has("endpoints")) nd = nd.copy(endpoints = obj.optArr("endpoints")?.objList()?.map { it.deepCopy() } ?: emptyList())
            if (obj.has("tls")) nd = nd.copy(tlsConfigs = obj.optArr("tls")?.objList()?.map { it.deepCopy() } ?: emptyList())
            nd
        }
    }

    /** Always request a full snapshot: local clock timestamps cannot safely drive server lu. */
    suspend fun load(force: Boolean = false): Result<Unit> = dataMutex.withLock {
        syncing.value = true
        try {
            val env = client.get("load")
            if (!env.success) throw java.io.IOException(env.msg)
            val obj = env.objObj() ?: throw java.io.IOException("APIv2 load returned no data object")
            // An incomplete response must never silently turn the panel into an empty list.
            for (collection in listOf("clients", "inbounds", "outbounds", "endpoints", "services", "tls", "config")) {
                if (!obj.has(collection)) {
                    val part = client.get(collection)
                    if (!part.success) throw java.io.IOException(part.msg)
                    val value = part.objObj()
                    if (value == null || !value.has(collection)) throw java.io.IOException("Missing $collection in APIv2 response")
                    obj.put(collection, value.get(collection))
                }
                if (!obj.isNull(collection) && collection != "config" && obj.opt(collection) !is JSONArray)
                    throw java.io.IOException("Invalid $collection collection in APIv2 response")
                if (collection == "config" && obj.opt(collection) !is JSONObject)
                    throw java.io.IOException("Invalid config object in APIv2 response")
            }
            setNewData(obj)
            mutate { it.copy(loaded = true, lastLoad = System.currentTimeMillis() / 1000) }
            lastError = null
            syncError.value = null
            try { OfflineOverview.capture(panel,_data.value) } catch(e: kotlinx.coroutines.CancellationException){throw e} catch(_:Exception){ /* Cache failure never replaces a verified live snapshot. */ }
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e.message
            syncError.value = e.message ?: "Panel synchronization failed"
            Result.failure(e)
        } finally { syncing.value = false }
    }

    /**
     * POST save {object, action, data(json string), initUsers}.
     * On success merges refreshed collections into state.
     */
    suspend fun save(
        objName: String,
        action: String,
        payload: Any?,
        initUsers: List<Long>? = null,
    ): Result<String> = dataMutex.withLock { try {
        require(objName in setOf("clients", "inbounds", "outbounds", "endpoints", "services", "tls", "config", "settings")) { "Unsupported APIv2 save object" }
        require(_data.value.loaded) { "Wait for the panel data to finish loading before saving" }
        val fields = linkedMapOf<String, String?>(
            "object" to objName,
            "action" to action,
            "data" to when (payload) {
                null -> null
                is JSONObject -> (if (objName == "settings") Panel161.settingsPayload(payload) else payload).toString(2)
                is JSONArray -> payload.toString(2)
                is String -> JSONObject.quote(payload)
                else -> payload.toString()
            },
            "initUsers" to initUsers?.joinToString(","),
        )
        val env = client.postForm("save", fields)
        if (env.success) {
            com.sonix21.suinode.core.DraftRegistry.saved()
            setNewData(env.objObj())
            lastError = null
            Result.success(env.msg)
        } else {
            lastError = env.msg
            Result.failure(Exception(env.msg))
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        lastError = e.message
        Result.failure(e)
    } }

    /** List endpoints contain summaries; fetch full records before edits or cloning. */
    suspend fun fetchRecord(collection: String, id: Long): JSONObject {
        require(collection in setOf("clients", "inbounds", "outbounds", "endpoints", "services", "tls"))
        val env = client.get(collection, if (collection in setOf("clients", "inbounds")) mapOf("id" to id.toString()) else emptyMap())
        if (!env.success) throw java.io.IOException(env.msg)
        return env.objObj()?.optJSONArray(collection)?.objList()?.firstOrNull { it.optLongOr("id") == id }?.deepCopy()
            ?: throw java.io.IOException("The selected record no longer exists. Refresh the panel and try again.")
    }

    // ------------------------------------------------------------ tag sources

    fun inboundTags(d: PanelData = _data.value): List<String> =
        d.inbounds.mapNotNull { it.optStringOrNull("tag")?.takeIf(String::isNotEmpty) } +
        d.endpoints.filter { it.optLongOr("listen_port") > 0 }.mapNotNull { it.optStringOrNull("tag") }

    fun outboundTags(d: PanelData = _data.value): List<String> =
        d.outbounds.mapNotNull { it.optStringOrNull("tag") } +
        d.endpoints.mapNotNull { it.optStringOrNull("tag") }

    fun tailscaleEndpointTags(d: PanelData = _data.value): List<String> =
        d.endpoints.filter { it.optString("type") == "tailscale" }.mapNotNull { it.optStringOrNull("tag") }

    fun resolvedServiceTags(d: PanelData = _data.value): List<String> =
        d.services.filter { it.optString("type") == "resolved" }.mapNotNull { it.optStringOrNull("tag") }

    fun unmanagedSsInboundTags(d: PanelData = _data.value): List<String> =
        d.inbounds.filter { it.optString("type") == "shadowsocks" && !it.has("users") }
            .mapNotNull { it.optStringOrNull("tag") }

    fun dnsServerTags(d: PanelData = _data.value): List<String> =
        d.config.optObj("dns")?.optArr("servers")
            ?.objList()?.mapNotNull { it.optStringOrNull("tag")?.takeIf(String::isNotEmpty) } ?: emptyList()

    fun clientNames(d: PanelData = _data.value): List<String> = d.clients.map { it.optString("name") }

    fun groups(d: PanelData = _data.value): List<String> =
        d.clients.map { it.optString("group") }.filter { it.isNotBlank() }.distinct()

    companion object {
        const val OBJ_CLIENTS = "clients"
        const val OBJ_INBOUNDS = "inbounds"
        const val OBJ_OUTBOUNDS = "outbounds"
        const val OBJ_ENDPOINTS = "endpoints"
        const val OBJ_SERVICES = "services"
        const val OBJ_TLS = "tls"
        const val OBJ_CONFIG = "config"
        const val OBJ_SETTINGS = "settings"
    }
}
