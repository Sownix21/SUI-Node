package com.sonix21.suinode.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class MonitoringTest {
    private val now = 1_800_000_000L
    private val config = MonitorConfig(enabled = true)
    private fun client() = jo("id" to 1, "name" to "Client", "enable" to true, "expiry" to now + 3600,
        "volume" to 1000, "up" to 500, "down" to 450)
    private fun alert(severity: Int = 1) = AlertCandidate("p:quota:1", "p", "quota", "Quota", "Private name", "1000", severity)

    @Test fun expiryAndQuotaUseUnixSecondsAndCurrentCounters() {
        val c = client().put("totalUp", Long.MAX_VALUE)
        val alerts = ClientHealth.alerts("p", "Panel", jarr(listOf(c)), now, config)
        assertEquals(setOf("expiry", "quota"), alerts.map { it.kind }.toSet())
        assertTrue(alerts.all { it.severity == 1 })
        c.put("expiry", now - 1).put("down", 500)
        assertTrue(ClientHealth.alerts("p", "Panel", jarr(listOf(c)), now, config).all { it.severity == 2 })
    }
    @Test fun unlimitedAndFirstUseClientsDoNotGenerateFalseAlerts() {
        val c = client().put("volume", 0).put("delayStart", true).put("expiry", now - 100)
        assertTrue(ClientHealth.alerts("p", "Panel", jarr(listOf(c)), now, config).isEmpty())
        c.put("delayStart", false).put("expiry", 0)
        assertTrue(ClientHealth.alerts("p", "Panel", jarr(listOf(c)), now, config).isEmpty())
    }
    @Test fun disabledClientsCanBeIncludedAndCountersCannotOverflow() {
        val c = client().put("enable", false).put("up", Long.MAX_VALUE).put("down", 99)
        assertEquals(Long.MAX_VALUE, ClientHealth.usage(c))
        assertTrue(ClientHealth.alerts("p", "Panel", jarr(listOf(c)), now, config.copy(includeDisabled = false)).isEmpty())
        assertFalse(ClientHealth.alerts("p", "Panel", jarr(listOf(c)), now, config).isEmpty())
    }
    @Test fun repeatSuppressionEscalationAndRecovery() {
        val state = JSONObject()
        assertEquals(1, AlertEngine.update(state, "p", listOf(alert()), setOf("quota"), now, config, true).size)
        assertTrue(AlertEngine.update(state, "p", listOf(alert()), setOf("quota"), now + 60, config, true).isEmpty())
        assertEquals(1, AlertEngine.update(state, "p", listOf(alert(2)), setOf("quota"), now + 120, config, true).size)
        assertEquals(1, AlertEngine.update(state, "p", listOf(alert(2)), setOf("quota"), now + 86400 + 120, config, true).size)
        assertEquals(1, AlertEngine.update(state, "p", emptyList(), setOf("quota"), now + 86550, config, true).size)
        assertEquals(0, state.getJSONObject("active").length())
    }
    @Test fun quietOrDeniedNotificationsRemainPendingAndUnknownDataDoesNotResolve() {
        val state = JSONObject()
        assertTrue(AlertEngine.update(state, "p", listOf(alert()), setOf("quota"), now, config, false).isEmpty())
        AlertEngine.update(state, "p", emptyList(), setOf("offline"), now + 60, config, true)
        assertEquals(1, state.getJSONObject("active").length())
        assertEquals(1, AlertEngine.update(state, "p", listOf(alert()), setOf("quota"), now + 120, config, true).size)
    }
    @Test fun quietHoursSupportOvernightAndLocalTime() {
        val c = config.copy(quiet = true, quietStart = 22, quietEnd = 8)
        fun at(hour: Int) = java.time.LocalDate.of(2026, 9, 10).atTime(hour, 0).toEpochSecond(java.time.ZoneOffset.UTC)
        assertTrue(c.isQuiet(at(23), ZoneId.of("UTC")))
        assertTrue(c.isQuiet(at(7), ZoneId.of("UTC")))
        assertFalse(c.isQuiet(at(8), ZoneId.of("UTC")))
        assertFalse(c.copy(quietStart = 8).isQuiet(at(8), ZoneId.of("UTC")))
    }
    @Test fun configRoundTripAndBounds() {
        assertEquals(config, MonitorConfig.fromJson(config.toJson()))
        val c = MonitorConfig.fromJson(jo("intervalMinutes" to 1, "quotaPercent" to 500, "failedChecks" to -1))
        assertEquals(15, c.intervalMinutes)
        assertEquals(100, c.quotaPercent)
        assertEquals(1, c.failedChecks)
    }
    @Test fun removedPanelsDoNotRemainActiveAndOldHistoryIsPruned() {
        val state = JSONObject()
        AlertEngine.update(state, "p", listOf(alert()), setOf("quota"), now, config, true)
        state.put("checks", jo("p" to jo("time" to now)))
        AlertEngine.pruneScope(state, emptySet(), now + 31 * 86400, config)
        assertEquals(0, state.getJSONObject("active").length())
        assertEquals(0, state.getJSONObject("checks").length())
        assertEquals(0, state.getJSONArray("history").length())
    }
}
