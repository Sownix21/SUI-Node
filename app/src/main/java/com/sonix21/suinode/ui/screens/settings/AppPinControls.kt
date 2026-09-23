package com.sonix21.suinode.ui.screens.settings

import androidx.lifecycle.repeatOnLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.APP
import com.sonix21.suinode.data.AppPinStore
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.screens.rememberRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun PinField(label: String, value: String, change: (String) -> Unit, enabled: Boolean = true) {
    GlassTextField(label, value, { if (it.length <= 32 && it.all { c -> c in '0'..'9' }) change(it) },
        obscure = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        supporting = "8–32 digits · separate from your Android screen-lock PIN")
}

@Composable
fun AppPinSettings(onLockEnabled: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppPinStore(context.applicationContext) }
    val runner = rememberRunner()
    var configured by remember { mutableStateOf<Boolean?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    fun refresh() = runner.go { configured = withContext(Dispatchers.IO) { store.configured() } }
    LaunchedEffect(Unit) { refresh() }
    GlassCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("App PIN")
        runner.error?.let { Text(it, color = LocalGlass.current.err) }
        Text(when (configured) { true -> "App PIN configured"; false -> "Optional alternative to biometric/device unlock"; null -> "Checking protected PIN storage" }, color = LocalGlass.current.textFaint)
        if (configured == null) GhostButton("Retry PIN status") { refresh() }
        else {
            GhostButton(if (expanded) "Close PIN settings" else if (configured == true) "Change / remove app PIN" else "Set up app PIN") {
                expanded = !expanded; current = ""; next = ""; repeat = ""
            }
            if (expanded) {
                if (configured == true) PinField("Current app PIN", current, { current = it }, !runner.busy)
                PinField("New app PIN", next, { next = it }, !runner.busy)
                PinField("Confirm new app PIN", repeat, { repeat = it }, !runner.busy)
                PrimaryButton("Save app PIN", enabled = !runner.busy && next.length >= 8 && next == repeat) { runner.go {
                    withContext(Dispatchers.IO) { store.set(next.toCharArray(), current.toCharArray()) }
                    APP.prefs.appLockEnabled = true
                    configured = true; current = ""; next = ""; repeat = ""; expanded = false
                    onLockEnabled()
                    ToastBus.show("App PIN saved; app lock enabled")
                } }
                if (configured == true) GhostButton("Remove app PIN", enabled = !runner.busy && current.length >= 8) { runner.go {
                    val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    check(!APP.prefs.appLockEnabled || androidx.biometric.BiometricManager.from(context).canAuthenticate(authenticators) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                        "Set up Android device authentication or turn off App lock before removing its only unlock method"
                    }
                    withContext(Dispatchers.IO) { store.remove(current.toCharArray()) }
                    configured = false; current = ""; next = ""; repeat = ""; expanded = false
                    ToastBus.show("App PIN removed; Android authentication is unchanged")
                } }
                Text("Stored as a salted PBKDF2 verifier inside a separate encrypted, device-bound vault. Repeated failures impose increasing delays. Remember this PIN; changing or removing it requires the current PIN. Android authentication remains available separately.", color = LocalGlass.current.textFaint)
            }
        }
    } }
}

@Composable
fun PinUnlockPanel(onUnlocked: () -> Unit, systemUnlock: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppPinStore(context.applicationContext) }
    val runner = rememberRunner()
    var configured by remember { mutableStateOf<Boolean?>(null) }
    var pin by remember { mutableStateOf("") }
    fun load() = runner.go {
        configured = withContext(Dispatchers.IO) { store.configured() }
    }
    LaunchedEffect(Unit) { load() }
    var autoPrompted by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(configured, lifecycleOwner) {
        if (configured == false) lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            if (!autoPrompted) { autoPrompted = true; systemUnlock() }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        runner.error?.let { Text(it, color = LocalGlass.current.err) }
        if (configured == true) {
            PinField("App PIN", pin, { pin = it }, !runner.busy)
            PrimaryButton("Unlock with app PIN", enabled = !runner.busy && pin.length >= 8) { runner.go {
                val entered = pin.toCharArray(); pin = ""
                check(withContext(Dispatchers.IO) { store.verify(entered) }) { "Incorrect app PIN" }
                onUnlocked()
            } }
        }
        if (configured == null) GhostButton("Retry PIN storage") { load() }
        GhostButton("Biometric / Android device unlock", enabled = !runner.busy, onClick = systemUnlock)
    }
}
