package com.sonix21.suinode.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

object DateTimeInput {
    fun pickerDate(date: String): Long = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun calendarDate(dateMillis: Long): String = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()
    fun pickerDate(unix: Long, zone: ZoneId): Long = Instant.ofEpochSecond(unix).atZone(zone)
        .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun unix(dateMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val local = LocalDateTime.of(date, LocalTime.of(hour, minute))
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) { "This local time does not exist because of daylight saving. Choose another time." }
        return local.toEpochSecond(offsets.first())
    }
}
