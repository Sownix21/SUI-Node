package com.sonix21.suinode.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import android.annotation.SuppressLint

enum class AppThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), AMOLED("AMOLED"), SYSTEM("System") }
enum class AccentPalette(val label: String, val dark: Long, val light: Long) {
    LIME("Lime", 0xFFD2EC90, 0xFF48651E),
    EMERALD("Emerald", 0xFF80E2B6, 0xFF006C4C),
    CYAN("Cyan", 0xFF80DCE6, 0xFF006874),
    BLUE("Blue", 0xFFA9C7FF, 0xFF245FA5),
    AMBER("Amber", 0xFFF5D178, 0xFF785900),
    AMETHYST("Amethyst", 0xFFD3B9FF, 0xFF6B45A0),
}
enum class Lang { EN, FA }

/** Plain preferences for appearance / behavior. */
class AppPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sui_app_prefs", Context.MODE_PRIVATE)

    var theme: AppThemeMode
        get() = runCatching { AppThemeMode.valueOf(prefs.getString("theme", null) ?: "DARK") }.getOrDefault(AppThemeMode.DARK)
        set(v) = prefs.edit { putString("theme", v.name) }

    var lang: Lang
        get() = if (prefs.getString("lang", "EN") == "FA") Lang.FA else Lang.EN
        set(v) = prefs.edit { putString("lang", v.name) }

    var dynamicColors: Boolean
        get() = prefs.getBoolean("dynamicColors", false)
        set(v) = prefs.edit { putBoolean("dynamicColors", v) }

    var accent: AccentPalette
        get() = runCatching { AccentPalette.valueOf(prefs.getString("accent", "LIME")!!) }.getOrDefault(AccentPalette.LIME)
        set(v) = prefs.edit { putString("accent", v.name); putBoolean("dynamicColors", false) }

    var refreshIntervalSec: Int
        get() = prefs.getInt("refreshSec", 4)
        set(v) = prefs.edit { putInt("refreshSec", v.coerceIn(2, 60)) }

    var appLockEnabled: Boolean
        get() = prefs.getBoolean("appLock", false)
        set(v) = prefs.edit { putBoolean("appLock", v) }

    var biometricUnlockEnabled: Boolean
        get() = prefs.getBoolean("biometricUnlock", false)
        set(v) = prefs.edit { putBoolean("biometricUnlock", v) }

    // Once PIN-first security is set up, a missing PIN record must fail closed.
    var pinFirstLock: Boolean
        get() = prefs.getBoolean("pinFirstLock", false)
        set(v) = prefs.edit { putBoolean("pinFirstLock", v) }

    /** Call off the UI thread. Persist dependencies together before deleting a PIN record. */
    // KTX edit returns Unit; this operation must check the commit result before removing a PIN.
    @SuppressLint("UseKtx")
    fun setLockState(enabled: Boolean, biometric: Boolean = biometricUnlockEnabled) {
        check(prefs.edit().putBoolean("appLock", enabled)
            .putBoolean("biometricUnlock", enabled && biometric)
            .putBoolean("pinFirstLock", true).commit()) { "Unable to save app-lock settings" }
    }

    var autoLock: AutoLock
        get() = runCatching { AutoLock.valueOf(prefs.getString("autoLock", AutoLock.IMMEDIATE.name)!!) }.getOrDefault(AutoLock.IMMEDIATE)
        set(v) = prefs.edit { putString("autoLock", v.name) }

    var blockScreenCapture: Boolean
        get() = prefs.getBoolean("blockScreenCapture", true)
        set(v) = prefs.edit { putBoolean("blockScreenCapture", v) }

    var reviewChanges: Boolean
        get() = prefs.getBoolean("reviewChanges", true)
        set(v) = prefs.edit { putBoolean("reviewChanges", v) }
    var recoverSaves: Boolean
        get() = prefs.getBoolean("recoverSaves", true)
        set(v) = prefs.edit { putBoolean("recoverSaves", v) }

    var defaultTiles: MutableSet<String>
        get() = HashSet(prefs.getStringSet("tiles", null) ?: setOf(
            "g-cpu", "g-mem", "g-dsk", "g-swp", "h-net", "i-sys", "i-sbd",
        ))
        set(v) = prefs.edit { putStringSet("tiles", v) }
}
