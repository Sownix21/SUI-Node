package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Hash-only verification receipt. Credentials never appear in review text or receipt values. */
object SaveEvidence {
    fun hash(value: Any?): String = MessageDigest.getInstance("SHA-256").digest(JsonCanonical.text(value).toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val volatile = setOf("up", "down", "totalUp", "totalDown", "onlineAt", "createdAt")
    private fun checks(o: JSONObject) = JSONObject().apply {
        o.keys().forEach { key -> if (key !in volatile && !(key == "id" && o.optLong("id") == 0L)) put(key, hash(o.opt(key))) }
    }
    private fun identity(o: JSONObject): Pair<String, Any> = if (o.optLong("id") > 0) "id" to o.get("id")
        else if (o.optString("name").isNotBlank()) "name" to o.getString("name")
        else if (o.optString("tag").isNotBlank()) "tag" to o.getString("tag")
        else throw IllegalArgumentException("A stable record identifier is required for a safe save")
    private fun sameId(o: JSONObject, key: String, value: Any) = o.opt(key)?.toString() == value.toString()
    fun build(collection: String, action: String, payload: Any?, before: Any?): JSONObject {
        val receipt = jo("object" to collection, "action" to action, "time" to System.currentTimeMillis()/1000)
        if (collection in setOf("config", "settings")) return receipt.put("checks", checks(payload as JSONObject))
        val current = (before as? JSONArray)?.objList().orEmpty()
        val delete = action in setOf("del", "delbulk")
        val items = if (payload is JSONArray) (0 until payload.length()).map { payload.get(it) } else listOf(requireNotNull(payload))
        val entries = items.map { value ->
            if (delete) {
                val key = if (value is Number) "id" else "tag"
                jo("key" to key, "value" to value, "delete" to true)
            } else {
                val record = value as? JSONObject ?: throw IllegalArgumentException("Expected a full record")
                val (key, id) = identity(record)
                if (action in setOf("new", "addbulk")) require(current.none { sameId(it, key, id) }) {
                    "A record with that identifier already exists. Refresh instead of creating a duplicate."
                }
                jo("key" to key, "value" to id, "checks" to checks(record))
            }
        }
        return receipt.put("entries", jarr(entries))
    }
    fun matches(receipt: JSONObject, current: Any?): Boolean {
        fun matchesChecks(checks: JSONObject, record: JSONObject) = checks.keys().asSequence().all { key ->
            record.has(key) && hash(record.opt(key)) == checks.optString(key)
        }
        receipt.optJSONObject("checks")?.let { return current is JSONObject && matchesChecks(it,current) }
        val entries = receipt.optJSONArray("entries")?.objList() ?: return false
        val rows = (current as? JSONArray)?.objList() ?: return false
        return entries.all { entry ->
            val found = rows.filter { sameId(it,entry.getString("key"),entry.get("value")) }
            if (entry.optBoolean("delete")) found.isEmpty() else found.size == 1 && matchesChecks(entry.getJSONObject("checks"),found.single())
        }
    }
    fun reviewBefore(receipt: JSONObject, before: Any?): Any? {
        if (before !is JSONArray) return before
        val entries = receipt.optJSONArray("entries")?.objList().orEmpty()
        val selected = entries.mapNotNull { e -> before.objList().firstOrNull { sameId(it,e.getString("key"),e.get("value")) } }
        return if (receipt.optString("action") in setOf("new","addbulk")) null
            else if (receipt.optString("action") in setOf("edit","del")) selected.firstOrNull() else jarr(selected)
    }
    private val secret = Regex("(?i)password|token|secret|private|public_key|uuid|credential|auth|certificate|key|header|link|uri|url|config\\.")
    fun diff(before: Any?, after: Any?): List<String> {
        val lines = mutableListOf<String>()
        fun display(path: String, value: Any?): String = if (secret.containsMatchIn(path)) "[hidden]" else when(value) {
            null, JSONObject.NULL -> "(absent)"
            is JSONObject -> "{${value.length()} fields}"
            is JSONArray -> "[${value.length()} items]"
            is String -> if (path.substringAfterLast('.') in setOf("type","tag","name","action","mode","network","method","strategy","level")) value.replace('\n',' ').take(180) else "[text hidden]"
            else -> value.toString().take(180)
        }
        fun visit(path: String, a: Any?, b: Any?) {
            if (JsonCanonical.text(a) == JsonCanonical.text(b)) return
            if (secret.containsMatchIn(path)) { lines += "$path: [hidden] → [hidden] (changed)"; return }
            if (a is JSONObject || b is JSONObject) {
                val aa = a as? JSONObject; val bb = b as? JSONObject
                val keys = (aa?.keys()?.asSequence()?.toList().orEmpty() + bb?.keys()?.asSequence()?.toList().orEmpty()).distinct().sorted()
                keys.forEach { visit(if (path.isBlank()) it else "$path.$it", aa?.opt(it), bb?.opt(it)) }
            } else if (a is JSONArray || b is JSONArray) {
                val aa = a as? JSONArray; val bb = b as? JSONArray
                for (i in 0 until maxOf(aa?.length() ?: 0,bb?.length() ?: 0)) visit("$path[$i]",aa?.opt(i),bb?.opt(i))
            } else lines += "$path: ${display(path,a)} → ${display(path,b)}"
        }
        visit("",before,after)
        return lines
    }
    fun stable(value: Any?): Any? = when(value) {
        is JSONObject -> value.deepCopy().apply { volatile.forEach(::remove) }
        is JSONArray -> jarr(value.objList().map { stable(it) })
        else -> value
    }
}
