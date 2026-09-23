package com.sonix21.suinode.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.AtomicFile
import com.sonix21.suinode.core.VaultCipher
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

internal class KeystoreVault(private val context: Context,
    private val alias: String = "sui_node_panel_vault_v1", filename: String = "panel-vault-v1.bin") {
    private val file = AtomicFile(File(context.noBackupFilesDir, filename))
    private val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun read(): ByteArray? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        return file.openRead().use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= VaultCipher.MAX_ENVELOPE) {
                val count = it.read(buffer, 0, minOf(buffer.size, VaultCipher.MAX_ENVELOPE + 1 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            val bytes = output.toByteArray()
            if (bytes.size > VaultCipher.MAX_ENVELOPE) throw IOException("Credential vault exceeds the size limit")
            bytes
        }
    }

    fun write(bytes: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    @Synchronized fun key(existingVault: Boolean): SecretKey {
        (keys.getKey(alias, null) as? SecretKey)?.let { return it }
        check(!existingVault) { "The device credential-vault key is unavailable" }
        if (Build.VERSION.SDK_INT >= 28 && context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            try { return generate(strongBox = true) }
            catch (_: StrongBoxUnavailableException) { /* Documented fallback to the platform Keystore. */ }
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= 28) spec.setIsStrongBoxBacked(strongBox)
        // Older Android versions have documented key-loss bugs for this restriction.
        if (Build.VERSION.SDK_INT >= 35) spec.setUnlockedDeviceRequired(true)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(spec.build())
        }.generateKey()
    }

    @Suppress("DEPRECATION")
    fun protection(): String {
        val info = SecretKeyFactory.getInstance("AES", "AndroidKeyStore")
            .getKeySpec(key(true), KeyInfo::class.java) as KeyInfo
        return if (Build.VERSION.SDK_INT >= 31) when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox hardware-backed"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE hardware-backed"
            else -> "Android Keystore (software-backed)"
        } else if (info.isInsideSecureHardware) "Hardware-backed Android Keystore"
        else "Android Keystore (software-backed)"
    }

}
