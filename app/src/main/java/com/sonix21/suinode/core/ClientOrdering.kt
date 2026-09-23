package com.sonix21.suinode.core

import org.json.JSONObject

object ClientOrdering {
    val fields = linkedMapOf("id" to "Panel order", "name" to "Name", "expiry" to "Expiry",
        "online" to "Online status", "enable" to "Enabled status", "inbounds" to "Inbounds",
        "createdAt" to "Created time", "onlineAt" to "Last online", "volume" to "Traffic quota",
        "used" to "Traffic used", "group" to "Group", "desc" to "Description")
    fun sort(clients: List<JSONObject>, key: String, descending: Boolean, online: Set<String>, tags: Map<Long, String>): List<JSONObject> {
        fun number(c: JSONObject): Long = when (key) {
            "online" -> if (c.optString("name") in online) 1 else 0
            "enable" -> if (c.optBoolean("enable")) 1 else 0
            "used" -> ClientHealth.usage(c)
            "expiry", "volume" -> c.optLong(key).takeIf { it > 0 } ?: Long.MAX_VALUE
            else -> c.optLong(key)
        }
        fun string(c: JSONObject) = if (key == "inbounds") c.optJSONArray(key)?.longList().orEmpty()
            .map { tags[it] ?: it.toString() }.sorted().joinToString(",") else c.optString(key)
        val cmp = Comparator<JSONObject> { a, b ->
            val value = if (key in setOf("name", "group", "desc", "inbounds")) string(a).compareTo(string(b), true)
                else number(a).compareTo(number(b))
            (if (descending) -value else value).takeIf { it != 0 } ?: a.optLong("id").compareTo(b.optLong("id"))
        }
        return clients.sortedWith(cmp)
    }
}
