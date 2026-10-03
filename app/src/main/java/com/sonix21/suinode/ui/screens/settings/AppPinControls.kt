package com.sonix21.suinode.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sonix21.suinode.APP
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.AppPinStore
import com.sonix21.suinode.runBiometricGate
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.screens.rememberRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Composable
private fun PinField(label: String, value: String, change: (String) -> Unit, enabled: Boolean = true) {
    GlassTextField(label, value, { input ->
        val normalized = UnlockPolicy.normalizePin(input)
        if (normalized.length <= 32 && normalized.all { it in '0'..'9' }) change(normalized)
    }, obscure = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        supporting = "8–32 digits · separate from your Android screen-lock PIN")
}

@Composable
fun AppPinSettings(onChanged: () -> Unit) {
    val context = LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current as? com.sonix21.suinode.MainActivity
    val store = remember { AppPinStore(context.applicationContext) }
    val runner = rememberRunner()
    var configured by remember { mutableStateOf<Boolean?>(null) }
    var lock by remember { mutableStateOf(APP.prefs.appLockEnabled) }
    var biometric by remember { mutableStateOf(APP.prefs.biometricUnlockEnabled) }
    var lockAfter by remember { mutableStateOf(APP.prefs.autoLock) }
    var action by remember { mutableStateOf<PinSettingsAction?>(null) }
    var prompting by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    val busy = runner.busy || prompting
    fun refresh() = runner.go { configured = withContext(Dispatchers.IO) { store.configured() } }
    fun enableBiometrics() = runner.go {
        val host = checkNotNull(activity) { "Biometric authentication unavailable" }
        val hasPin = withContext(Dispatchers.IO) { store.configured() }
        check(UnlockPolicy.canEnrollBiometrics(hasPin, APP.prefs.appLockEnabled, host.access.unlocked)) {
            "Unlock the app and set up an app PIN first"
        }
        biometricError = null
        prompting = true
        try {
            runBiometricGate(host, onSuccess = {
                prompting = false
                runner.go {
                    withContext(NonCancellable + Dispatchers.IO) {
                        // Recheck dependencies after the Android prompt, before persisting the opt-in.
                        check(UnlockPolicy.canEnrollBiometrics(store.configured(), APP.prefs.appLockEnabled, host.access.unlocked)) {
                            "Unlock the app and set up an app PIN first"
                        }
                        APP.prefs.setLockState(true, true)
                    }
                    biometric = true; onChanged()
                }
            }, onError = {
                prompting = false; biometricError = it
            }, enrollment = true)
        } catch (error: Exception) {
            prompting = false
            throw error
        }
    }
    LaunchedEffect(Unit) { refresh() }
    GlassCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("App lock & PIN")
        runner.error?.let { Text(it, color = LocalGlass.current.err) }
        Text(when (configured) {
            true -> "Your app PIN is the primary unlock method. Biometrics are an optional shortcut."
            false -> "Set up an app PIN before enabling app lock or biometrics."
            null -> "Checking protected PIN storage"
        }, color = LocalGlass.current.textFaint)
        if (configured == null) GhostButton("Retry PIN status", enabled = !busy) { refresh() }
        else {
            if (configured == true) {
                SwitchRow("App lock", lock, { enable ->
                    if (!busy) action = if (enable) PinSettingsAction.ENABLE_LOCK else PinSettingsAction.DISABLE_LOCK
                }, subtitle = "Changes require your current app PIN")
                if (lock) {
                    SwitchRow("Biometric unlock", biometric, { enable ->
                        if (!busy) {
                            if (enable) enableBiometrics()
                            else runner.go {
                                withContext(NonCancellable + Dispatchers.IO) { APP.prefs.setLockState(lock, false) }
                                biometric = false; biometricError = null; onChanged()
                            }
                        }
                    }, subtitle = "Confirm with Android biometrics. Your configured app PIN stays available as fallback.")
                    biometricError?.let { Text(it, color = LocalGlass.current.err) }
                }
            }
            if (lock) {
                SelectField("Lock after leaving", lockAfter, AutoLock.entries.map { Opt(it.label, it) }, clearable = false,
                    onChange = { policy -> if (policy != null) { lockAfter = policy; APP.prefs.autoLock = policy } })
                Text("Screen-off always locks the app. A fresh app process also requires authentication. Timed presets keep this session unlocked briefly while switching apps or choosing a backup file.", color = LocalGlass.current.textFaint)
            }
            GhostButton(if (configured == true) "Change app PIN" else "Set up app PIN", enabled = !busy) {
                action = PinSettingsAction.SET
            }
            if (configured == true) GhostButton("Remove app PIN", enabled = !busy) {
                action = PinSettingsAction.REMOVE
            }
            Text("The PIN is stored as a salted PBKDF2 verifier inside a separate encrypted, device-bound vault. Repeated failures impose increasing delays. Keep your PIN safe: biometrics do not replace it, and changing or removing it requires the current PIN.", color = LocalGlass.current.textFaint)
        }
    } }
    action?.let { pending ->
        key(pending) {
            PinSettingsDialog(pending, configured == true, store, onDismiss = { action = null }, onSaved = {
                if (pending == PinSettingsAction.SET) configured = true
                if (pending == PinSettingsAction.REMOVE) configured = false
                lock = APP.prefs.appLockEnabled
                biometric = APP.prefs.biometricUnlockEnabled
                biometricError = null; action = null; onChanged()
            })
        }
    }
}

private enum class PinSettingsAction { SET, ENABLE_LOCK, DISABLE_LOCK, REMOVE }

/** Confirmation is independent of the settings scroll position; only the form body scrolls. */
@Composable
private fun PinSettingsDialog(action: PinSettingsAction, hasPin: Boolean, store: AppPinStore,
    onDismiss: () -> Unit, onSaved: () -> Unit) {
    val runner = rememberRunner()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val setting = action == PinSettingsAction.SET
    val title = when (action) {
        PinSettingsAction.SET -> if (hasPin) "Change app PIN" else "Set up app PIN"
        PinSettingsAction.REMOVE -> "Remove app PIN"
        else -> "App lock"
    }
    val confirm = when (action) {
        PinSettingsAction.SET -> "Save app PIN"
        PinSettingsAction.REMOVE -> "Remove PIN and disable app lock"
        else -> "Confirm with app PIN"
    }
    val valid = (!hasPin || current.length >= 8) && (!setting || (next.length >= 8 && next == repeat))
    AlertDialog(onDismissRequest = { if (!runner.busy) onDismiss() }, containerColor = LocalGlass.current.surface,
        title = { Text(UiLocale.text(title)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                runner.error?.let { Text(it, color = LocalGlass.current.err) }
                if (!setting) Text(UiLocale.text(when (action) {
                    PinSettingsAction.REMOVE -> "Removing the PIN also turns off app lock and biometrics. Your panel profiles remain encrypted."
                    PinSettingsAction.DISABLE_LOCK -> "Turn off app lock and biometric unlock? Your PIN will be kept for re-enabling the lock."
                    else -> "Verify your app PIN to enable app lock."
                }), color = LocalGlass.current.textDim)
                if (hasPin) PinField("Current app PIN", current, { current = it }, !runner.busy)
                if (setting) {
                    PinField("New app PIN", next, { next = it }, !runner.busy)
                    PinField("Confirm new app PIN", repeat, { repeat = it }, !runner.busy)
                }
            }
        }, confirmButton = {
            TextButton(enabled = !runner.busy && valid, onClick = { runner.go {
                val entered = current.toCharArray(); val pin = next.toCharArray()
                current = ""; next = ""; repeat = ""
                try {
                    withContext(NonCancellable + Dispatchers.IO) {
                        when (action) {
                            PinSettingsAction.SET -> { store.set(pin, entered); APP.prefs.setLockState(true) }
                            PinSettingsAction.REMOVE -> store.remove(entered) { APP.prefs.setLockState(false, false) }
                            else -> {
                                check(store.verify(entered)) { "Incorrect app PIN" }
                                APP.prefs.setLockState(action == PinSettingsAction.ENABLE_LOCK)
                            }
                        }
                    }
                } finally { entered.fill('\u0000'); pin.fill('\u0000') }
                if (setting) ToastBus.show("App PIN saved; app lock enabled")
                onSaved()
            } }) { Text(UiLocale.text(confirm)) }
        }, dismissButton = {
            TextButton(enabled = !runner.busy, onClick = onDismiss) { Text(UiLocale.text("Cancel")) }
        })
}

@Composable
fun PinUnlockPanel(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current as? FragmentActivity
    val store = remember { AppPinStore(context.applicationContext) }
    val runner = rememberRunner()
    var configured by remember { mutableStateOf<Boolean?>(null) }
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var legacyVerified by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }
    val busy = runner.busy || prompting
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                legacyVerified = false; pin = ""; repeat = ""
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun load() = runner.go { configured = withContext(Dispatchers.IO) { store.configured() } }
    LaunchedEffect(Unit) { load() }
    val mode = UnlockPolicy.mode(configured, APP.prefs.pinFirstLock, APP.prefs.biometricUnlockEnabled)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        runner.error?.let { Text(it, color = LocalGlass.current.err) }
        when (mode) {
            UnlockMode.PIN, UnlockMode.PIN_AND_BIOMETRIC -> {
                PinField("App PIN", pin, { pin = it }, !busy)
                PrimaryButton("Unlock with app PIN", enabled = !busy && pin.length >= 8, loading = runner.busy) { runner.go {
                    val entered = pin.toCharArray(); pin = ""
                    try { check(withContext(Dispatchers.IO) { store.verify(entered) }) { "Incorrect app PIN" } }
                    finally { entered.fill('\u0000') }
                    APP.prefs.pinFirstLock = true
                    onUnlocked()
                } }
                if (mode == UnlockMode.PIN_AND_BIOMETRIC) GhostButton("Unlock with biometrics", enabled = !busy && activity != null) {
                    prompting = true
                    runBiometricGate(requireNotNull(activity), onSuccess = {
                        prompting = false; APP.prefs.pinFirstLock = true; onUnlocked()
                    }, onError = { prompting = false; ToastBus.show(it) })
                }
            }
            UnlockMode.LEGACY_MIGRATION -> {
                Text("Upgrade your app lock: authenticate with your existing Android lock, then create an app PIN. Your saved profiles will stay locked until setup is complete.", color = LocalGlass.current.textDim)
                if (!legacyVerified) GhostButton("Verify existing app lock", enabled = !busy && activity != null) {
                    prompting = true
                    runBiometricGate(requireNotNull(activity), onSuccess = { prompting = false; legacyVerified = true },
                        onError = { prompting = false; ToastBus.show(it) }, legacyMigration = true)
                } else {
                    PinField("New app PIN", pin, { pin = it }, !busy)
                    PinField("Confirm new app PIN", repeat, { repeat = it }, !busy)
                    PrimaryButton("Save PIN and unlock", enabled = !busy && pin.length >= 8 && pin == repeat, loading = runner.busy) { runner.go {
                        val entered = pin.toCharArray(); pin = ""; repeat = ""
                        try { withContext(NonCancellable + Dispatchers.IO) {
                            store.set(entered, charArrayOf())
                            APP.prefs.setLockState(true, false)
                        } }
                        finally { entered.fill('\u0000') }
                        ToastBus.show("PIN saved. You can enable biometrics in App settings.")
                        onUnlocked()
                    } }
                }
            }
            UnlockMode.PIN_UNAVAILABLE -> Text("The protected PIN record is missing. Access remains locked. Do not clear app data or uninstall; preserve this installation for recovery.", color = LocalGlass.current.err)
            UnlockMode.LOADING -> Text("Reading protected PIN storage…", color = LocalGlass.current.textDim)
        }
        if (mode == UnlockMode.LOADING || mode == UnlockMode.PIN_UNAVAILABLE) GhostButton("Retry PIN storage", enabled = !busy) { load() }
    }
}
