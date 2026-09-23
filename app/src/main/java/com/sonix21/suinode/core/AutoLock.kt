package com.sonix21.suinode.core

enum class AutoLock(val label: String, val delayMillis: Long?) {
    IMMEDIATE("Immediately", 0), ONE_MINUTE("After 1 minute", 60_000),
    FIVE_MINUTES("After 5 minutes", 300_000), FIFTEEN_MINUTES("After 15 minutes", 900_000),
    SCREEN_OFF("When the screen turns off", null),
}

/** Uses elapsed realtime, never the user-adjustable wall clock. */
class LockWindow {
    private var leftAt: Long? = null
    fun background(now: Long) { leftAt = now }
    fun clear() { leftAt = null }
    fun expired(now: Long, policy: AutoLock): Boolean {
        val start = leftAt ?: return false
        val delay = policy.delayMillis ?: return false
        return now < start || now - start >= delay
    }
}

enum class DocumentKind { BACKUP, RESTORE }
data class DocumentTicket(val panelId: String, val kind: DocumentKind, val exclude: String = "")

/** The queue contains identifiers/URI only, never a panel token or database bytes. */
class DocumentQueue {
    var ticket: DocumentTicket? = null
        private set
    var uri: String? = null
        private set
    fun begin(value: DocumentTicket) {
        check(ticket == null) { "A document operation is already pending" }
        ticket = value; uri = null
    }
    fun selected(value: String?) { if (value == null) cancel() else if (ticket != null) uri = value }
    fun cancel() { ticket = null; uri = null }
    fun claim(unlocked: Boolean, vaultReady: Boolean): Pair<DocumentTicket, String>? {
        if (!unlocked || !vaultReady) return null
        val task = ticket ?: return null
        val target = uri ?: return null
        cancel() // Main-thread consume-once, before starting any network request.
        return task to target
    }
}
