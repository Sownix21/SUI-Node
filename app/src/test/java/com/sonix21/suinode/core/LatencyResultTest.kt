package com.sonix21.suinode.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LatencyResultTest {
    @Test fun successUsesCoreMillisecondsIncludingZero() {
        assertEquals(125L, LatencyResult.parse(JSONObject("{\"OK\":true,\"Delay\":125,\"Error\":\"\"}")).delayMs)
        assertEquals(0L, LatencyResult.parse(JSONObject("{\"OK\":true,\"Delay\":0}")).delayMs)
    }

    @Test fun errorsAndMalformedResponsesNeverBecomeZeroLatency() {
        val result = LatencyResult.parse(JSONObject("{\"OK\":false,\"Delay\":0,\"Error\":\"outbound not found\"}"))
        assertNull(result.delayMs)
        assertEquals("outbound not found", result.error)
        listOf(null, JSONObject("{\"OK\":true}"), JSONObject("{\"OK\":true,\"Delay\":-1}")).forEach {
            assertNotNull(LatencyResult.parse(it).error)
        }
    }
}
