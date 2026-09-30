package com.sonix21.suinode.core

import org.json.JSONObject
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class VpsQuotaConfig(val enabled: Boolean = false, val mode: String = "combined",
    val receiveLimit: Long = 5_000_000_000_000, val sendLimit: Long = 5_000_000_000_000,
    val totalLimit: Long = 10_000_000_000_000, val resetDay: Int = 1, val zone: String = "UTC",
    val threshold: Int = 90, val initialReceive: Long = 0, val initialSend: Long = 0, val baseline: String = "initial",
    val resetHour: Int = 0, val resetMinute: Int = 0, val startMode: String = "now", val startAt: Long = 0) {
    fun toJson() = jo("enabled" to enabled, "mode" to mode, "receiveLimit" to receiveLimit, "sendLimit" to sendLimit,
        "totalLimit" to totalLimit, "resetDay" to resetDay, "zone" to zone, "threshold" to threshold,
        "initialReceive" to initialReceive, "initialSend" to initialSend, "baseline" to baseline,
        "resetHour" to resetHour, "resetMinute" to resetMinute, "startMode" to startMode, "startAt" to startAt)
    fun validate() {
        require(mode in setOf("combined", "receive", "send", "separate"))
        require(resetDay in 1..31 && threshold in 1..100)
        require(resetHour in 0..23 && resetMinute in 0..59 && startMode in setOf("now", "next_cycle") && startAt >= 0)
        ZoneId.of(zone)
        require(receiveLimit > 0 && sendLimit > 0 && totalLimit > 0 && initialReceive >= 0 && initialSend >= 0)
    }
    companion object {
        fun fromJson(o: JSONObject?): VpsQuotaConfig = if (o == null) VpsQuotaConfig() else VpsQuotaConfig(
            o.optBoolean("enabled"), o.optString("mode", "combined"), o.optLong("receiveLimit", 5_000_000_000_000),
            o.optLong("sendLimit", 5_000_000_000_000), o.optLong("totalLimit", 10_000_000_000_000),
            o.optInt("resetDay", 1), o.optString("zone", "UTC"), o.optInt("threshold", 90),
            o.optLong("initialReceive"), o.optLong("initialSend"), o.optString("baseline", "initial"),
            o.optInt("resetHour"), o.optInt("resetMinute"), o.optString("startMode", "now"), o.optLong("startAt")).also { it.validate() }
    }
}

object VpsQuota {
    fun calendarChanged(previous: VpsQuotaConfig, next: VpsQuotaConfig) =
        previous.resetDay != next.resetDay || previous.zone != next.zone || previous.resetHour != next.resetHour || previous.resetMinute != next.resetMinute

    fun parseGb(text: String): Long {
        val normalized = UnlockPolicy.normalizePin(text.trim()).replace('٫', '.')
        require(normalized.matches(Regex("[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+"))) { "Enter a nonnegative GB amount" }
        return try { java.math.BigDecimal(normalized).movePointRight(9).longValueExact() }
        catch (_: ArithmeticException) { throw IllegalArgumentException("GB amount is too large or has more than 9 decimal places") }
    }

    fun validateCalendarChange(previous: VpsQuotaConfig, next: VpsQuotaConfig, hasSample: Boolean, correction: Boolean) {
        require(!next.enabled || !hasSample || !calendarChanged(previous, next) || correction) {
            "Changing the billing calendar requires an already-used traffic correction from your provider dashboard"
        }
    }

    fun nextReset(now: Long, config: VpsQuotaConfig): Long {
        val zone = ZoneId.of(config.zone)
        val cycleMonth = YearMonth.from(Instant.ofEpochSecond(cycleStart(now, config)).atZone(zone)).plusMonths(1)
        return cycleMonth.atDay(minOf(config.resetDay, cycleMonth.lengthOfMonth())).atTime(config.resetHour, config.resetMinute).atZone(zone).toEpochSecond()
    }

    fun cycleStart(now: Long, config: VpsQuotaConfig): Long {
        val zone = ZoneId.of(config.zone)
        val local = Instant.ofEpochSecond(now).atZone(zone)
        fun start(month: YearMonth) = month.atDay(minOf(config.resetDay, month.lengthOfMonth())).atTime(config.resetHour, config.resetMinute).atZone(zone).toEpochSecond()
        val month = YearMonth.from(local)
        return start(month).let { if (now < it) start(month.minusMonths(1)) else it }
    }
    fun add(a: Long, b: Long) = if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b

    /** Device-observed deltas, never a claim to match a provider's invoice. */
    fun sample(old: JSONObject?, config: VpsQuotaConfig, now: Long, receive: Long, send: Long, boot: Long): JSONObject {
        config.validate()
        require(receive >= 0 && send >= 0 && boot >= 0)
        val cycle = cycleStart(now, config)
        if (old != null && old.optString("baseline") == config.baseline && now <= old.optLong("lastCheck")) return old.deepCopy()
        if (config.startMode == "next_cycle" && now < config.startAt) return jo(
            "baseline" to config.baseline, "cycle" to cycle, "lastCheck" to now, "receive" to receive,
            "send" to send, "boot" to boot, "usedReceive" to 0L, "usedSend" to 0L, "waiting" to true,
            "uncertain" to true, "note" to "Waiting for the selected billing reset; traffic is not counted yet")
        val fresh = old == null || old.optString("baseline") != config.baseline || old.optBoolean("waiting")
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
        if (!config.enabled || usage.optBoolean("waiting")) return emptyList()
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
