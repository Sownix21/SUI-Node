package com.sonix21.suinode.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Profile changes publish a new observable session, even when the profile ID is unchanged. */
object Panels {
    private lateinit var store: PanelStore
    private val _list = MutableStateFlow<List<Panel>>(emptyList())
    val list: StateFlow<List<Panel>> = _list
    private val _activeId = MutableStateFlow("")
    val activeId: StateFlow<String> = _activeId
    private val _session = MutableStateFlow<PanelSession?>(null)
    val currentSession: StateFlow<PanelSession?> = _session
    val session: PanelSession? get() = _session.value
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError
    private val _protection = MutableStateFlow("")
    val protection: StateFlow<String> = _protection

    fun init(context: Context) {
        store = PanelStore(context.applicationContext)
    }

    @Synchronized fun unlock() {
        if (_ready.value) return
        _storageError.value = null
        try {
            val loaded = store.load()
            val selected = loaded.firstOrNull { it.id == store.activeId() } ?: loaded.firstOrNull()
            val protection = store.protection()
            publish(selected, persist = false)
            _list.value = loaded
            _protection.value = protection
            _ready.value = true
        } catch (_: Exception) {
            _storageError.value = "Secure storage could not be opened. Unlock your device and retry. Your saved data has not been reset."
        }
    }

    @Synchronized fun lock() {
        session?.client?.logout()
        _session.value = null
        _list.value = emptyList()
        _activeId.value = ""
        _ready.value = false
        StatusStore.reset()
    }

    private fun publish(panel: Panel?, persist: Boolean = true) {
        if (persist) store.setActive(panel?.id.orEmpty())
        session?.client?.logout()
        StatusStore.reset()
        _session.value = panel?.let(::PanelSession)
        _activeId.value = panel?.id.orEmpty()
    }

    @Synchronized fun activate(id: String) {
        check(_ready.value) { "Secure storage is locked" }
        val panel = _list.value.firstOrNull { it.id == id } ?: return
        if (session?.panel == panel) return
        publish(panel)
    }

    @Synchronized fun upsert(panel: Panel) {
        check(_ready.value) { "Secure storage is locked" }
        val next = _list.value.toMutableList()
        val index = next.indexOfFirst { it.id == panel.id }
        if (index < 0) next.add(panel) else next[index] = panel
        store.save(next)
        _list.value = next
        if (session == null || _activeId.value == panel.id) publish(panel)
    }

    @Synchronized fun remove(id: String) {
        check(_ready.value) { "Secure storage is locked" }
        val next = _list.value.filterNot { it.id == id }
        store.save(next)
        _list.value = next
        if (_activeId.value == id) publish(next.firstOrNull())
    }

    fun active(): Panel? = session?.panel
}
