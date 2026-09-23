package com.sonix21.suinode.data

/** Initialized by the application; left injectable for isolated transport contract tests. */
object ConnectionSecurity {
    @Volatile var verifyIdentity: ((Panel, String) -> Boolean)? = null
    fun init(context: android.content.Context) {
        val store = PanelStore(context.applicationContext)
        verifyIdentity = store::checkIdentity
    }
}
