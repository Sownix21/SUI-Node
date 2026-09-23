package com.sonix21.suinode.data

import android.content.Context
import com.sonix21.suinode.APP
import com.sonix21.suinode.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException

class SaveReview(val title: String, val lines: List<String>, val decision: CompletableDeferred<Boolean> = CompletableDeferred())
object SaveGuard {
    private var store: PanelStore? = null
    val review = MutableStateFlow<SaveReview?>(null)
    private val mutex = Mutex()
    fun init(context: Context) { store = PanelStore(context.applicationContext) }
    private suspend fun read(api: SuiClient, collection: String, ids: List<Long> = emptyList()): Any {
        val response = api.get(collection, if (ids.isEmpty()) emptyMap() else mapOf("id" to ids.joinToString(",")))
        if (!response.success) throw IOException(response.msg)
        val body = response.objObj() ?: throw IOException("Invalid APIv2 save verification response")
        if (collection == "settings") return body
        val raw = body.opt(collection)
        if (raw == JSONObject.NULL && collection != "config") return JSONArray()
        return raw ?: throw IOException("Missing collection during save verification")
    }
    suspend fun current(api: SuiClient, receipt: JSONObject): Any {
        val collection = receipt.getString("object")
        if (collection !in setOf("clients","inbounds")) return read(api,collection)
        val entries = receipt.optJSONArray("entries")?.objList().orEmpty()
        val ids = entries.filter { it.optString("key") == "id" }.map { it.getLong("value") }
        if (ids.size == entries.size && ids.isNotEmpty()) return read(api,collection,ids)
        val summary = read(api,collection) as? JSONArray ?: throw IOException("Invalid collection")
        val found = summary.objList().filter { row -> entries.any { row.opt(it.optString("key"))?.toString() == it.opt("value")?.toString() } }
        return if (found.isEmpty()) summary else read(api,collection,found.map { it.getLong("id") })
    }
    suspend fun requireNoPending(panel: Panel) {
        val storage = store ?: return
        check(withContext(Dispatchers.IO) { storage.localRecord("pendingSaves",panel.id) } == null) {
            "A previous save has an uncertain outcome. Open Pending changes for this panel before making another change."
        }
    }
    suspend fun save(api: SuiClient, panel: Panel, fields: Map<String,String?>, send: suspend () -> Envelope): Envelope {
        val storage = store ?: return send() // Isolated JVM fixtures; production initializes in Application.
        return mutex.withLock {
            val requestHash = SaveEvidence.hash(JSONObject(fields.filterValues { it != null }))
            val pending = withContext(Dispatchers.IO) { storage.localRecord("pendingSaves",panel.id) }
            if (pending != null) {
                val applied = runCatching { SaveEvidence.matches(pending,current(api,pending)) }.getOrDefault(false)
                check(applied) { "Previous save outcome is uncertain. Open Pending changes, inspect the panel, and acknowledge it before retrying." }
                withContext(Dispatchers.IO) { storage.saveLocalRecord("pendingSaves",panel.id,null) }
                if (pending.optString("requestHash") == requestHash) return@withLock Envelope(true,"The previous save is confirmed by GET; no duplicate request was sent.",null)
            }
            val reviewEnabled = APP.prefs.reviewChanges
            val recoveryEnabled = APP.prefs.recoverSaves
            if (!reviewEnabled && !recoveryEnabled) return@withLock send()
            val collection = requireNotNull(fields["object"])
            val action = requireNotNull(fields["action"])
            val payload = JSONTokener(requireNotNull(fields["data"])).nextValue()
            val values = if (payload is JSONArray) payload.objList() else listOfNotNull(payload as? JSONObject)
            val ids = if (collection in setOf("clients","inbounds") && action in setOf("edit","editbulk")) values.map { it.optLong("id") }.filter { it > 0 } else emptyList()
            val before = read(api,collection,ids)
            val receipt = SaveEvidence.build(collection,action,payload,before).put("requestHash",requestHash)
            val selectedBefore = SaveEvidence.reviewBefore(receipt,before)
            val beforeHash = SaveEvidence.hash(SaveEvidence.stable(selectedBefore))
            val lines = SaveEvidence.diff(selectedBefore,if(action in setOf("del","delbulk")) null else payload) +
                listOfNotNull(fields["initUsers"]?.takeIf { it.isNotBlank() }?.let { users ->
                    val ids = users.split(',').mapNotNull { it.trim().toLongOrNull() }
                    "Initialize inbound users: ${ids.size} selected client IDs (${ids.joinToString(", ")})"
                })
            receipt.put("summary",jarr(lines.take(300)))
            if (reviewEnabled) {
                val request = SaveReview("${panel.name} · $action $collection",lines)
                review.value = request
                try { check(request.decision.await()) { "Save cancelled. Nothing was sent to the panel." } }
                finally { if (review.value === request) review.value = null }
                // Check again after the user has spent time reviewing. This is not server-side CAS.
                val refreshed = read(api,collection,ids)
                if(action in setOf("new","addbulk")) SaveEvidence.build(collection,action,payload,refreshed)
                check(beforeHash == SaveEvidence.hash(SaveEvidence.stable(SaveEvidence.reviewBefore(receipt,refreshed)))) {
                    "Panel data changed during review. Refresh the editor and review again."
                }
            }
            if (recoveryEnabled) withContext(Dispatchers.IO) { storage.saveLocalRecord("pendingSaves",panel.id,receipt) }
            try {
                val response = send()
                if (recoveryEnabled) withContext(Dispatchers.IO) { storage.saveLocalRecord("pendingSaves",panel.id,null) }
                response
            } catch (e: CancellationException) { throw e }
            catch (e: IOException) {
                if (!recoveryEnabled) throw e
                val applied = runCatching { SaveEvidence.matches(receipt,current(api,receipt)) }.getOrDefault(false)
                if (applied) {
                    withContext(Dispatchers.IO) { storage.saveLocalRecord("pendingSaves",panel.id,null) }
                    Envelope(true,"The response was interrupted, but GET confirms the requested state. No retry was sent.",null)
                } else throw IOException("Save outcome is uncertain; it was not retried. Open Pending changes to check the panel before trying again.")
            }
        }
    }
}
