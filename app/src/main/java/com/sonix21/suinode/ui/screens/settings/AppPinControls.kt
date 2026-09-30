package com.sonix21.suinode.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
    val activity = androidx.activity.compose.LocalActivity.current as? FragmentActivity
    val store = remember { AppPinStore(context.applicationContext) }
    val runner = rememberRunner()
    var configured by remember { mutableStateOf<Boolean?>(null) }
    var lock by remember { mutableStateOf(APP.prefs.appLockEnabled) }
    var biometric by remember { mutableStateOf(APP.prefs.biometricUnlockEnabled) }
    var lockAfter by remember { mutableStateOf(APP.prefs.autoLock) }
    var expanded by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<String?>(null) }
    var prompting by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val busy = runner.busy || prompting
    fun clearInputs() { current = ""; next = ""; repeat = ""; action = null; expanded = false }
    fun refresh() = runner.go { configured = withContext(Dispatchers.IO) { store.configured() } }
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
                    if (!busy) { action = if (enable) "lock-on" else "lock-off"; current = ""; expanded = false }
                }, subtitle = "Changes require your current app PIN")
                if (lock) {
                    SwitchRow("Biometric unlock", biometric, { enable ->
                        if (!busy) {
                            if (enable) { action = "biometric-on"; current = ""; expanded = false }
                            else runner.go {
                                withContext(NonCancellable + Dispatchers.IO) { APP.prefs.setLockState(lock, false) }
                                biometric = false; onChanged()
                            }
                        }
                    }, subtitle = "Requires an app PIN and Android strong biometrics; PIN fallback stays available")
                }
            }
            if (lock) {
                SelectField("Lock after leaving", lockAfter, AutoLock.entries.map { Opt(it.label, it) }, clearable = false,
                    onChange = { policy -> if (policy != null) { lockAfter = policy; APP.prefs.autoLock = policy } })
                Text("Screen-off always locks the app. A fresh app process also requires authentication. Timed presets keep this session unlocked briefly while switching apps or choosing a backup file.", color = LocalGlass.current.textFaint)
            }
            GhostButton(if (expanded) "Close PIN settings" else if (configured == true) "Change app PIN" else "Set up app PIN", enabled = !busy) {
                val open = !expanded; clearInputs(); expanded = open
            }
            if (expanded) {
                if (configured == true) PinField("Current app PIN", current, { current = it }, !busy)
                PinField("New app PIN", next, { next = it }, !busy)
                PinField("Confirm new app PIN", repeat, { repeat = it }, !busy)
                PrimaryButton("Save app PIN", enabled = !busy && next.length >= 8 && next == repeat, loading = runner.busy) { runner.go {
                    val pin = next.toCharArray(); val previous = current.toCharArray()
                    try { withContext(NonCancellable + Dispatchers.IO) {
                        store.set(pin, previous)
                        APP.prefs.setLockState(true)
                    } }
                    finally { pin.fill('\u0000'); previous.fill('\u0000') }
                    configured = true; lock = true; clearInputs(); onChanged()
                    ToastBus.show("App PIN saved; app lock enabled")
                } }
            }
            if (configured == true) GhostButton("Remove app PIN", enabled = !busy) { clearInputs(); action = "remove" }
            action?.let { pending ->
                Text(when (pending) {
                    "remove" -> "Removing the PIN also turns off app lock and biometrics. Your panel profiles remain encrypted."
                    "lock-off" -> "Turn off app lock and biometric unlock? Your PIN will be kept for re-enabling the lock."
                    "biometric-on" -> "Verify your app PIN, then confirm your biometric."
                    else -> "Verify your app PIN to enable app lock."
                }, color = LocalGlass.current.textDim)
                PinField("Current app PIN", current, { current = it }, !busy)
                PrimaryButton(if (pending == "remove") "Remove PIN and disable app lock" else "Confirm with app PIN",
                    enabled = !busy && current.length >= 8, loading = runner.busy) { runner.go {
                    val entered = current.toCharArray(); current = ""
                    try {
                        withContext(NonCancellable + Dispatchers.IO) {
                            if (pending == "remove") store.remove(entered) {
                                APP.prefs.setLockState(false, false)
                            }
                            else check(store.verify(entered)) { "Incorrect app PIN" }
                            if (pending == "lock-on" || pending == "lock-off") {
                                APP.prefs.setLockState(pending == "lock-on")
                            }
                        }
                    } finally { entered.fill('\u0000') }
                    if (pending == "biometric-on") {
                        checkNotNull(activity) { "Biometric authentication unavailable" }
                        prompting = true
                        runBiometricGate(activity, onSuccess = {
                            prompting = false
                            runner.go {
                                withContext(NonCancellable + Dispatchers.IO) {
                                    check(store.configured()) { "Set up an app PIN first" }
                                    APP.prefs.setLockState(true, true)
                                }
                                biometric = true; action = null; onChanged()
                            }
                        }, onError = { prompting = false; ToastBus.show(it) })
                    } else {
                        lock = pending == "lock-on"
                        if (!lock) biometric = false
                        if (pending == "remove") configured = false
                        clearInputs(); onChanged()
                    }
                } }
                GhostButton("Cancel", enabled = !busy) { clearInputs() }
            }
            Text("The PIN is stored as a salted PBKDF2 verifier inside a separate encrypted, device-bound vault. Repeated failures impose increasing delays. Keep your PIN safe: biometrics do not replace it, and changing or removing it requires the current PIN.", color = LocalGlass.current.textFaint)
        }
    } }
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
