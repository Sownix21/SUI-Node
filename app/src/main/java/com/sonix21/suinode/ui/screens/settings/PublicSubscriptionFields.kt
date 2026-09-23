package com.sonix21.suinode.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Text
import com.sonix21.suinode.core.*
import com.sonix21.suinode.ui.glass.*

@Composable
fun PublicSubscriptionFields(j: J, generatedUri: String) {
    JsonEditSignal.revision
    var enabled by rememberSaveable { mutableStateOf(j.str("subURI").isNotBlank()) }
    SwitchRow("Use public subscription URL / reverse proxy", enabled || j.str("subURI").isNotBlank(), {
        enabled = it
        j.setStr("subURI", if (it) generatedUri else "", false)
    }, subtitle = "Uses the panel's Public subscription URI override for both QR codes and copied links")
    if (enabled || j.str("subURI").isNotBlank()) {
        GlassTextField("Public subscription URI", j.str("subURI"), { j.setStr("subURI", it, false) })
        Text("Use your external URL, for example https://vpn.example.com/sub/. Keep the path your proxy exposes. This does not change the listener port. Save with the tick to update the panel; your proxy must already forward that URL.", color = LocalGlass.current.textFaint)
        if (j.str("subURI").isBlank()) Text("An empty URL leaves the panel's generated subscription URL in use.", color = LocalGlass.current.textFaint)
    }
}
