package com.sonix21.suinode.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.GeneralSecurityException
import javax.crypto.SecretKey

/** No plaintext disk fallback or silent recovery to empty data on authentication failure. */
class VaultRepository(
    private val read: () -> ByteArray?,
    private val write: (ByteArray) -> Unit,
    private val key: (existingVault: Boolean) -> SecretKey,
    private val readLegacy: () -> JSONObject?,
    private val clearLegacy: () -> Unit,
) {
    @Synchronized fun load(): JSONObject {
        val blob = read()
        if (blob != null) {
            val plain = VaultCipher.decrypt(key(true), blob)
            try {
                val document = decode(plain)
                // Also finish interrupted cleanup, but never consult stale legacy data.
                clearLegacy()
                return document
            } finally { plain.fill(0) }
        }
        val document = readLegacy() ?: JSONObject().put("version", 1)
            .put("panels", JSONArray()).put("activePanel", "")
        save(document)
        clearLegacy() // Only after durable write and successful decrypt/read-back.
        return document
    }

    @Synchronized fun save(document: JSONObject) {
        val plaintext = document.toString().toByteArray(Charsets.UTF_8)
        try {
            decode(plaintext)
            val vaultKey = key(read() != null)
            write(VaultCipher.encrypt(vaultKey, plaintext))
            val verified = VaultCipher.decrypt(vaultKey,
                read() ?: throw GeneralSecurityException("Credential vault write was not durable"))
            try {
                check(java.security.MessageDigest.isEqual(plaintext, verified)) { "Credential vault verification failed" }
            } finally { verified.fill(0) }
        } finally { plaintext.fill(0) }
    }

    private fun decode(bytes: ByteArray): JSONObject {
        require(bytes.size <= VaultCipher.MAX_PLAINTEXT)
        val document = JSONObject(bytes.toString(Charsets.UTF_8))
        require(document.getInt("version") == 1)
        require(document.get("activePanel") is String)
        val profiles = document.getJSONArray("panels")
        val ids = mutableSetOf<String>()
        for (i in 0 until profiles.length()) {
            val profile = profiles.getJSONObject(i)
            listOf("id", "name", "url", "token").forEach { require(profile.get(it) is String) }
            require(profile.getString("id").isNotBlank() && ids.add(profile.getString("id")))
        }
        return document
    }
}
