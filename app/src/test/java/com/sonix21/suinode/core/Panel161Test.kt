package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test

class Panel161Test {
    @Test fun maintenanceSuppressesOnlyIntentionalCoreStops() {
        assertFalse(Panel161.coreStoppedUnexpectedly(jo("running" to false, "maintenance" to true)))
        assertTrue(Panel161.coreStoppedUnexpectedly(jo("running" to false, "maintenance" to false)))
        assertTrue(Panel161.coreStoppedUnexpectedly(jo("running" to false))) // Older panels.
        assertFalse(Panel161.coreStoppedUnexpectedly(jo())) // Unknown is not stopped.
    }
    @Test fun settingsOmitProtectedFieldsWithoutChangingUserDraft() {
        val draft = jo("subURI" to "https://vpn.example/sub/", "maintenance" to "true", "secret" to "private", "config" to "{}", "version" to "1.6.1", "globalResetLast" to "3")
        val sent = Panel161.settingsPayload(draft)
        assertEquals(setOf("subURI"), sent.keys().asSequence().toSet())
        assertTrue(draft.has("secret"))
    }
    @Test fun spoofMethodFollowsHostAndCannotCoexistWithReality() {
        val tls = jo("future" to "preserved")
        Panel161.setSpoof(tls, "example.org")
        assertEquals("wrong-sequence", tls.getString("spoof_method"))
        tls.put("spoof_method", "wrong-checksum")
        Panel161.setSpoof(tls, "other.example")
        assertEquals("wrong-checksum", tls.getString("spoof_method"))
        Panel161.setSpoof(tls, "")
        assertFalse(tls.has("spoof_method")); assertTrue(tls.has("spoof"))
        Panel161.setSpoof(tls, null)
        assertFalse(tls.has("spoof")); assertEquals("preserved", tls.getString("future"))
        tls.put("reality", jo())
        assertTrue(runCatching { Panel161.setSpoof(tls, "example.org") }.isFailure)
    }
    @Test fun openVpnCipherSetsAreMutuallyExclusive() {
        val endpoint = EndpointConfig.create("openvpn-client").put("data_ciphers", jarr(listOf("AES-256-GCM"))).put("data_ciphers_fallback", "AES-128-GCM")
        EndpointConfig.setMode(endpoint, "static_key")
        assertFalse(endpoint.has("data_ciphers")); assertFalse(endpoint.has("data_ciphers_fallback"))
        assertEquals("AES-256-CBC", endpoint.getString("cipher"))
        EndpointConfig.setMode(endpoint, "tls")
        assertFalse(endpoint.has("cipher"))
    }
    @Test fun staticPayloadExcludesTlsAndKeepsDirectionAsRoleString() {
        val endpoint = EndpointConfig.create("openvpn-server").put("key_direction", "server")
        EndpointConfig.setMode(endpoint, "static_key")
        val sent = EndpointConfig.payload(endpoint)
        assertFalse(sent.has("tls")); assertTrue(endpoint.has("tls"))
        assertEquals("server", sent.getString("key_direction"))
    }
    @Test fun certificateAndKeyBlocksAreSeparatedAndErrorsRejected() {
        val pair = listOf("-----BEGIN PRIVATE KEY-----", "private", "-----END PRIVATE KEY-----", "", "-----BEGIN CERTIFICATE-----", "public", "-----END CERTIFICATE-----")
        assertEquals(listOf("-----BEGIN CERTIFICATE-----", "public", "-----END CERTIFICATE-----"), Panel161.pemBlock(pair, "CERTIFICATE"))
        assertEquals(3, Panel161.pemBlock(pair, "PRIVATE KEY").size)
        assertTrue(runCatching { Panel161.pemBlock(listOf("Failed to generate keypair"), "CERTIFICATE") }.isFailure)
    }
    @Test fun openVpnKeyResponseRetainsItsOwnMarkers() {
        val key = listOf("#", "-----BEGIN OpenVPN Static key V1-----", "abcd", "-----END OpenVPN Static key V1-----")
        assertEquals(key.drop(1), Panel161.pemBlock(key, "OpenVPN Static key V1"))
        assertTrue(runCatching { Panel161.pemBlock(key.dropLast(1), "OpenVPN Static key V1") }.isFailure)
    }
}
