package com.sonix21.suinode.core

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned authenticated envelope; keys and nonces are never derived from credentials. */
object VaultCipher {
    const val MAX_PLAINTEXT = 1024 * 1024
    private val header = byteArrayOf(0x53, 0x55, 0x49, 0x4e, 0x01)
    private val context = "com.sonix21.suinode/panel-vault/v1".toByteArray(Charsets.UTF_8)
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 16
    const val MAX_ENVELOPE = MAX_PLAINTEXT + 5 + IV_SIZE + TAG_SIZE

    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT) { "Credential vault exceeds the size limit" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key) // Provider generates a fresh random nonce.
        check(cipher.iv.size == IV_SIZE)
        cipher.updateAAD(context)
        cipher.updateAAD(header)
        return header + cipher.iv + cipher.doFinal(plaintext)
    }

    fun decrypt(key: SecretKey, envelope: ByteArray): ByteArray {
        if (envelope.size !in (header.size + IV_SIZE + TAG_SIZE)..MAX_ENVELOPE ||
            !envelope.copyOfRange(0, header.size).contentEquals(header)) {
            throw GeneralSecurityException("Invalid credential vault envelope")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key,
            GCMParameterSpec(TAG_SIZE * 8, envelope, header.size, IV_SIZE))
        cipher.updateAAD(context)
        cipher.updateAAD(header)
        return cipher.doFinal(envelope, header.size + IV_SIZE, envelope.size - header.size - IV_SIZE)
    }
}
