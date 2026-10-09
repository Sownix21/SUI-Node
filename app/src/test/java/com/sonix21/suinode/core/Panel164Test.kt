package com.sonix21.suinode.core

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class Panel164Test {
    @Test fun sourceIpRevealKeepsAnEmptyArrayUntilTheUserTypes() {
        val rule = jo("action" to "route", "server" to "dns-1", "future" to "keep")
        Panel164.sourceIpMode(rule, "source_ip_cidr")
        assertEquals(0, rule.getJSONArray("source_ip_cidr").length())
        rule.put("source_ip_cidr", jarr(listOf("10.0.0.0/8", "fd00::/8")))
        Panel164.sourceIpMode(rule, "source_ip_cidr")
        assertEquals(2, rule.getJSONArray("source_ip_cidr").length())
        assertEquals("keep", rule.getString("future"))
    }

    @Test fun sourceIpModesUseArrayAndBooleanAndClearOnlyTheirOwnFields() {
        val rule = jo("source_ip_cidr" to jarr(listOf("10.0.0.0/8")), "ip_cidr" to jarr(listOf("1.1.1.1/32")))
        Panel164.sourceIpMode(rule, "source_ip_is_private")
        assertFalse(rule.has("source_ip_cidr"))
        assertEquals(false, rule.get("source_ip_is_private"))
        rule.put("source_ip_is_private", true)
        Panel164.sourceIpMode(rule, "source_ip_is_private")
        assertTrue(rule.getBoolean("source_ip_is_private"))
        Panel164.sourceIpMode(rule, null)
        assertFalse(rule.has("source_ip_is_private"))
        assertEquals("1.1.1.1/32", rule.getJSONArray("ip_cidr").getString(0))
    }

    @Test fun invalidSourceModeDoesNotMutateTheRule() {
        val rule = jo("source_ip_is_private" to true)
        assertTrue(runCatching { Panel164.sourceIpMode(rule, "other") }.isFailure)
        assertTrue(rule.getBoolean("source_ip_is_private"))
    }

    @Test fun repeatedRuleMovesPreserveOrderAndDetachedNestedFields() {
        val rules = jarr(listOf(jo("tag" to "a"), jo("tag" to "b", "future" to jo("keep" to true)), jo("tag" to "c")))
        val moved = Panel164.moveRule(rules, 0, 2)
        assertEquals(listOf("b", "c", "a"), moved.objList().map { it.getString("tag") })
        val again = Panel164.moveRule(moved, 1, 0)
        assertEquals(listOf("c", "b", "a"), again.objList().map { it.getString("tag") })
        again.getJSONObject(1).getJSONObject("future").put("keep", false)
        assertTrue(rules.getJSONObject(1).getJSONObject("future").getBoolean("keep"))
        assertEquals("a", rules.getJSONObject(0).getString("tag"))
        assertTrue(runCatching { Panel164.moveRule(JSONArray(), 0, 1) }.isFailure)
    }

    @Test fun unlimitedClientsSortByUsedTrafficIndependentlyOfQuota() {
        val clients = listOf(jo("id" to 1, "volume" to 0, "up" to 100, "down" to 50),
            jo("id" to 2, "volume" to 0, "up" to 1, "down" to 2))
        assertEquals(listOf(2L, 1L), ClientOrdering.sort(clients, "used", false, emptySet(), emptyMap()).map { it.getLong("id") })
        assertEquals(listOf(1L, 2L), ClientOrdering.sort(clients, "used", true, emptySet(), emptyMap()).map { it.getLong("id") })
    }
}
