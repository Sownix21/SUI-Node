package com.sonix21.suinode.data

import android.content.Context
import com.sonix21.suinode.core.PinVerifier
import com.sonix21.suinode.core.VaultCipher
import org.json.JSONObject

/** Separate device-bound PIN verifier. Opening it never decrypts panel profiles. Call on IO. */
class AppPinStore(context: Context) {
    private val vault = KeystoreVault(context.applicationContext, "sui_node_app_pin_v1", "app-pin-v1.bin")
    private fun read(): JSONObject {
        val bytes = vault.read() ?: return JSONObject().put("enabled", false)
        val plain = VaultCipher.decrypt(vault.key(true), bytes)
        return try { JSONObject(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
    }
    private fun write(record: JSONObject) {
        val existing = vault.read() != null
        val key = vault.key(existing)
        val plain = record.toString().toByteArray(Charsets.UTF_8)
        try {
            vault.write(VaultCipher.encrypt(key, plain))
            val verified = VaultCipher.decrypt(key, requireNotNull(vault.read()))
            try { check(java.security.MessageDigest.isEqual(plain, verified)) } finally { verified.fill(0) }
        } finally { plain.fill(0) }
    }
    fun configured(): Boolean = synchronized(lock) { read().getBoolean("enabled") }
    fun verify(pin: CharArray): Boolean = synchronized(lock) {
        try { verifyInternal(pin) } finally { pin.fill('\u0000') }
    }
    private fun verifyInternal(pin: CharArray): Boolean {
        val record = read()
        check(record.optBoolean("enabled")) { "No app PIN configured" }
        val now = System.currentTimeMillis() / 1000
        val remaining = PinVerifier.remainingSeconds(record, now)
        check(remaining == 0L) { "Too many attempts. Try again in $remaining seconds." }
        val valid = PinVerifier.verify(pin, record)
        if (valid) record.put("failures", 0).put("blockedUntil", 0) else PinVerifier.failed(record, now)
        write(record)
        return valid
    }
    fun set(pin: CharArray, current: CharArray) = synchronized(lock) {
        try {
            if (read().optBoolean("enabled")) check(verifyInternal(current)) { "Current app PIN is incorrect" }
            write(PinVerifier.create(pin))
        } finally { pin.fill('\u0000'); current.fill('\u0000') }
    }
    fun remove(current: CharArray, onVerified: () -> Unit = {}) = synchronized(lock) {
        try {
            check(verifyInternal(current)) { "Current app PIN is incorrect" }
            // Disable dependent locks before removing their only recovery method.
            onVerified()
            write(JSONObject().put("enabled", false))
        } finally { current.fill('\u0000') }
    }
    private companion object { val lock = Any() }
}
