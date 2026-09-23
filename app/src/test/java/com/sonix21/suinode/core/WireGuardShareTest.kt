package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test

class WireGuardShareTest {
    private fun endpoint() = jo(
        "type" to "wireguard",
        "private_key" to "SERVER-PRIVATE-NEVER-EXPORT",
        "listen_port" to 51820, "address" to jarr(listOf("10.0.0.1/24")),
        "ext" to jo("public_key" to "SERVER-PUBLIC",
            "keys" to jarr(listOf(jo("public_key" to "CLIENT-PUBLIC", "private_key" to "CLIENT-PRIVATE")))),
        "peers" to jarr(listOf(jo("public_key" to "CLIENT-PUBLIC", "allowed_ips" to jarr(listOf("10.0.0.2/32")),
            "pre_shared_key" to "PSK", "persistent_keepalive_interval" to 25))))

    @Test fun exportUsesClientKeysAndServerEndpointLikePanel() {
        val text = WireGuardShare.peerConfig(endpoint(), 0, "panel.test")!!
        assertTrue(text.contains("PrivateKey = CLIENT-PRIVATE"))
        assertTrue(text.contains("PublicKey = SERVER-PUBLIC"))
        assertTrue(text.contains("Address = 10.0.0.2/32"))
        assertTrue(text.contains("Endpoint = panel.test:51820"))
        assertTrue(text.contains("PresharedKey = PSK"))
        assertTrue(text.contains("PersistentKeepalive = 25"))
        assertFalse(text.contains("SERVER-PRIVATE"))
        assertFalse(text.contains("10.0.0.1/24"))
    }

    @Test fun absentClientKeysNeverFallBackToServerPrivateKey() {
        val e = endpoint()
        e.getJSONObject("ext").remove("keys")
        assertNull(WireGuardShare.peerConfig(e, 0, "panel.test"))
    }

    @Test fun absentServerPublicKeyOrListenPortProducesNoQr() {
        val e = endpoint()
        e.getJSONObject("ext").remove("public_key")
        assertNull(WireGuardShare.peerConfig(e, 0, "panel.test"))
        assertNull(WireGuardShare.peerConfig(endpoint().put("listen_port", 0), 0, "panel.test"))
    }

    @Test fun ipv6EndpointIsBracketed() {
        val e = endpoint()
        e.getJSONObject("ext").put("server", "2001:db8::1")
        assertTrue(WireGuardShare.peerConfig(e, 0, "panel.test")!!.contains("Endpoint = [2001:db8::1]:51820"))
    }
}
