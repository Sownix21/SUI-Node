package com.sonix21.suinode.core

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.math.BigDecimal

data class VpsRenewal(val enabled: Boolean = false, val due: String = LocalDate.now().plusMonths(1).toString(),
    val schedule: String = "monthly", val customDays: Int = 30, val price: String = "", val currency: String = "USD",
    val warningDays: Int = 7, val zone: String = "UTC", val anchorDay: Int = 0) {
    fun toJson() = jo("enabled" to enabled, "due" to due, "schedule" to schedule, "customDays" to customDays,
        "price" to price, "currency" to currency, "warningDays" to warningDays, "zone" to zone, "anchorDay" to anchorDay)
    fun validate() {
        LocalDate.parse(due); ZoneId.of(zone)
        require(schedule in setOf("monthly", "quarterly", "yearly", "custom"))
        require(customDays in 1..3650 && warningDays in 0..90)
        require(anchorDay in 0..31)
        require(currency.length in 1..12 && price.length <= 30)
        require(price.isBlank() || BigDecimal(price) >= BigDecimal.ZERO) { "Enter a valid nonnegative price" }
    }
    fun next(): VpsRenewal {
        val date = LocalDate.parse(due)
        val anchor=anchorDay.takeIf{it>0}?:date.dayOfMonth
        val advanced=when(schedule){"monthly"->date.plusMonths(1);"quarterly"->date.plusMonths(3);"yearly"->date.plusYears(1);else->date.plusDays(customDays.toLong())}
        return copy(due=(if(schedule=="custom")advanced else advanced.withDayOfMonth(minOf(anchor,advanced.lengthOfMonth()))).toString(),anchorDay=anchor)
    }
    fun alert(panelId: String, panelName: String, now: Long): AlertCandidate? {
        if (!enabled) return null
        validate()
        val date = LocalDate.parse(due)
        val today = java.time.Instant.ofEpochSecond(now).atZone(ZoneId.of(zone)).toLocalDate()
        if (today < date.minusDays(warningDays.toLong())) return null
        val overdue = today > date
        return AlertCandidate("$panelId:billing", panelId, "billing", if (overdue) "VPS renewal overdue" else "VPS renewal due soon",
            "$panelName · due $due" + if (price.isNotBlank()) " · $price $currency" else "", due, if (overdue) 2 else 1)
    }
    companion object {
        fun fromJson(o: JSONObject?): VpsRenewal = if (o == null) VpsRenewal() else VpsRenewal(o.optBoolean("enabled"),
            o.getString("due"), o.optString("schedule", "monthly"), o.optInt("customDays",30), o.optString("price"),
            o.optString("currency","USD"), o.optInt("warningDays",7), o.optString("zone","UTC"),o.optInt("anchorDay")).also { it.validate() }
    }
}
