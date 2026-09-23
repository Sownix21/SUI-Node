package com.sonix21.suinode.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Exact conversion prevents a displayed quota from changing on an unrelated client edit. */
object VolumeInput {
    private val gib = BigDecimal(1073741824L)
    fun format(bytes: Long): String = if (bytes == 0L) "" else BigDecimal(bytes).divide(gib).stripTrailingZeros().toPlainString()

    fun parse(text: String): Long? {
        if (text.isBlank()) return 0L
        val normalized = text.trim().map { c ->
            c.digitToIntOrNull()?.toString() ?: if (c == '٫') "." else c.toString()
        }.joinToString("")
        if (!normalized.matches(Regex("[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+"))) return null
        return try {
            BigDecimal(normalized).multiply(gib).setScale(0, RoundingMode.DOWN).longValueExact()
        } catch (_: ArithmeticException) { null }
    }
}
