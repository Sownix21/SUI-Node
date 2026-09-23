package com.sonix21.suinode.ui.glass

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.UiLocale

enum class HeaderActionType { New, Save, Delete }

/** Explicit action semantics: opening a new draft is +; submitting a form is a tick. */
@Composable
fun HeaderPrimaryAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    type: HeaderActionType = HeaderActionType.Save,
    onClick: () -> Unit,
) {
    val label = UiLocale.text(text.removePrefix("+ "))
    val icon = when (type) {
        HeaderActionType.New -> Icons.Filled.Add
        HeaderActionType.Save -> Icons.Filled.Check
        HeaderActionType.Delete -> Icons.Filled.Delete
    }
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        FilledIconButton(onClick = { onClick(); JsonEditSignal.bump() }, enabled = enabled && !loading,
            modifier = modifier.size(48.dp), shape = RoundedCornerShape(16.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary)
            else Icon(icon, label, Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else LocalGlass.current.textFaint)
        }
    }
}
