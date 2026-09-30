package com.sonix21.suinode

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sonix21.suinode.core.AppThemeMode
import com.sonix21.suinode.core.Lang
import com.sonix21.suinode.ui.AppRoot
import com.sonix21.suinode.ui.glass.GlassTheme

class MainActivity : FragmentActivity() {
    lateinit var access: AppAccess
        private set
    lateinit var documents: DocumentTransfers
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        access = AppAccess(this)
        documents = DocumentTransfers(this, savedInstanceState)
        applyScreenPrivacy()
        // Compose supplies both palettes; OEM force-dark must not recolor its text or QR codes.
        if (android.os.Build.VERSION.SDK_INT >= 29) window.decorView.isForceDarkAllowed = false
        setContent {
            var themeTick by remember { mutableStateOf(0) }
            val dark = when (APP.prefs.theme) {
                AppThemeMode.DARK, AppThemeMode.AMOLED -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            androidx.compose.runtime.SideEffect {
                applyScreenPrivacy()
                androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            LaunchedEffect(themeTick) { }
            GlassTheme(dark = dark, dynamicColors = APP.prefs.dynamicColors,
                amoled = APP.prefs.theme == AppThemeMode.AMOLED, palette = APP.prefs.accent) {
                CompositionLocalProvider(LocalLayoutDirection provides if (APP.prefs.lang == Lang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        com.sonix21.suinode.ui.glass.LiquidBackdrop()
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                            AppRoot(requestThemeRefresh = { themeTick++ })
                        }
                    }
                }
            }
        }
    }

    fun applyScreenPrivacy() {
        if (APP.prefs.blockScreenCapture) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!APP.prefs.blockScreenCapture)
    }

    override fun onStart() { access.foreground(); super.onStart() }
    override fun onStop() { access.background(); super.onStop() }
    override fun onDestroy() { access.close(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { documents.saveState(outState); super.onSaveInstanceState(outState) }
}

/** Device credentials are accepted only to migrate an existing biometric-only lock. */
fun runBiometricGate(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit,
    legacyMigration: Boolean = false) {
    val manager = BiometricManager.from(activity)
    val authenticators = if (legacyMigration)
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_STRONG
    val can = manager.canAuthenticate(authenticators)
    if (can != BiometricManager.BIOMETRIC_SUCCESS) {
        onError(if (legacyMigration) "Authenticate with the Android security method used by your previous app lock"
            else "Strong biometrics are unavailable. Use your app PIN or enroll a supported biometric in Android settings.")
        return
    }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
        override fun onAuthenticationError(code: Int, msg: CharSequence) { onError(msg.toString()) }
    })
    val builder = BiometricPrompt.PromptInfo.Builder()
        .setTitle("S-UI Node")
        .setSubtitle(if (legacyMigration) "Verify your existing lock before setting up an app PIN" else "Use biometrics or return to your app PIN")
        .setAllowedAuthenticators(authenticators)
    if (!legacyMigration) builder.setNegativeButtonText("Use app PIN")
    prompt.authenticate(builder.build())
}
