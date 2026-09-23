package com.sonix21.suinode.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.APP
import com.sonix21.suinode.core.AccentPalette
import com.sonix21.suinode.ui.glass.LocalGlass

@Composable
fun AccentSelector(wallpaperColors: Boolean, onChange: () -> Unit) {
    val g = LocalGlass.current
    var selected by remember { mutableStateOf(APP.prefs.accent) }
    Text("Accent · ${if (wallpaperColors) "Wallpaper" else selected.label}", color = g.textDim, fontSize = 13.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()) {
        AccentPalette.entries.forEach { palette ->
            val checked = !wallpaperColors && palette == selected
            val color = Color(if (g.isDark) palette.dark else palette.light)
            Box(
                Modifier.size(48.dp).selectable(checked, role = Role.RadioButton) {
                    selected = palette; APP.prefs.accent = palette; onChange()
                }.semantics { contentDescription = "${palette.label} accent" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp).background(color, CircleShape)
                    .then(if (checked) Modifier.border(2.dp, g.text, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center) {
                    if (checked) Icon(Icons.Filled.Check, null, tint = if (g.isDark) Color(0xFF142014) else Color.White,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
