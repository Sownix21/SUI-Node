package com.sonix21.suinode.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoutingDraftTest {
    @Test fun newRuleIsImmediatelyBlankAndUsesWireFormat() {
        val source = JSONObject("""{"route":{"rules":[{"inbound":["existing"],"action":"reject"}]},"future":true}""")
        val draft = RoutingDraft(source, -1, "direct")
        assertEquals(2, draft.config.getJSONObject("route").getJSONArray("rules").length())
        assertEquals(1, source.getJSONObject("route").getJSONArray("rules").length())
        assertEquals("route", draft.rule.getString("action"))
        assertEquals("direct", draft.rule.getString("outbound"))
        listOf("type", "mode", "rules", "inbound").forEach { assertFalse(it, draft.rule.has(it)) }
        assertTrue(draft.config.getBoolean("future"))
    }

    @Test fun emptyConfigAndExistingRuleAreDetached() {
        assertEquals("direct", RoutingDraft(JSONObject(), -1, "direct").rule.getString("outbound"))
        val source = JSONObject("""{"route":{"rules":[{"inbound":["one"],"future_option":42}]}}""")
        val draft = RoutingDraft(source, 0, "direct")
        draft.rule.put("future_option", 43)
        assertEquals(42, source.getJSONObject("route").getJSONArray("rules").getJSONObject(0).getInt("future_option"))
        assertTrue(runCatching { RoutingDraft(source, 9, "direct") }.isFailure)
    }

    @Test fun logicalRoundTripPreservesConditionsAndAction() {
        val draft = RoutingDraft(JSONObject(), -1, "direct")
        draft.rule.put("inbound", jarr(listOf("one")))
        draft.rule.put("future_condition", true)
        draft.setLogical(true)
        assertFalse(draft.rule.has("inbound"))
        draft.rule.put("mode", "or")
        draft.rule.getJSONArray("rules").put(jo("auth_user" to jarr(listOf("client"))))
        draft.setLogical(false)
        assertEquals("one", draft.rule.getJSONArray("inbound").getString(0))
        assertTrue(draft.rule.getBoolean("future_condition"))
        assertFalse(draft.rule.has("type"))
        draft.rule.put("inbound", jarr(listOf("two")))
        draft.setLogical(true)
        assertEquals("or", draft.rule.getString("mode"))
        val rules = draft.rule.getJSONArray("rules")
        assertEquals("two", rules.getJSONObject(0).getJSONArray("inbound").getString(0))
        assertEquals("client", rules.getJSONObject(1).getJSONArray("auth_user").getString(0))
        assertEquals("direct", draft.rule.getString("outbound"))
    }
}
