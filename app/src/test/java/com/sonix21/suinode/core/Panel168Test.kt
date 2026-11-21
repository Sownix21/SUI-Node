package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test

class Panel168Test {
    @Test fun inboundEnableDefaultsAndExplicitFalseMatchPanel() {
        assertTrue(Panel168.inboundEnabled(jo()))
        assertTrue(Panel168.inboundEnabled(jo("enable" to true)))
        assertFalse(Panel168.inboundEnabled(jo("enable" to false)))
    }

    @Test fun switchingFromGeckoRemovesOnlyPacketSizeOptions() {
        val obfs = jo("type" to "gecko", "password" to "secret", "min_packet_size" to 512,
            "max_packet_size" to 1200, "future" to true)
        Panel168.obfuscationType(obfs, "salamander")
        assertFalse(obfs.has("min_packet_size")); assertFalse(obfs.has("max_packet_size"))
        assertEquals("secret", obfs.getString("password")); assertTrue(obfs.getBoolean("future"))
        Panel168.obfuscationType(obfs, "gecko")
        assertFalse(obfs.has("min_packet_size")) // omitted means core default
        assertTrue(runCatching { Panel168.obfuscationType(obfs, "invalid") }.isFailure)
        assertEquals("gecko", obfs.getString("type"))
    }

    private val material = listOf("-----BEGIN ECH KEYS-----", "key-fixture", "-----END ECH KEYS-----",
        "-----BEGIN ECH CONFIGS-----", "config-fixture", "-----END ECH CONFIGS-----")

    @Test fun echGeneratorAtomicallyReplacesPathsAndDnsQueryWithPairedPem() {
        val server = jo("enabled" to true, "key_path" to "/old", "future" to 1)
        val client = jo("query_server_name" to "dns.example", "config_path" to "/old-config", "future" to 2)
        Panel168.applyEch(server, client, material)
        assertFalse(server.has("key_path")); assertFalse(client.has("query_server_name"))
        assertFalse(client.has("config_path")); assertTrue(Panel168.hasEchConfig(client))
        assertEquals(3, server.getJSONArray("key").length())
        assertEquals(1, server.getInt("future")); assertEquals(2, client.getInt("future"))
    }

    @Test fun incompleteOrEmptyEchResponseDoesNotOverwriteEitherHalf() {
        val server = jo("key_path" to "/keep"); val client = jo("query_server_name" to "keep.example")
        val beforeServer = server.toString(); val beforeClient = client.toString()
        for (bad in listOf(material.take(3), material.filterNot { it.endsWith("-fixture") }, emptyList())) {
            assertTrue(runCatching { Panel168.applyEch(server, client, bad) }.isFailure)
            assertEquals(beforeServer, server.toString()); assertEquals(beforeClient, client.toString())
        }
    }

    @Test fun blankEchConfigAllowsDnsQuery() {
        assertFalse(Panel168.hasEchConfig(jo()))
        assertFalse(Panel168.hasEchConfig(jo("config" to jarr(listOf("", "  ")))))
    }

    @Test fun happImportUsesExplicitFormatAndUnencodedSubscriptionUrl() {
        assertEquals("happ://add/https://sub.example/sub/alice?format=happ",
            Panel168.happImport("https://sub.example/sub/", "alice"))
        assertEquals("https://sub.example/sub/alice", Panel168.subscriptionUrl("https://sub.example/sub", "alice"))
        assertEquals("https://sub.example/sub/a%20b%2Fc%3F%23?format=clash",
            Panel168.subscriptionUrl("https://sub.example/sub/", "a b/c?#", "clash"))
    }

    @Test fun lastOnlineUsesUnixSecondsNotExpiryOrMilliseconds() {
        assertEquals(Fmt.dateTime(1795000000L), Panel168.lastOnline(jo("onlineAt" to 1795000000L, "expiry" to 1L)))
        assertEquals(Fmt.dateTime(1795000000L), Panel168.lastOnline(jo("onlineAt" to "1795000000")))
        for (value in listOf(0L, -1L, Long.MAX_VALUE)) assertEquals("Not recorded", Panel168.lastOnline(jo("onlineAt" to value)))
        assertEquals("Not recorded", Panel168.lastOnline(jo()))
    }
}
