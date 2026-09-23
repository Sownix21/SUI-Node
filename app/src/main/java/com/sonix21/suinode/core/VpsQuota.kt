package com.sonix21.suinode.core

import org.json.JSONObject
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class VpsQuotaConfig(val enabled: Boolean = false, val mode: String = "combined",
    val receiveLimit: Long = 5_000_000_000_000, val sendLimit: Long = 5_000_000_000_000,
    val totalLimit: Long = 10_000_000_000_000, val resetDay: Int = 1, val zone: String = "UTC",
    val threshold: Int = 90, val initialReceive: Long = 0, val initialSend: Long = 0, val baseline: String = "initial") {
    fun toJson() = jo("enabled" to enabled, "mode" to mode, "receiveLimit" to receiveLimit, "sendLimit" to sendLimit,
        "totalLimit" to totalLimit, "resetDay" to resetDay, "zone" to zone, "threshold" to threshold,
        "initialReceive" to initialReceive, "initialSend" to initialSend, "baseline" to baseline)
    fun validate() {
        require(mode in setOf("combined", "receive", "send", "separate"))
        require(resetDay in 1..31 && threshold in 1..100)
        ZoneId.of(zone)
        require(receiveLimit > 0 && sendLimit > 0 && totalLimit > 0 && initialReceive >= 0 && initialSend >= 0)
    }
    companion object {
        fun fromJson(o: JSONObject?): VpsQuotaConfig = if (o == null) VpsQuotaConfig() else VpsQuotaConfig(
            o.optBoolean("enabled"), o.optString("mode", "combined"), o.optLong("receiveLimit", 5_000_000_000_000),
            o.optLong("sendLimit", 5_000_000_000_000), o.optLong("totalLimit", 10_000_000_000_000),
            o.optInt("resetDay", 1), o.optString("zone", "UTC"), o.optInt("threshold", 90),
            o.optLong("initialReceive"), o.optLong("initialSend"), o.optString("baseline", "initial")).also { it.validate() }
    }
}

object VpsQuota {
    fun cycleStart(now: Long, config: VpsQuotaConfig): Long {
        val zone = ZoneId.of(config.zone)
        val local = Instant.ofEpochSecond(now).atZone(zone)
        fun start(month: YearMonth) = month.atDay(minOf(config.resetDay, month.lengthOfMonth())).atStartOfDay(zone).toEpochSecond()
        val month = YearMonth.from(local)
        return start(month).let { if (now < it) start(month.minusMonths(1)) else it }
    }
    fun add(a: Long, b: Long) = if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b

    /** Device-observed deltas, never a claim to match a provider's invoice. */
    fun sample(old: JSONObject?, config: VpsQuotaConfig, now: Long, receive: Long, send: Long, boot: Long): JSONObject {
        config.validate()
        require(receive >= 0 && send >= 0 && boot >= 0)
        val cycle = cycleStart(now, config)
        val fresh = old == null || old.optString("baseline") != config.baseline
        if (!fresh && now <= old!!.optLong("lastCheck")) return old.deepCopy()
        val sameCycle = !fresh && old!!.optLong("cycle") == cycle
        var rx = if (fresh) config.initialReceive else if (sameCycle) old!!.optLong("usedReceive") else 0L
        var tx = if (fresh) config.initialSend else if (sameCycle) old!!.optLong("usedSend") else 0L
        var uncertain = if (fresh) true else old!!.optBoolean("uncertain")
        var note = if (fresh) "Started from your supplied usage; earlier traffic is not reconstructed" else old!!.optString("note")
        if (sameCycle) {
            val restarted = boot > 0 && old!!.optLong("boot") > 0 && boot != old.optLong("boot")
            val reset = restarted || receive < old!!.optLong("receive") || send < old.optLong("send")
            if (reset) {
                uncertain = true
                note = "Server restart/counter reset detected; traffic before the reset may be missing"
                if (boot >= cycle) { rx = add(rx, receive); tx = add(tx, send) }
            } else {
                rx = add(rx, receive - old.optLong("receive")); tx = add(tx, send - old.optLong("send"))
            }
        } else if (!fresh) {
            uncertain = true
            note = "New billing cycle; traffic spanning the reset boundary cannot be split precisely"
        }
        return jo("baseline" to config.baseline, "cycle" to cycle, "lastCheck" to now, "receive" to receive,
            "send" to send, "boot" to boot, "usedReceive" to rx, "usedSend" to tx, "uncertain" to uncertain, "note" to note)
    }
    fun alerts(panelId: String, panelName: String, usage: JSONObject, config: VpsQuotaConfig): List<AlertCandidate> {
        if (!config.enabled) return emptyList()
        val rx = usage.optLong("usedReceive"); val tx = usage.optLong("usedSend")
        val meters = when (config.mode) {
            "receive" -> listOf(Triple("receive", rx, config.receiveLimit))
            "send" -> listOf(Triple("send", tx, config.sendLimit))
            "separate" -> listOf(Triple("receive", rx, config.receiveLimit), Triple("send", tx, config.sendLimit))
            else -> listOf(Triple("combined", add(rx, tx), config.totalLimit))
        }
        return meters.filter { (_, used, limit) -> used.toDouble() / limit * 100 >= config.threshold }.map { (meter, used, limit) ->
            AlertCandidate("$panelId:vps:$meter", panelId, "vps", if (used >= limit) "VPS quota reached" else "VPS quota approaching",
                "$panelName · $meter · ${(used.toDouble() / limit * 100).toLong()}% observed (estimate)",
                "${usage.optLong("cycle")}:$limit:${config.baseline}", if (used >= limit) 2 else 1)
        }
    }
}
