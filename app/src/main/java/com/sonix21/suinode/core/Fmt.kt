package com.sonix21.suinode.core

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object Rand {
    private val rng = SecureRandom()
    private const val SEQ = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun seq(n: Int): String = buildString { repeat(n) { append(SEQ[rng.nextInt(SEQ.length)]) } }

    fun uuid(): String {
        val b = ByteArray(16); rng.nextBytes(b)
        b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()
        b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = b.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0,8)}-${hex.substring(8,12)}-${hex.substring(12,16)}-${hex.substring(16,20)}-${hex.substring(20)}"
    }

    /** base64 of n random bytes (shadowsocks 2022 style passwords). */
    fun ssPassword(n: Int): String {
        val b = ByteArray(n); rng.nextBytes(b)
        return Base64.getEncoder().encodeToString(b)
    }

    fun int(min: Int, max: Int): Int = if (max <= min) min else min + rng.nextInt(max - min + 1)

    fun bytes(n: Int): ByteArray { val b = ByteArray(n); rng.nextBytes(b); return b }

    /** reality short ids: 24 entries, first empty, rest 0-7 random hex bytes. */
    fun shortIds(): List<String> = (0 until 24).map {
        if (it == 0) "" else Rand.bytes(int(1, 7)).joinToString("") { b -> "%02x".format(b) }
    }
}

object Fmt {
    fun size(bytes: Long, digits: Int = 2): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var v = bytes.toDouble(); var i = 0
        while (v >= 1024.0 && i < units.size - 1) { v /= 1024.0; i++ }
        return if (i == 0) "${bytes.toLong()} ${units[i]}"
        else String.format(Locale.US, "%.${digits}f %s", v, units[i])
    }

    fun packets(p: Long): String {
        if (p <= 0) return "0"
        val units = arrayOf("", "K", "M", "G", "T")
        var v = p.toDouble(); var i = 0
        while (v >= 1000.0 && i < units.size - 1) { v /= 1000.0; i++ }
        return String.format(Locale.US, "%.${if (i == 0) 0 else 1}f%s", v, units[i])
    }

    fun duration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "-"
        val d = totalSeconds / 86400
        val h = (totalSeconds % 86400) / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun remainedDays(expiryUnix: Long, nowSec: Long = System.currentTimeMillis() / 1000): String {
        if (expiryUnix <= 0) return "∞"
        val diff = expiryUnix - nowSec
        return when {
            diff <= 0 -> "expired"
            diff < 3600 -> "${diff / 60}m"
            diff < 86400 -> "${diff / 3600}h"
            else -> "${diff / 86400}d"
        }
    }

    fun dateTime(unixSec: Long, pattern: String = "yyyy-MM-dd HH:mm"): String {
        if (unixSec <= 0) return "-"
        val cal = Calendar.getInstance()
        cal.timeInMillis = unixSec * 1000
        return SimpleDateFormat(pattern, Locale.getDefault()).format(cal.time)
    }

    fun parseDateTimeToUnix(text: String, pattern: String = "yyyy-MM-dd HH:mm"): Long? = try {
        val f = SimpleDateFormat(pattern, Locale.US)
        f.timeZone = TimeZone.getDefault()
        f.parse(text)?.time?.let { it / 1000 }
    } catch (_: Exception) { null }

    fun percent(a: Long, b: Long): Float =
        if (b <= 0) 0f else (a * 100f / b.toFloat()).coerceIn(0f, 999f)

    fun speed(bytesPerInterval: Long, intervalSec: Long): String =
        size(bytesPerInterval.coerceAtLeast(0), 1) + "/s"
}

/** English <-> Persian digit conversion + locale-aware number rendering. */
object Digits {
    private val fa = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')

    fun toFa(s: String): String = buildString {
        for (c in s) append(if (c in '0'..'9') fa[c - '0'] else c)
    }

    fun localize(s: String, fa: Boolean): String = if (fa) toFa(s) else s
}
