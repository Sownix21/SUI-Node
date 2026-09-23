package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class NewManagementFeaturesTest {
    private fun time(s: String) = Instant.parse(s).epochSecond
    @Test fun clientSortingSupportsStatusDatesInboundsAndStableTies() {
        val a = jo("id" to 1, "name" to "Z", "expiry" to 0, "createdAt" to 20, "enable" to false, "inbounds" to jarr(listOf(2)))
        val b = jo("id" to 2, "name" to "A", "expiry" to 100, "createdAt" to 10, "enable" to true, "inbounds" to jarr(listOf(1)))
        val rows = listOf(a,b)
        fun first(key: String, desc: Boolean = false) = ClientOrdering.sort(rows, key, desc, setOf("A"), mapOf(1L to "Alpha", 2L to "Zulu")).first().getLong("id")
        assertEquals(2L, first("name")); assertEquals(2L, first("expiry"))
        assertEquals(2L, first("online", true)); assertEquals(2L, first("enable", true))
        assertEquals(2L, first("inbounds")); assertEquals(1L, first("createdAt", true))
    }
    @Test fun rejectedTokenMessageDoesNotClaimToDistinguishExpiry() {
        assertEquals(ApiTokenError.MESSAGE, ApiTokenError.explain("invalid token"))
        assertEquals(ApiTokenError.MESSAGE, ApiTokenError.explain("token expired"))
        assertEquals("timeout", ApiTokenError.explain("timeout"))
    }
    @Test fun billingCyclesClampDayAndRespectTimezone() {
        val config = VpsQuotaConfig(resetDay = 31)
        assertEquals(time("2026-02-28T00:00:00Z"), VpsQuota.cycleStart(time("2026-03-01T12:00:00Z"), config))
        assertEquals(time("2026-01-31T00:00:00Z"), VpsQuota.cycleStart(time("2026-02-27T12:00:00Z"), config))
        assertEquals(time("2026-08-31T20:30:00Z"), VpsQuota.cycleStart(time("2026-09-01T01:00:00Z"), config.copy(resetDay = 1, zone = "Asia/Tehran")))
    }
    @Test fun quotaUsesDeltasAndNeverCountsPreMonitoringLifetimeTraffic() {
        val config = VpsQuotaConfig(enabled = true, initialReceive = 100, initialSend = 200)
        val now = time("2026-09-10T12:00:00Z")
        val first = VpsQuota.sample(null, config, now, 10000, 20000, now - 1000)
        assertEquals(100, first.getLong("usedReceive"))
        val second = VpsQuota.sample(first, config, now + 60, 10300, 20400, now - 1000)
        assertEquals(400, second.getLong("usedReceive")); assertEquals(600, second.getLong("usedSend"))
        val stale = VpsQuota.sample(second, config, now, 0, 0, 0)
        assertEquals(second.toString(), stale.toString())
    }
    @Test fun quotaCyclesAndRestartsAreMarkedIncomplete() {
        val cfg = VpsQuotaConfig(enabled = true)
        val now = time("2026-09-30T23:00:00Z")
        val first = VpsQuota.sample(null, cfg, now, 100, 200, now - 1000)
        val restarted = VpsQuota.sample(first, cfg, now + 60, 10, 20, now + 30)
        assertEquals(10, restarted.getLong("usedReceive")); assertTrue(restarted.getBoolean("uncertain"))
        val cycle = VpsQuota.sample(restarted, cfg, time("2026-10-01T01:00:00Z"), 600, 700, now + 30)
        assertEquals(0, cycle.getLong("usedReceive")); assertTrue(cycle.getString("note").contains("billing cycle"))
    }
    @Test fun providerBillingModesCountOnlyChargeableDirections() {
        val cfg = VpsQuotaConfig(enabled = true, receiveLimit = 1000, sendLimit = 1000, totalLimit = 2000)
        val usage = jo("usedReceive" to 950, "usedSend" to 100, "cycle" to 1)
        assertTrue(VpsQuota.alerts("p","Panel",usage,cfg).isEmpty())
        assertEquals(1, VpsQuota.alerts("p","Panel",usage,cfg.copy(mode="receive")).size)
        assertTrue(VpsQuota.alerts("p","Panel",usage,cfg.copy(mode="send")).isEmpty())
        assertEquals(1, VpsQuota.alerts("p","Panel",usage,cfg.copy(mode="separate")).size)
    }
    @Test fun manualBaselineCorrectionReplacesObservedUsage() {
        val cfg = VpsQuotaConfig(enabled = true)
        val now = time("2026-09-10T12:00:00Z")
        val old = VpsQuota.sample(null, cfg, now, 100, 200, now - 100)
        val updated = VpsQuota.sample(old, cfg.copy(baseline="corrected", initialReceive=900), now+60, 300, 400, now-100)
        assertEquals(900, updated.getLong("usedReceive"))
        assertEquals(cfg, VpsQuotaConfig.fromJson(cfg.toJson()))
    }
    @Test fun appPinHasMinimumLengthSaltedVerifierAndConstantTimeComparison() {
        assertFalse(PinVerifier.valid("1234567".toCharArray()))
        assertFalse(PinVerifier.valid("1234567x".toCharArray()))
        val pin = "92740518".toCharArray()
        val a = PinVerifier.create(pin); val b = PinVerifier.create(pin)
        assertNotEquals(a.getString("salt"), b.getString("salt"))
        assertTrue(PinVerifier.verify(pin, a))
        assertFalse(PinVerifier.verify("92740519".toCharArray(), a))
        assertFalse(a.toString().contains("92740518"))
    }
    @Test fun appPinFailuresPersistAnIncreasingCooldown() {
        val record = jo("failures" to 0)
        repeat(5) { PinVerifier.failed(record, 1000) }
        assertEquals(30, PinVerifier.remainingSeconds(record, 1000))
        PinVerifier.failed(record, 1040)
        assertEquals(60, PinVerifier.remainingSeconds(record, 1040))
        assertEquals(0, PinVerifier.remainingSeconds(record, 2000))
    }
}
