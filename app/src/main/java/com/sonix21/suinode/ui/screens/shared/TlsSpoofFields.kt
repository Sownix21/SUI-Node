package com.sonix21.suinode.ui.screens.shared

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

@Composable
fun TlsSpoofFields(tls: J) {
    JsonEditSignal.revision
    if (tls.has("reality")) return
    SwitchRow("SNI spoofing", tls.has("spoof"), { Panel161.setSpoof(tls.o, if (it) "" else null) })
    if (tls.has("spoof")) {
        GlassTextField("Spoofed server name", tls.str("spoof"), { Panel161.setSpoof(tls.o, it) })
        if (tls.str("spoof").isNotBlank()) SelectField("Spoof method", tls.str("spoof_method", "wrong-sequence"),
            Panel161.spoofMethods.map { Opt(it.removePrefix("wrong-").replaceFirstChar(Char::uppercase), it) },
            clearable = false, onChange = { tls["spoof_method"] = it ?: "wrong-sequence" })
        Text("Requires a compatible core. Wrong timestamp is unavailable on macOS.", color = LocalGlass.current.textFaint)
    }
}
