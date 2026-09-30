package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject

/** APIv2 sessions is an array, not the object-wrapped configuration collections. */
object LiveSessions {
    fun parse(value: Any?): List<JSONObject> {
        if (value == null || value == JSONObject.NULL) return emptyList()
        require(value is JSONArray) { "Invalid APIv2 sessions response" }
        return (0 until value.length()).map { index ->
            val row = value.optJSONObject(index) ?: error("Invalid session record")
            require(row.opt("id") is String && row.getString("id").isNotBlank()) { "Missing session identifier" }
            listOf("up", "down", "createdAt").forEach { key ->
                require(row.opt(key) is Number && row.getLong(key) >= 0) { "Invalid session $key" }
            }
            row
        }.sortedByDescending { it.getLong("createdAt") }
    }
}
