package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject

/** A detached, wire-format rule exists before Compose renders the editor. */
class RoutingDraft(source: JSONObject, index: Int, defaultOutbound: String) {
    val config = source.deepCopy()
    val rule: JSONObject
    private var suspendedLogical: JSONObject? = null

    init {
        val route = config.optJSONObject("route") ?: JSONObject().also { config.put("route", it) }
        val rules = route.optJSONArray("rules") ?: JSONArray().also { route.put("rules", it) }
        rule = if (index < 0) {
            jo("action" to "route", "outbound" to defaultOutbound).also { rules.put(it) }
        } else {
            requireNotNull(rules.optJSONObject(index)) { "The routing rule no longer exists. Refresh and try again." }
        }
    }

    /** Keep the first condition when simplifying, and retain other conditions for a toggle back. */
    fun setLogical(enabled: Boolean) {
        if (enabled == (rule.optString("type") == "logical")) return
        if (enabled) {
            val condition = JSONObject()
            rule.keys().asSequence().toList().filter { it !in actionKeys }.forEach {
                condition.put(it, rule.remove(it))
            }
            val previous = suspendedLogical
            val rules = previous?.optJSONArray("rules") ?: JSONArray()
            rules.put(0, condition)
            rule.put("type", "logical")
            rule.put("mode", previous?.optString("mode", "and") ?: "and")
            rule.put("rules", rules)
        } else {
            suspendedLogical = rule.deepCopy()
            val first = rule.optJSONArray("rules")?.optJSONObject(0)?.deepCopy() ?: JSONObject()
            rule.remove("type"); rule.remove("mode"); rule.remove("rules")
            first.keys().forEach { if (it !in actionKeys) rule.put(it, first.get(it)) }
        }
    }

    private companion object {
        val actionKeys = setOf("action", "invert", "outbound", "override_address", "override_port",
            "network_strategy", "fallback_delay", "udp_disable_domain_unmapping", "udp_connect",
            "udp_timeout", "method", "no_drop", "sniffer", "timeout", "strategy", "server")
    }
}
