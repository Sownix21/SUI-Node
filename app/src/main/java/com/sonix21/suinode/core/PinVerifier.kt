package com.sonix21.suinode.core

import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinVerifier {
    const val ITERATIONS = 600_000
    fun valid(pin: CharArray) = pin.size in 8..32 && pin.all { it in '0'..'9' }
    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun create(pin: CharArray): JSONObject {
        require(valid(pin)) { "Use an app PIN of 8–32 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        return try { jo("enabled" to true, "iterations" to ITERATIONS,
            "salt" to Base64.getEncoder().encodeToString(salt), "hash" to Base64.getEncoder().encodeToString(hash), "failures" to 0) }
        finally { hash.fill(0) }
    }
    fun verify(pin: CharArray, record: JSONObject): Boolean {
        if (!valid(pin)) return false
        check(record.getInt("iterations") == ITERATIONS) { "Unsupported app PIN record" }
        val salt = Base64.getDecoder().decode(record.getString("salt"))
        val expected = Base64.getDecoder().decode(record.getString("hash"))
        check(salt.size == 16 && expected.size == 32)
        val actual = derive(pin, salt)
        return try { MessageDigest.isEqual(expected, actual) } finally { expected.fill(0); actual.fill(0) }
    }
    fun remainingSeconds(record: JSONObject, now: Long) = (record.optLong("blockedUntil") - now).coerceIn(0, 1800)
    fun failed(record: JSONObject, now: Long) {
        val failures = (record.optInt("failures") + 1).coerceIn(1, 100)
        val delay = if (failures < 5) 0L else (30L shl (failures - 5).coerceAtMost(6)).coerceAtMost(1800)
        record.put("failures", failures).put("blockedUntil", now + delay)
    }
}
