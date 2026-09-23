package com.sonix21.suinode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.ui.glass.GhostButton
import com.sonix21.suinode.ui.glass.GlassCard
import com.sonix21.suinode.ui.glass.GlassTextField
import com.sonix21.suinode.ui.glass.JsonEditSignal
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.SwitchRow
import com.sonix21.suinode.ui.glass.ToastBus
import org.json.JSONObject

/** Lossless escape hatch for new sing-box fields not yet represented by a visual control. */
@Composable
fun AdvancedJsonCard(value: JSONObject, onApply: (JSONObject) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value.toString(2)) }
    GlassCard(contentPadding = 14.dp) {
        SwitchRow(
            label = "Advanced JSON",
            checked = open,
            onChange = { open = it; if (it) text = value.toString(2) },
            subtitle = "Edit every field without losing options introduced by newer panel versions",
        )
        if (open) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                GlassTextField("JSON object", text, { text = it }, singleLine = false, minLines = 8, maxLines = 24)
                GhostButton("Validate & apply", tint = LocalGlass.current.teal) {
                    runCatching { JSONObject(text) }
                        .onSuccess { onApply(it); text = it.toString(2); JsonEditSignal.bump(); ToastBus.show("JSON applied") }
                        .onFailure { ToastBus.show("Invalid JSON: ${it.message}") }
                }
            }
        }
    }
}
