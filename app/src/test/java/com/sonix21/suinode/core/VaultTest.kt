package com.sonix21.suinode.core

import com.sonix21.suinode.data.Panel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class VaultTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun document() = JSONObject("""{"version":1,"panels":[{"id":"a","name":"Private","url":"https://example.test/","token":"secret-token"}],"activePanel":"a"}""")

    @Test fun authenticatedRoundTripUsesFreshNonces() {
        val key = key()
        val plain = document().toString().toByteArray()
        val first = VaultCipher.encrypt(key, plain)
        val second = VaultCipher.encrypt(key, plain)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, VaultCipher.decrypt(key, first))
        assertFalse(first.toString(Charsets.UTF_8).contains("secret-token"))
    }

    @Test fun tamperingTruncationAndWrongKeysAreRejected() {
        val key = key()
        val blob = VaultCipher.encrypt(key, document().toString().toByteArray())
        for (index in listOf(0, 4, 7, blob.lastIndex)) {
            val changed = blob.clone().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertTrue(runCatching { VaultCipher.decrypt(key, changed) }.isFailure)
        }
        assertTrue(runCatching { VaultCipher.decrypt(key(), blob) }.isFailure)
        assertTrue(runCatching { VaultCipher.decrypt(key, blob.copyOf(12)) }.isFailure)
        assertTrue(runCatching { VaultCipher.encrypt(key, ByteArray(VaultCipher.MAX_PLAINTEXT + 1)) }.isFailure)
    }

    @Test fun migrationVerifiesBeforeDeletingLegacyData() {
        val key = key()
        var blob: ByteArray? = null
        var cleared = false
        val repository = VaultRepository({ blob }, { blob = it }, { key }, { document() }, {
            assertNotNull(blob)
            assertTrue(VaultCipher.decrypt(key, blob!!).toString(Charsets.UTF_8).contains("secret-token"))
            cleared = true
        })
        assertEquals("a", repository.load().getString("activePanel"))
        assertTrue(cleared)
        assertEquals(1, repository.load().getJSONArray("panels").length())
    }

    @Test fun failedWriteKeepsLegacyDataAndDoesNotResetIt() {
        var cleared = false
        val repository = VaultRepository({ null }, { throw java.io.IOException("write failed") },
            { key() }, { document() }, { cleared = true })
        assertTrue(runCatching { repository.load() }.isFailure)
        assertFalse(cleared)
    }

    @Test fun corruptedVaultNeverFallsBackToLegacyOrWritesOverIt() {
        var readLegacy = false
        var wrote = false
        val repository = VaultRepository({ byteArrayOf(1, 2, 3) }, { wrote = true },
            { existing -> assertTrue(existing); key() }, { readLegacy = true; document() }, {})
        assertTrue(runCatching { repository.load() }.isFailure)
        assertFalse(readLegacy)
        assertFalse(wrote)
    }

    @Test fun malformedProfileDoesNotSilentlyDisappear() {
        var cleared = false
        val bad = document().also { it.getJSONArray("panels").getJSONObject(0).remove("token") }
        val repository = VaultRepository({ null }, { fail("Must not write malformed data") },
            { key() }, { bad }, { cleared = true })
        assertTrue(runCatching { repository.load() }.isFailure)
        assertFalse(cleared)
    }

    @Test fun profileDiagnosticStringDoesNotExposeMetadataOrCredentials() {
        val p = Panel("id", "private-name", "https://private.example/", token = "secret", username = "user", password = "pass")
        assertEquals("Panel([redacted])", p.toString())
        assertFalse(p.toJson().has("password"))
        assertFalse(p.toJson().has("username"))
    }
}
