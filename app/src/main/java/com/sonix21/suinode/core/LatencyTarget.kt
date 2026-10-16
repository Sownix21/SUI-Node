package com.sonix21.suinode.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Kept only in the panel session: test URLs can contain private query parameters. */
data class LatencyTarget(val enabled: Boolean = false, val url: String = "") {
    fun link(): String? {
        if (!enabled || url.isBlank()) return null
        val value = url.trim()
        require(Regex("(?i)^https?://").containsMatchIn(value) &&
            value.none { it.isWhitespace() || it.isISOControl() } &&
            value.toHttpUrlOrNull() != null) { "Enter a valid HTTP or HTTPS test URL." }
        return value
    }
}
