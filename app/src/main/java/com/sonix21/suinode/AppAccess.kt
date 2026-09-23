package com.sonix21.suinode

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sonix21.suinode.core.LockWindow
import com.sonix21.suinode.data.Panels
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppAccess(private val activity: MainActivity) {
    var unlocked by mutableStateOf(!APP.prefs.appLockEnabled)
        private set
    private val window = LockWindow()
    private var timeout: Job? = null
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && APP.prefs.appLockEnabled) lock()
        }
    }
    init {
        ContextCompat.registerReceiver(activity, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    fun authenticated() { timeout?.cancel(); window.clear(); unlocked = true }
    fun lock() { timeout?.cancel(); window.clear(); unlocked = false; Panels.lock() }
    fun foreground() {
        timeout?.cancel()
        if (APP.prefs.appLockEnabled &&
            (activity.getSystemService(KeyguardManager::class.java).isDeviceLocked || window.expired(SystemClock.elapsedRealtime(), APP.prefs.autoLock))) lock()
        window.clear()
    }
    fun background() {
        if (!APP.prefs.appLockEnabled || !unlocked) return
        window.background(SystemClock.elapsedRealtime())
        val delayMillis = APP.prefs.autoLock.delayMillis ?: return
        if (delayMillis == 0L) { lock(); return }
        timeout?.cancel()
        timeout = activity.lifecycleScope.launch { delay(delayMillis); if (APP.prefs.appLockEnabled) lock() }
    }
    fun close() { timeout?.cancel(); activity.unregisterReceiver(screenOff) }
}
