package com.sonix21.suinode.core

object ApiTokenError {
    const val MESSAGE = "API token rejected (invalid or expired). Generate or renew a token in the s-ui web panel, then replace it in this app's panel profile."
    fun matches(message: String) = message.contains("invalid token", true) || message.contains("token expired", true) ||
        message.contains("expired token", true) || message.startsWith("API token rejected")
    fun explain(message: String) = if (matches(message)) MESSAGE else message
}
class RejectedApiToken : java.io.IOException(ApiTokenError.MESSAGE)
