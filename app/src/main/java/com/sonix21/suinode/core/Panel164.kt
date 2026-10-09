package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject

/** Wire shapes and conditional fields reviewed against s-ui/frontend 1.6.4. */
object Panel164 {
    val sourceIpKeys = listOf("source_ip_cidr", "source_ip_is_private")

    /** Switching the selected condition removes only its mutually exclusive alternative. */
    fun sourceIpMode(rule: JSONObject, key: String?) {
        require(key == null || key in sourceIpKeys) { "Invalid source IP condition" }
        sourceIpKeys.filter { it != key }.forEach(rule::remove)
        if (key != null && !rule.has(key)) rule.put(key, if (key == "source_ip_cidr") JSONArray() else false)
    }

    /** Return a detached array so the screen observes each reorder and keeps unknown fields. */
    fun moveRule(rules: JSONArray, from: Int, to: Int): JSONArray {
        require(from in 0 until rules.length() && to in 0 until rules.length()) { "Rule index is out of range" }
        val values = (0 until rules.length()).map { rules.get(it) }.toMutableList()
        values.add(to, values.removeAt(from))
        return JSONArray(JSONArray(values).toString())
    }
}
