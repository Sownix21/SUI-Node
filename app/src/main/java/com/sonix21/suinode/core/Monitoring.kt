package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

data class MonitorConfig(
    val enabled: Boolean = false,
    val panelIds: Set<String> = emptySet(),
    val intervalMinutes: Long = 60,
    val expiry: Boolean = true,
    val expiryHours: Long = 72,
    val quota: Boolean = true,
    val quotaPercent: Int = 90,
    val includeDisabled: Boolean = true,
    val offline: Boolean = true,
    val coreStopped: Boolean = true,
    val failedChecks: Int = 3,
    val recovery: Boolean = true,
    val repeatHours: Long = 24,
    val quiet: Boolean = false,
    val quietStart: Int = 22,
    val quietEnd: Int = 8,
    val showNames: Boolean = false,
    val retentionDays: Int = 30,
) {
    fun toJson() = jo("enabled" to enabled, "panelIds" to jarr(panelIds.toList()),
        "intervalMinutes" to intervalMinutes, "expiry" to expiry, "expiryHours" to expiryHours,
        "quota" to quota, "quotaPercent" to quotaPercent, "includeDisabled" to includeDisabled,
        "offline" to offline, "coreStopped" to coreStopped, "failedChecks" to failedChecks,
        "recovery" to recovery, "repeatHours" to repeatHours, "quiet" to quiet,
        "quietStart" to quietStart, "quietEnd" to quietEnd, "showNames" to showNames, "retentionDays" to retentionDays)

    fun isQuiet(now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!quiet || quietStart == quietEnd) return false
        val hour = Instant.ofEpochSecond(now).atZone(zone).hour
        return if (quietStart < quietEnd) hour in quietStart until quietEnd else hour >= quietStart || hour < quietEnd
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) MonitorConfig() else MonitorConfig(
            enabled = o.optBoolean("enabled"), panelIds = o.optJSONArray("panelIds")?.strList()?.toSet().orEmpty(),
            intervalMinutes = o.optLong("intervalMinutes", 60).coerceIn(15, 1440),
            expiry = o.optBoolean("expiry", true), expiryHours = o.optLong("expiryHours", 72).coerceIn(1, 2160),
            quota = o.optBoolean("quota", true), quotaPercent = o.optInt("quotaPercent", 90).coerceIn(1, 100),
            includeDisabled = o.optBoolean("includeDisabled", true), offline = o.optBoolean("offline", true),
            coreStopped = o.optBoolean("coreStopped", true), failedChecks = o.optInt("failedChecks", 3).coerceIn(1, 10),
            recovery = o.optBoolean("recovery", true), repeatHours = o.optLong("repeatHours", 24).coerceIn(0, 168),
            quiet = o.optBoolean("quiet"), quietStart = o.optInt("quietStart", 22).coerceIn(0, 23),
            quietEnd = o.optInt("quietEnd", 8).coerceIn(0, 23), showNames = o.optBoolean("showNames"),
            retentionDays = o.optInt("retentionDays", 30).coerceIn(1, 90))
    }
}

data class AlertCandidate(val key: String, val panelId: String, val kind: String, val title: String,
    val detail: String, val fingerprint: String, val severity: Int = 1)

object ClientHealth {
    fun usage(c: JSONObject): Long {
        val up = c.optLong("up").coerceAtLeast(0)
        val down = c.optLong("down").coerceAtLeast(0)
        return if (Long.MAX_VALUE - up < down) Long.MAX_VALUE else up + down
    }

    fun alerts(panelId: String, panelName: String, clients: JSONArray, now: Long, cfg: MonitorConfig): List<AlertCandidate> = buildList {
        for (i in 0 until clients.length()) {
            val c = clients.optJSONObject(i) ?: continue
            if (!cfg.includeDisabled && !c.optBoolean("enable")) continue
            val id = c.optLong("id", -1)
            if (id < 0) continue
            val name = c.optString("name").take(120)
            val expiry = c.optLong("expiry")
            if (cfg.expiry && expiry > 0 && !c.optBoolean("delayStart") && expiry - now <= cfg.expiryHours * 3600) {
                val expired = expiry <= now
                add(AlertCandidate("$panelId:expiry:$id", panelId, "expiry", if (expired) "Client expired" else "Client expiry approaching",
                    "$panelName · $name · " + if (expired) "expired" else "expires within ${((expiry - now + 3599) / 3600)} hours",
                    expiry.toString(), if (expired) 2 else 1))
            }
            val limit = c.optLong("volume")
            val used = usage(c)
            if (cfg.quota && limit > 0 && used.toDouble() / limit * 100 >= cfg.quotaPercent) {
                val exhausted = used >= limit
                add(AlertCandidate("$panelId:quota:$id", panelId, "quota", if (exhausted) "Client quota exhausted" else "Client quota approaching",
                    "$panelName · $name · ${(used.toDouble() / limit * 100).toLong()}% used",
                    "$limit:${c.optLong("nextReset")}", if (exhausted) 2 else 1))
            }
        }
    }
}

/** Pure, deterministic alert lifecycle; missing data never resolves a condition. */
object AlertEngine {
    /** Removed/unselected panels are no longer active, not falsely reported as recovered. */
    fun pruneScope(state: JSONObject, panelIds: Set<String>, now: Long, config: MonitorConfig) {
        state.optJSONObject("active")?.let { active ->
            active.keys().asSequence().toList().filter { active.optJSONObject(it)?.optString("panelId") !in panelIds }.forEach(active::remove)
        }
        state.optJSONObject("checks")?.let { checks ->
            checks.keys().asSequence().toList().filter { it !in panelIds }.forEach(checks::remove)
        }
        val history = state.optJSONArray("history")?.objList().orEmpty()
        state.put("history", jarr(history.filter { it.optLong("time") >= now - config.retentionDays * 86400L }.takeLast(500)))
    }
    fun update(state: JSONObject, panelId: String, candidates: List<AlertCandidate>, knownKinds: Set<String>,
        now: Long, config: MonitorConfig, canNotify: Boolean): List<JSONObject> {
        val active = state.optJSONObject("active") ?: JSONObject().also { state.put("active", it) }
        val history = state.optJSONArray("history") ?: JSONArray().also { state.put("history", it) }
        val notifications = mutableListOf<JSONObject>()
        val keys = candidates.map { it.key }.toSet()
        active.keys().asSequence().toList().forEach { key ->
            val previous = active.optJSONObject(key) ?: return@forEach
            if (previous.optString("panelId") == panelId && previous.optString("kind") in knownKinds && key !in keys) {
                val resolved = previous.deepCopy().put("resolved", true).put("time", now)
                    .put("title", "Resolved: ${previous.optString("title")}")
                history.put(resolved)
                // Do not announce recovery for an alert the user was never notified about.
                if (config.recovery && canNotify && previous.optLong("lastSent") > 0) notifications.add(resolved)
                active.remove(key)
            }
        }
        for (c in candidates) {
            val old = active.optJSONObject(c.key)
            val changed = old == null || old.optString("fingerprint") != c.fingerprint || old.optInt("severity") != c.severity
            val record = jo("key" to c.key, "panelId" to c.panelId, "kind" to c.kind, "title" to c.title,
                "detail" to c.detail.take(400), "fingerprint" to c.fingerprint, "severity" to c.severity,
                "time" to (if (changed) now else old!!.optLong("time")), "resolved" to false,
                "lastSeen" to now, "lastSent" to (if (changed) 0L else old!!.optLong("lastSent")))
            if (changed) history.put(record.deepCopy())
            val lastSent = record.optLong("lastSent")
            if (canNotify && (lastSent == 0L || config.repeatHours > 0 && now - lastSent >= config.repeatHours * 3600)) {
                record.put("lastSent", now)
                notifications.add(record.deepCopy())
            }
            active.put(c.key, record)
        }
        val cutoff = now - config.retentionDays * 86400L
        val retained = history.objList().filter { it.optLong("time") >= cutoff }.takeLast(500)
        state.put("history", jarr(retained))
        return notifications
    }
}
