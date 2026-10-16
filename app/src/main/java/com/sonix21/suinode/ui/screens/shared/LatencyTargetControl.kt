package com.sonix21.suinode.ui.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.LatencyTarget
import com.sonix21.suinode.core.UiLocale
import com.sonix21.suinode.ui.glass.*

@Composable
fun LatencyTargetControl(target: LatencyTarget, editable: Boolean, onChange: (LatencyTarget) -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwitchRow("Custom test URL", target.enabled, { onChange(target.copy(enabled = it)) }, enabled = editable)
            if (target.enabled) {
                GlassTextField("Test URL", target.url, { onChange(target.copy(url = it)) },
                    hint = "https://www.gstatic.com/generate_204", enabled = editable)
                val invalid = runCatching { target.link() }.isFailure
                Text(UiLocale.text(if (invalid) "Enter a valid HTTP or HTTPS test URL."
                    else "The panel runs this probe. Leave blank for the core default. Shared between Outbounds and Endpoints for this session only."),
                    color = if (invalid) LocalGlass.current.err else LocalGlass.current.textFaint)
            }
        }
    }
}
