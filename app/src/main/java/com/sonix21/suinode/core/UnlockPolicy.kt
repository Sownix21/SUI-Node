package com.sonix21.suinode.core

/** Unknown/unreadable PIN storage never enables a biometric bypass. */
enum class UnlockMode { LOADING, PIN, PIN_AND_BIOMETRIC, LEGACY_MIGRATION, PIN_UNAVAILABLE }

object UnlockPolicy {
    fun mode(configured: Boolean?, pinFirst: Boolean, biometric: Boolean): UnlockMode = when {
        configured == null -> UnlockMode.LOADING
        configured -> if (biometric) UnlockMode.PIN_AND_BIOMETRIC else UnlockMode.PIN
        !pinFirst -> UnlockMode.LEGACY_MIGRATION
        else -> UnlockMode.PIN_UNAVAILABLE
    }
    fun normalizePin(text: String): String = text.map { c -> when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> c
    } }.joinToString("")
}
