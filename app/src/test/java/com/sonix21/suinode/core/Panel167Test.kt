package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test

class Panel167Test {
    @Test fun realmRemovesOnlyConflictingOutboundFields() {
        val o = jo("server" to "old", "server_port" to 443, "server_ports" to jarr(listOf("1:2")), "hop_interval" to "5s", "future" to true, "tls" to jo("enabled" to true))
        Panel167.realm(o, true, true)
        listOf("server", "server_port", "server_ports", "hop_interval").forEach { assertFalse(o.has(it)) }
        assertEquals(0, o.getJSONObject("realm").getJSONArray("stun_servers").length())
        assertTrue(o.getBoolean("future")); assertTrue(o.has("tls"))
        Panel167.realm(o, false, true)
        assertFalse(o.has("realm")); assertFalse(o.has("server"))
    }
    @Test fun inboundRealmPreservesListenerAndIpv6ClearsMapping() {
        val o = jo("listen_port" to 443)
        Panel167.realm(o, true, false)
        assertEquals(443, o.getInt("listen_port"))
        val realm = o.getJSONObject("realm").put("port_mapping", jo("enabled" to true))
        Panel167.realmIp(realm, 6)
        assertFalse(realm.has("port_mapping")); assertEquals(6, realm.getInt("ip_version"))
        Panel167.realmIp(realm, 0); assertFalse(realm.has("ip_version"))
    }
    @Test fun generatedClientPairPreservesCaAndRejectsMalformedReplyAtomically() {
        val tls = jo("certificate_authority_path" to "/ca.pem", "client_key_password" to "old", "client_key_path" to "/old", "future" to true)
        val before = tls.toString()
        assertTrue(runCatching { Panel167.clientCertificate(tls, listOf("failed")) }.isFailure)
        assertEquals(before, tls.toString())
        Panel167.clientCertificate(tls, listOf("-----BEGIN PRIVATE KEY-----", "key", "-----END PRIVATE KEY-----", "-----BEGIN CERTIFICATE-----", "cert", "-----END CERTIFICATE-----"))
        assertFalse(tls.has("client_key_path")); assertFalse(tls.has("client_key_password"))
        assertEquals("/ca.pem", tls.getString("certificate_authority_path"))
        assertTrue(tls.getBoolean("future")); assertEquals(3, tls.getJSONArray("client_certificate").length())
    }
    @Test fun staticModeAcceptsPanelCiphersAndRemovesInvalidAeadOrStreamChoice() {
        for (cipher in listOf("CHACHA20-POLY1305", "AES-256-CFB", "AES-128-GCM")) {
            val o = jo("cipher" to cipher)
            EndpointConfig.setMode(o, "static_key")
            assertEquals("AES-256-CBC", o.getString("cipher"))
        }
        val o = jo("cipher" to "NONE")
        EndpointConfig.setMode(o, "static_key"); assertEquals("NONE", o.getString("cipher"))
    }
    @Test fun realmServiceHasListenerTlsAndUsers() {
        val o = ServiceConfig.create("hysteria-realm")
        assertEquals(8443, o.getInt("listen_port"))
        assertTrue("hysteria-realm" in ServiceConfig.tlsTypes)
        assertEquals(0, o.getJSONArray("users").length())
    }
}
