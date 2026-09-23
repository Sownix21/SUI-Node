package com.sonix21.suinode.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubscriptionConfigTest {
    @Test fun yamlRoundTripPreservesTypesAndCustomFields() {
        val raw = "mixed-port: 7890\nallow-lan: false\ncustom:\n  token: '00123'\n  enabled: true\nrules:\n  - MATCH,Proxy\n"
        val o = SubscriptionConfig.parse(raw, true)
        assertEquals(7890, o.getInt("mixed-port"))
        assertFalse(o.getBoolean("allow-lan"))
        SubscriptionConfig.clashOption(o, "log-level", true)
        val decoded = SubscriptionConfig.parse(SubscriptionConfig.encode(o, true), true)
        assertEquals("00123", decoded.getJSONObject("custom").getString("token"))
        assertTrue(decoded.getJSONObject("custom").getBoolean("enabled"))
        assertEquals("info", decoded.getString("log-level"))
    }

    @Test fun invalidYamlIsRejectedNotReplacedByEmptyMapping() {
        listOf("- one\n- two", "mixed-port: [", "a: 1\na: 2", "x: !!java/object 'bad'",
            "x: &x {self: *x}").forEach { raw ->
            assertTrue(raw, runCatching { SubscriptionConfig.parse(raw, true) }.isFailure)
        }
        val anchored = SubscriptionConfig.parse("x: &x [1, 2]\ny: *x", true)
        assertEquals(anchored.getJSONArray("x").toString(), anchored.getJSONArray("y").toString())
    }

    @Test fun settingsKeepExtensionsAsStringsAndValidateBothFormats() {
        val settings = jo("subJsonExt" to """{"final":"proxy"}""", "subClashExt" to "mixed-port: 7890\n")
        SubscriptionConfig.validateSettings(settings)
        assertTrue(settings.get("subJsonExt") is String)
        assertTrue(settings.get("subClashExt") is String)
        settings.put("subJsonExt", "[]")
        assertTrue(runCatching { SubscriptionConfig.validateSettings(settings) }.isFailure)
        assertEquals("", SubscriptionConfig.encode(JSONObject(), true))
        assertEquals("", SubscriptionConfig.encode(JSONObject(), false))
    }

    @Test fun dnsToggleAddsSingleHijackAndPreservesOtherRules() {
        val o = JSONObject("""{"rules":[{"domain":["example.org"],"outbound":"custom"}],"extension":42}""")
        repeat(2) { SubscriptionConfig.jsonOption(o, "dns", true) }
        assertEquals(1, o.getJSONArray("rules").objList().count { it.optString("action") == "hijack-dns" })
        SubscriptionConfig.jsonOption(o, "dns", false)
        assertFalse(o.has("dns"))
        assertEquals(1, o.getJSONArray("rules").length())
        assertEquals(42, o.getInt("extension"))
    }

    @Test fun routingPresetsPreserveCustomRulesAndCatalogOverrides() {
        val o = JSONObject("""{"rules":[{"domain":["a.test"],"outbound":"special"}],
          "rule_set":[{"tag":"custom","type":"local","path":"/x.srs"},
          {"tag":"geoip-ir","type":"remote","url":"https://custom.example/ir.srs"}]}""")
        SubscriptionConfig.setRouteTags(o, "direct", setOf("geoip-ir", "custom"))
        assertEquals(setOf("geoip-ir", "custom"), SubscriptionConfig.routeTags(o, "direct"))
        assertEquals("https://custom.example/ir.srs", o.getJSONArray("rule_set").getJSONObject(0).getString("url"))
        SubscriptionConfig.defaults(o, setOf("0", "1"))
        assertEquals(setOf("0", "1"), SubscriptionConfig.selectedDefaults(o))
        assertTrue(o.getJSONArray("rules").objList().any { it.has("domain") })
        SubscriptionConfig.setRouteTags(o, "direct", emptySet())
        assertEquals("custom", o.getJSONArray("rule_set").getJSONObject(0).getString("tag"))
    }

    @Test fun clashRulesRetainCustomEntriesAndFallback() {
        val o = JSONObject("""{"rules":["DOMAIN,a.test,DIRECT","GEOIP,Private,DIRECT","MATCH,Proxy"],"x":true}""")
        SubscriptionConfig.setClashRules(o, setOf("GEOIP,CN,REJECT"))
        assertEquals(listOf("GEOIP,CN,REJECT", "DOMAIN,a.test,DIRECT", "MATCH,Proxy"), o.getJSONArray("rules").strList())
        SubscriptionConfig.clashOption(o, "mixed-port", true)
        SubscriptionConfig.clashOption(o, "mixed-port", false)
        assertFalse(o.has("allow-lan"))
        assertTrue(o.getBoolean("x"))
    }

    @Test fun dnsRuleSetsAndDefaultBlocksMatchFrontend() {
        val o = JSONObject()
        SubscriptionConfig.jsonOption(o, "dns", true)
        SubscriptionConfig.setRouteTags(o, "direct", setOf("geosite-ir"), dns = true)
        assertEquals("geosite-ir", o.getJSONArray("rule_set").getJSONObject(0).getString("tag"))
        assertEquals(setOf("geosite-ir"), SubscriptionConfig.routeTags(o, "direct", dns = true))
        SubscriptionConfig.jsonOption(o, "inbounds", true)
        assertEquals(9000, o.getJSONArray("inbounds").getJSONObject(0).getInt("mtu"))
        SubscriptionConfig.jsonOption(o, "experimental", true)
        assertTrue(o.getJSONObject("experimental").getJSONObject("cache_file").getBoolean("enabled"))
    }
}
