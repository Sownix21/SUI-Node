package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LockAndMonitoringRegressionTest {
    @Test fun aClientSaveInvalidatesOnlyItsPanelsPendingSnapshot() {
        val panel = "freshness-test"
        val before = ClientAlertFreshness.version(panel)
        ClientAlertFreshness.changed(panel)
        var called = false
        assertFalse(ClientAlertFreshness.ifCurrent(panel, before) { called = true })
        assertFalse(called)
        assertTrue(ClientAlertFreshness.ifCurrent(panel, ClientAlertFreshness.version(panel)) { called = true })
        assertTrue(called)
    }
    @Test fun extendingNearExpiryDoesNotSendAnotherImmediateNotification() {
        val state = jo(); val c = MonitorConfig(enabled = true, intervalMinutes = 60, repeatHours = 24)
        val original = AlertCandidate("p:expiry:1", "p", "expiry", "Expiring", "private", "15000")
        assertEquals(1, AlertEngine.update(state, "p", listOf(original), setOf("expiry"), 1000, c, true).size)
        val renewed = original.copy(fingerprint = "25000")
        assertTrue(AlertEngine.update(state, "p", listOf(renewed), setOf("expiry"), 1100, c, true).isEmpty())
        assertTrue(AlertEngine.update(state, "p", listOf(renewed), setOf("expiry"), 1200, c, true).isEmpty())
        assertEquals(1, AlertEngine.update(state, "p", listOf(renewed.copy(severity = 2)), setOf("expiry"), 1300, c, true).size)
    }
    @Test fun futureCycleDefersAllUsageAndQuotaAlerts() {
        val start = Instant.parse("2026-10-01T06:30:00Z").epochSecond
        val cfg = VpsQuotaConfig(enabled = true, resetHour = 6, resetMinute = 30, startMode = "next_cycle", startAt = start)
        val waiting = VpsQuota.sample(null, cfg, start - 60, 999999, 888888, start - 1000)
        assertTrue(waiting.getBoolean("waiting"))
        assertEquals(waiting.toString(), VpsQuota.sample(waiting, cfg, start - 120, 1, 1, start - 1000).toString())
        assertTrue(VpsQuota.alerts("p", "Panel", waiting, cfg).isEmpty())
        val initial = VpsQuota.sample(waiting, cfg, start + 120, 1000099, 888988, start - 1000)
        assertFalse(initial.optBoolean("waiting"))
        assertEquals(0, initial.getLong("usedReceive"))
        val second = VpsQuota.sample(initial, cfg, start + 180, 1000299, 889088, start - 1000)
        assertEquals(200, second.getLong("usedReceive"))
        assertEquals(second.toString(), VpsQuota.sample(second, cfg, start - 30, 1, 1, start - 1000).toString())
        assertEquals(cfg, VpsQuotaConfig.fromJson(cfg.toJson()))
    }
    @Test fun resetHourIsAppliedInProviderTimezone() {
        val cfg = VpsQuotaConfig(resetDay = 1, resetHour = 6, resetMinute = 30, zone = "Asia/Tehran")
        assertEquals(Instant.parse("2026-10-01T03:00:00Z").epochSecond,
            VpsQuota.nextReset(Instant.parse("2026-09-30T20:00:00Z").epochSecond, cfg))
    }
    @Test fun pickerUsesLocalCalendarDayRatherThanUtcInstant() {
        val zone = java.time.ZoneId.of("Asia/Tehran")
        val unix = Instant.parse("2026-09-30T21:15:00Z").epochSecond
        val day = DateTimeInput.pickerDate(unix, zone)
        assertEquals(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), day)
        assertEquals(unix, DateTimeInput.unix(day, 0, 45, zone))
    }
    @Test fun nonexistentDstTimeIsRejectedInsteadOfSilentlyChangingExpiry() {
        val day = Instant.parse("2026-03-08T00:00:00Z").toEpochMilli()
        assertTrue(runCatching { DateTimeInput.unix(day, 2, 30, java.time.ZoneId.of("America/New_York")) }.isFailure)
    }
    @Test fun biometricUnlockRequiresKnownConfiguredPin() {
        assertEquals(UnlockMode.PIN_AND_BIOMETRIC, UnlockPolicy.mode(true, true, true))
        assertEquals(UnlockMode.PIN, UnlockPolicy.mode(true, true, false))
        assertEquals(UnlockMode.LOADING, UnlockPolicy.mode(null, true, true))
        assertEquals(UnlockMode.PIN_UNAVAILABLE, UnlockPolicy.mode(false, true, true))
    }
    @Test fun legacyLockOffersMigrationOnlyBeforePinFirstSetup() {
        assertEquals(UnlockMode.LEGACY_MIGRATION, UnlockPolicy.mode(false, false, false))
        assertEquals(UnlockMode.LEGACY_MIGRATION, UnlockPolicy.mode(false, false, true))
        assertEquals(UnlockMode.PIN_UNAVAILABLE, UnlockPolicy.mode(false, true, false))
    }
    @Test fun persianAndArabicPinInputUsesSameVerifierDigits() {
        assertEquals("12345678", UnlockPolicy.normalizePin("۱۲۳۴۵۶۷۸"))
        assertEquals("12345678", UnlockPolicy.normalizePin("١٢٣٤٥٦٧٨"))
        assertFalse(PinVerifier.valid(UnlockPolicy.normalizePin("۱۲۳۴۵۶۷x").toCharArray()))
    }
    @Test fun serverOnlyMonitoringSkipsClientDetailBatches() {
        val config = MonitorConfig(expiry = false, quota = false)
        assertEquals(MonitorReads(false, "sbd"), config.reads(false))
        assertEquals(MonitorReads(false, "sbd,net,sys"), config.reads(true))
        assertEquals(MonitorReads(true, null), config.copy(expiry = true, offline = false, coreStopped = false).reads(false))
    }
    @Test fun failedNotificationDeliveryRemainsEligibleEvenWithNoRepeats() {
        val state = jo()
        val c = MonitorConfig(enabled = true, repeatHours = 0)
        val alert = AlertCandidate("p:quota:1", "p", "quota", "Quota", "private", "v1")
        val first = AlertEngine.update(state, "p", listOf(alert), setOf("quota"), 1000, c, true)
        AlertEngine.deliveryFailed(state, first)
        assertEquals(1, AlertEngine.update(state, "p", listOf(alert), setOf("quota"), 1100, c, true).size)
        assertTrue(AlertEngine.update(state, "p", listOf(alert), setOf("quota"), 1200, c, true).isEmpty())
    }
    @Test fun olderDeliveryFailureCannotResetNewerNotification() {
        val state = jo(); val c = MonitorConfig(enabled = true)
        val firstAlert = AlertCandidate("p:quota:1", "p", "quota", "Quota", "private", "v1")
        val first = AlertEngine.update(state, "p", listOf(firstAlert), setOf("quota"), 1000, c, true)
        AlertEngine.update(state, "p", listOf(firstAlert.copy(fingerprint = "v2")), setOf("quota"), 1100, c, true)
        AlertEngine.deliveryFailed(state, first)
        assertEquals(1100, state.getJSONObject("active").getJSONObject(firstAlert.key).getLong("lastSent"))
    }
    @Test fun disablingAnAlertDoesNotSendAFakeRecovery() {
        val state = jo(); val c = MonitorConfig(enabled = true)
        val alert = AlertCandidate("p:core", "p", "core", "Stopped", "private", "stopped")
        AlertEngine.update(state, "p", listOf(alert), setOf("core"), 1000, c, true)
        assertTrue(AlertEngine.update(state, "p", emptyList(), setOf("core"), 1100, c, true, setOf("core")).isEmpty())
        assertEquals(0, state.getJSONObject("active").length())
    }
    @Test fun decimalVpsLimitsAcceptLocalizedDigitsWithoutRounding() {
        assertEquals(1_250_000_000L, VpsQuota.parseGb("۱٫۲۵"))
        assertEquals(1L, VpsQuota.parseGb("0.000000001"))
        for (text in listOf("-1", "NaN", "1e20", "0.0000000001", "999999999999999999999"))
            assertTrue(text, runCatching { VpsQuota.parseGb(text) }.isFailure)
    }
    @Test fun calendarChangesRequireExplicitUsageCorrection() {
        val old = VpsQuotaConfig(enabled = true)
        val next = old.copy(resetDay = 15)
        assertTrue(runCatching { VpsQuota.validateCalendarChange(old, next, true, false) }.isFailure)
        VpsQuota.validateCalendarChange(old, next, true, true)
        VpsQuota.validateCalendarChange(old, next, false, false)
        VpsQuota.validateCalendarChange(old, old.copy(threshold = 80), true, false)
    }
    @Test fun nextBillingResetRestoresMonthEndAfterFebruary() {
        val config = VpsQuotaConfig(resetDay = 31)
        assertEquals(Instant.parse("2026-03-31T00:00:00Z").epochSecond,
            VpsQuota.nextReset(Instant.parse("2026-03-01T00:00:00Z").epochSecond, config))
        assertEquals(Instant.parse("2026-02-28T00:00:00Z").epochSecond,
            VpsQuota.nextReset(Instant.parse("2026-02-01T00:00:00Z").epochSecond, config))
    }
}
