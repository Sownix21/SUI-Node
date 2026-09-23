package com.sonix21.suinode.core

import org.json.JSONObject

object RenewalPlan {
    fun apply(source: JSONObject, days: Long, gib: Long, enable: Boolean, now: Long): JSONObject {
        require(days in 0..3650 && gib in 0..1_000_000) { "Renewal amount is out of range" }
        return source.deepCopy().apply {
            val expiry = optLong("expiry")
            val volume = optLong("volume")
            if (days > 0 && expiry > 0 && !optBoolean("delayStart"))
                put("expiry", Math.addExact(maxOf(expiry, now), Math.multiplyExact(days, 86400L)))
            if (gib > 0 && volume > 0) put("volume", Math.addExact(volume, Math.multiplyExact(gib, 1073741824L)))
            if (enable) put("enable", true)
        }
    }

    fun sameConfiguration(a: JSONObject, b: JSONObject): Boolean {
        val volatile = setOf("up", "down", "totalUp", "totalDown", "onlineAt")
        fun withoutCounters(o: JSONObject) = o.deepCopy().apply { volatile.forEach(::remove) }
        return JsonCanonical.text(withoutCounters(a)) == JsonCanonical.text(withoutCounters(b))
    }
}
