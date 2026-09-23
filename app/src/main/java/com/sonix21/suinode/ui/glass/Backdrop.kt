package com.sonix21.suinode.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Stationary refracted light. Motion belongs to interactions, not an idle render loop. */
@Composable
fun LiquidBackdrop(modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    Canvas(modifier.fillMaxSize()) {
        if (g.isAmoled) {
            drawRect(Color.Black)
            return@Canvas
        }
        drawRect(Brush.verticalGradient(listOf(g.bgTop, g.bgBottom)))
        val light = Offset(size.width * 1.05f, size.height * 0.16f)
        drawRect(Brush.radialGradient(listOf(g.blob1.copy(alpha = if (g.isDark) 0.11f else 0.27f), Color.Transparent), light, size.width * 0.95f))
        val ice = Offset(-size.width * 0.15f, size.height * 0.62f)
        drawRect(Brush.radialGradient(listOf(g.blob2.copy(alpha = if (g.isDark) 0.065f else 0.19f), Color.Transparent), ice, size.width * 0.8f))
        val gap = 24.dp.toPx()
        for (x in 0..(size.width / gap).toInt()) for (y in 0..(size.height / gap).toInt()) {
            drawCircle(g.text.copy(alpha = 0.035f), 0.55.dp.toPx(), Offset(x * gap, y * gap))
        }
    }
}

@Composable
fun GlassScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { LiquidBackdrop(); content() }
}
