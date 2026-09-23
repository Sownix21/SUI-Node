package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test

class AutoLockTest {
    @Test fun immediateLockRequiresAnActualBackgroundEvent() {
        val window = LockWindow()
        assertFalse(window.expired(10, AutoLock.IMMEDIATE))
        window.background(10)
        assertTrue(window.expired(10, AutoLock.IMMEDIATE))
    }
    @Test fun gracePeriodHasAnExactMonotonicDeadline() {
        val window = LockWindow(); window.background(1_000)
        assertFalse(window.expired(60_999, AutoLock.ONE_MINUTE))
        assertTrue(window.expired(61_000, AutoLock.ONE_MINUTE))
        window.clear(); assertFalse(window.expired(100_000, AutoLock.ONE_MINUTE))
    }
    @Test fun eachBackgroundPeriodStartsFreshAndScreenOnlyHasNoTimer() {
        val window = LockWindow(); window.background(1_000); window.clear(); window.background(20_000)
        assertFalse(window.expired(79_999, AutoLock.ONE_MINUTE))
        assertFalse(window.expired(Long.MAX_VALUE, AutoLock.SCREEN_OFF))
        assertTrue(window.expired(0, AutoLock.ONE_MINUTE)) // Fail closed for an invalid elapsed clock.
    }
    @Test fun documentSelectionSurvivesLockAndWaitsForBothGates() {
        val queue = DocumentQueue()
        val ticket = DocumentTicket("panel-id", DocumentKind.BACKUP, "stats,changes")
        queue.begin(ticket); queue.selected("content://fixture/document/backup")
        assertNull(queue.claim(false, true)); assertNull(queue.claim(true, false))
        assertEquals(ticket, queue.ticket)
        assertEquals(ticket to "content://fixture/document/backup", queue.claim(true, true))
        assertNull(queue.claim(true, true))
    }
    @Test fun cancelledPickerCannotRunAPreviousTransfer() {
        val queue = DocumentQueue(); queue.begin(DocumentTicket("a", DocumentKind.BACKUP))
        assertNull(queue.claim(true, true))
        queue.selected(null); assertNull(queue.ticket)
        queue.selected("content://late-result"); assertNull(queue.claim(true, true))
    }
    @Test fun restoreIsClaimedOnlyOnceAndConcurrentPickersAreRejected() {
        val queue = DocumentQueue(); queue.begin(DocumentTicket("a", DocumentKind.RESTORE))
        assertTrue(runCatching { queue.begin(DocumentTicket("b", DocumentKind.BACKUP)) }.isFailure)
        queue.selected("content://fixture/restore")
        assertEquals(DocumentKind.RESTORE, queue.claim(true, true)!!.first.kind)
        assertNull(queue.claim(true, true))
    }
}
