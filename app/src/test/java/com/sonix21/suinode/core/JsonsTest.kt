package com.sonix21.suinode.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonsTest {
    @Test fun wrapperPreservesUnknownFieldsAndRemovesOptionalValues() {
        val raw = JSONObject("""{"type":"future-protocol","unknown":{"x":1},"port":"443"}""")
        val j = J(raw)
        assertEquals(443, j.long("port"))
        j.setStr("optional", "")
        j.setBool("flag", false, onlyTrue = true)
        assertFalse(raw.has("optional"))
        assertFalse(raw.has("flag"))
        assertEquals(1, raw.getJSONObject("unknown").getInt("x"))
    }

    @Test fun mergeIsRecursiveAndDoesNotDiscardSiblings() {
        val base = JSONObject("""{"tls":{"enabled":false,"future":7},"tag":"x"}""")
        base.mergeFrom(JSONObject("""{"tls":{"enabled":true}}"""))
        assertTrue(base.getJSONObject("tls").getBoolean("enabled"))
        assertEquals(7, base.getJSONObject("tls").getInt("future"))
        assertEquals("x", base.getString("tag"))
    }
}
