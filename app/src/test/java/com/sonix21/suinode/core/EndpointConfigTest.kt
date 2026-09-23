package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test

class EndpointConfigTest {
    @Test fun defaultsCoverAllSixEndpointTypes() {
        assertEquals(6, EndpointConfig.types.size)
        EndpointConfig.types.keys.forEach { type -> assertEquals(type, EndpointConfig.create(type).getString("type")) }
        assertFalse(EndpointConfig.create("openconnect").has("peers"))
        assertEquals(1194, EndpointConfig.create("openvpn-server").getInt("listen_port"))
    }

    @Test fun qrIsOnlyForWireGuardWithPeers() {
        assertFalse(EndpointConfig.canOfferQr(EndpointConfig.create("warp")))
        val wg = EndpointConfig.create("wireguard")
        assertFalse(EndpointConfig.canOfferQr(wg))
        wg.getJSONArray("peers").put(jo("public_key" to "peer"))
        assertTrue(EndpointConfig.canOfferQr(wg))
        assertNull(WireGuardShare.peerConfig(EndpointConfig.create("warp"), 0, "test"))
    }

    @Test fun staticKeyModeChangesGcmToCbcAndPreservesOtherFields() {
        val o = EndpointConfig.create("openvpn-client").put("cipher", "AES-128-GCM").put("future", "keep")
        EndpointConfig.setMode(o, "static_key")
        assertEquals("AES-256-CBC", o.getString("cipher"))
        assertEquals("keep", o.getString("future"))
        EndpointConfig.setMode(o, "tls")
        assertEquals("tls", o.getString("mode"))
    }

    @Test fun validatesOnlyRelevantRequiredFields() {
        val o = EndpointConfig.create("openvpn-client")
        assertNotNull(EndpointConfig.validate(o))
        o.put("server", "vpn.example")
        assertNull(EndpointConfig.validate(o))
        EndpointConfig.setMode(o, "static_key")
        assertNotNull(EndpointConfig.validate(o))
        o.put("address", jarr(listOf("10.8.0.2/24"))).put("static_key_path", "/etc/openvpn/static.key").put("peer_address", "10.8.0.1")
        assertNull(EndpointConfig.validate(o))
        o.put("server_port", 65536)
        assertNotNull(EndpointConfig.validate(o))
    }

    @Test fun endpointUsesOwnTlsAndPrunesOnlyEmptyTlsFields() {
        val tls = jo("certificate_authority_path" to "/etc/ca.pem", "insecure" to false,
            "server_name" to "", "peer_fingerprint" to jarr(emptyList<String>()), "custom" to jo("keep" to true))
        val input = EndpointConfig.create("openconnect").put("tls_id", 12).put("tls", tls).put("future", "keep")
        val result = EndpointConfig.payload(input)
        assertFalse(result.has("tls_id"))
        assertTrue(input.has("tls_id"))
        assertFalse(result.getJSONObject("tls").has("server_name"))
        assertFalse(result.getJSONObject("tls").getBoolean("insecure"))
        assertEquals("/etc/ca.pem", result.getJSONObject("tls").getString("certificate_authority_path"))
        assertEquals("keep", result.getString("future"))
        assertTrue(result.getJSONObject("tls").getJSONObject("custom").getBoolean("keep"))
    }
}
