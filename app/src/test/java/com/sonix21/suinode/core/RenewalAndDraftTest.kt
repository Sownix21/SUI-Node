package com.sonix21.suinode.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RenewalAndDraftTest {
    @Test fun renewalsAreDetachedAndPreserveCredentialsAndUnknownFields() {
        val original = jo("id" to 1, "expiry" to 100, "volume" to 1000L, "enable" to false, "config" to jo("password" to "fake"), "future" to true)
        val updated = RenewalPlan.apply(original, 2, 1, false, 200)
        assertEquals(200 + 2 * 86400L, updated.getLong("expiry"))
        assertEquals(1000L + 1073741824, updated.getLong("volume"))
        assertEquals("fake", updated.getJSONObject("config").getString("password"))
        assertFalse(updated.getBoolean("enable"))
        assertEquals(100, original.getLong("expiry"))
    }
    @Test fun noExpiryUnlimitedAndDelayedStartAreNotChanged() {
        val original = jo("expiry" to 0, "volume" to 0, "enable" to false)
        val updated = RenewalPlan.apply(original, 30, 50, true, 200)
        assertEquals(0, updated.getLong("expiry")); assertEquals(0, updated.getLong("volume"))
        assertTrue(updated.getBoolean("enable"))
        original.put("delayStart", true).put("expiry", 100)
        assertEquals(100, RenewalPlan.apply(original, 30, 0, false, 200).getLong("expiry"))
    }
    @Test fun overflowAndInvalidAmountsAreRejected() {
        assertTrue(runCatching { RenewalPlan.apply(jo("volume" to Long.MAX_VALUE), 0, 1, false, 0) }.isFailure)
        assertTrue(runCatching { RenewalPlan.apply(JSONObject(), -1, 0, false, 0) }.isFailure)
    }
    @Test fun conflictComparisonIgnoresTrafficButNotConfiguration() {
        val a = jo("id" to 1, "volume" to 10, "up" to 1)
        assertTrue(RenewalPlan.sameConfiguration(a, a.deepCopy().put("up", 2)))
        assertFalse(RenewalPlan.sameConfiguration(a, a.deepCopy().put("volume", 20)))
    }
    @Test fun draftDetectsRevertedChangesAndAcceptsOnlyExplicitSave() {
        val value = jo("a" to 1)
        val checkpoint = DraftCheckpoint { value }
        assertFalse(checkpoint.changed())
        value.put("a", 2); assertTrue(checkpoint.changed())
        value.put("a", 1); assertFalse(checkpoint.changed())
        value.put("a", 3); checkpoint.accept(); assertFalse(checkpoint.changed())
        value.put("a", 4); assertTrue(checkpoint.changed())
    }
}
