package com.sonix21.suinode.core

/** Suppress a monitor snapshot fetched before a successful in-app client write. */
object ClientAlertFreshness {
    private val versions = mutableMapOf<String, Long>()
    private val lock = Any()
    var onChanged: ((String) -> Unit)? = null
    fun version(panelId: String): Long = synchronized(lock) { versions[panelId] ?: 0L }
    fun changed(panelId: String) = synchronized(lock) {
        versions[panelId] = (versions[panelId] ?: 0L) + 1
        // Notification housekeeping must not turn a successful panel save into a reported failure.
        runCatching { onChanged?.invoke(panelId) }
    }
    fun ifCurrent(panelId: String, version: Long, publish: () -> Unit): Boolean = synchronized(lock) {
        if ((versions[panelId] ?: 0L) != version) false else { publish(); true }
    }
}
