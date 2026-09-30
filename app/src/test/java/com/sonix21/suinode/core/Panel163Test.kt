package com.sonix21.suinode.core

import com.sonix21.suinode.ui.screens.clients.randomConfigs
import com.sonix21.suinode.ui.screens.clients.shuffleKey
import com.sonix21.suinode.ui.screens.clients.updateConfigs
import com.sonix21.suinode.ui.screens.dns.DNS_TYPES
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class Panel163Test {
    @Test fun mutualTlsPathsAndTextUseDifferentWireTypes() {
        val server = jo("client_authentication" to "require-and-verify", "certificate_provider" to "acme-main")
        val client = jo("future_tls_option" to true)
        Panel163.mutualSource(server, client, false)
        assertTrue(server.get("client_certificate_path") is JSONArray)
        assertTrue(client.get("client_certificate_path") is String)
        assertTrue(client.get("client_key_path") is String)
        Panel163.mutualSource(server, client, true)
        assertFalse(server.has("client_certificate_path")); assertFalse(client.has("client_key_path"))
        assertTrue(server.get("client_certificate") is JSONArray)
        assertTrue(client.get("client_certificate") is JSONArray); assertTrue(client.get("client_key") is JSONArray)
        Panel163.disableMutual(server, client)
        assertFalse(Panel163.serverMutualKeys.any(server::has)); assertFalse(Panel163.clientMutualKeys.any(client::has))
        assertEquals("acme-main", server.getString("certificate_provider")); assertTrue(client.getBoolean("future_tls_option"))
    }
    @Test fun outboundOnlyMutualTlsDoesNotRequireServerBlock() {
        val client = jo()
        Panel163.mutualSource(null, client, true)
        assertTrue(client.has("client_key"))
        Panel163.disableMutual(null, client)
        assertEquals(0, client.length())
    }
    @Test fun httpVersionSwitchDropsOnlyInapplicableOptions() {
        val o = jo("version" to 3, "initial_packet_size" to 1400, "disable_path_mtu_discovery" to true,
            "idle_timeout" to "30s", "stream_receive_window" to "8mb", "disable_version_fallback" to true,
            "future_option" to "keep", "tls" to jo("server_name" to "example.test"))
        Panel163.httpVersion(o, 2)
        assertFalse(o.has("initial_packet_size")); assertFalse(o.has("disable_path_mtu_discovery"))
        assertEquals("8mb", o.getString("stream_receive_window"))
        Panel163.httpVersion(o, 1)
        assertFalse(o.has("idle_timeout")); assertFalse(o.has("stream_receive_window"))
        Panel163.httpVersion(o, 0)
        assertFalse(o.has("disable_version_fallback"))
        assertEquals("keep", o.getString("future_option")); assertTrue(o.has("tls"))
    }
    @Test fun fragmentDisableClearsDependentFieldsButKeepsOtherTlsOptions() {
        val o = jo("record_fragment" to true, "fragment_fallback_delay" to "500ms", "server_name" to "example.test")
        Panel163.fragment(o, true); assertTrue(o.getBoolean("fragment"))
        Panel163.fragment(o, false)
        assertFalse(o.has("fragment")); assertFalse(o.has("record_fragment")); assertFalse(o.has("fragment_fallback_delay"))
        assertEquals("example.test", o.getString("server_name"))
    }
    @Test fun snellCredentialsHaveIndependentUserKeyAndSurviveRename() {
        val config = randomConfigs("alice")
        val before = config.getJSONObject("snell").getString("userkey")
        assertEquals(32, before.length)
        shuffleKey(J(config), "snell")
        assertNotEquals(before, config.getJSONObject("snell").getString("userkey"))
        val renamed = updateConfigs(config, "bob")
        assertEquals("bob", renamed.getJSONObject("snell").getString("name"))
        assertEquals(config.getJSONObject("snell").getString("userkey"), renamed.getJSONObject("snell").getString("userkey"))
    }
    @Test fun sessionSnapshotsValidateCountersAndAllowOmittedMetadata() {
        assertEquals(1, LiveSessions.parse(jarr(listOf(jo("id" to "x", "createdAt" to 1, "up" to 0, "down" to 0)))).size)
        assertTrue(LiveSessions.parse(JSONArray()).isEmpty())
        assertTrue(runCatching { LiveSessions.parse(jarr(listOf(jo("id" to "x", "createdAt" to 1, "up" to -1, "down" to 0)))) }.isFailure)
        assertTrue(runCatching { LiveSessions.parse(jarr(listOf("invalid"))) }.isFailure)
    }
    @Test fun newFrontendSelectorsUseExpectedWireValues() {
        assertTrue("mdns" in DNS_TYPES)
        assertEquals(listOf("no", "request", "require-any", "verify-if-given", "require-and-verify"), Panel163.clientAuth)
    }
}
