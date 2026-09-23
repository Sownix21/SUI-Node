package com.sonix21.suinode.core

import org.json.JSONObject

data class LatencyResult(val delayMs: Long? = null, val error: String? = null, val loading: Boolean = false) {
    companion object {
        fun parse(o: JSONObject?): LatencyResult {
            if (o == null) return LatencyResult(error = "The panel returned an invalid test result.")
            if (!o.optBoolean("OK")) return LatencyResult(error = o.optString("Error").ifBlank { "The core could not test this endpoint." })
            val delay = (o.opt("Delay") as? Number)?.toLong()?.takeIf { it >= 0 }
            return if (delay == null) LatencyResult(error = "The panel returned no valid latency.") else LatencyResult(delayMs = delay)
        }
    }
}
