package com.sonix21.suinode.ui.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import com.sonix21.suinode.APP
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape

@Immutable
data class GlassColors(
    val isDark: Boolean,
    // background
    val bgTop: Color,
    val bgBottom: Color,
    val blob1: Color,
    val blob2: Color,
    val blob3: Color,
    // surfaces
    val glassHi: Color,     // top of card gradient (white-ish)
    val glassLo: Color,     // bottom of card gradient
    val strokeHi: Color,    // top border highlight
    val strokeLo: Color,    // bottom border
    val innerFill: Color,   // input fields fill
    // content
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    // accents
    val teal: Color,
    val blue: Color,
    val violet: Color,
    val pink: Color,
    val orange: Color,
    val green: Color,
    val red: Color,
    val isAmoled: Boolean = false,
) {
    val surface: Color get() = if (isAmoled) Color(0xFF101010) else if (isDark) Color(0xFF202420) else Color(0xFFFDFEFA)
    val onAccent: Color get() = if (isDark) Color(0xFF172009) else Color.White
    val accentBrush: Brush get() = Brush.linearGradient(listOf(blue, teal))
    val ok get() = green
    val warn get() = orange
    val err get() = red
}

val DarkGlass = GlassColors(
    isDark = true,
    bgTop = Color(0xFF151914),
    bgBottom = Color(0xFF0B0E0C),
    blob1 = Color(0xFFC6E89A),
    blob2 = Color(0xFF8BCAC0),
    blob3 = Color(0xFF789186),
    glassHi = Color(0xFFFFFFFF),
    glassLo = Color(0xFFFFFFFF),
    strokeHi = Color(0x30F0F6E9),
    strokeLo = Color(0x17F0F6E9),
    innerFill = Color(0x0AF0F6E9),
    text = Color(0xFFF2F4EA),
    textDim = Color(0xFFD3D8CC),
    textFaint = Color(0xFFA0AA9C),
    teal = Color(0xFFD2EC90),
    blue = Color(0xFF9AD6CE),
    violet = Color(0xFFB9C7E9),
    pink = Color(0xFFF472B6),
    orange = Color(0xFFFFB454),
    green = Color(0xFF4ADE80),
    red = Color(0xFFF87171),
)

val LightGlass = GlassColors(
    isDark = false,
    bgTop = Color(0xFFF0F3E9),
    bgBottom = Color(0xFFE8ECE5),
    blob1 = Color(0xFFB2CE84),
    blob2 = Color(0xFFB7DBD5),
    blob3 = Color(0xFFDCE4D5),
    glassHi = Color(0xFFFFFFFF),
    glassLo = Color(0xFFF2F4FB),
    strokeHi = Color(0xB3FFFFFF),
    strokeLo = Color(0x33808CA8),
    innerFill = Color(0x66FFFFFF),
    text = Color(0xFF192219),
    textDim = Color(0xFF3C493B),
    textFaint = Color(0xFF5C6A58),
    teal = Color(0xFF48651E),
    blue = Color(0xFF236A64),
    violet = Color(0xFF52678A),
    pink = Color(0xFFDB2777),
    orange = Color(0xFFD97706),
    green = Color(0xFF16A34A),
    red = Color(0xFFDC2626),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

@Composable
fun GlassTheme(dark: Boolean, dynamicColors: Boolean, amoled: Boolean,
    palette: com.sonix21.suinode.core.AccentPalette, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dynamic = dynamicColors && Build.VERSION.SDK_INT >= 31
    val primary = Color(if (dark) palette.dark else palette.light)
    val base = (if (dark) DarkGlass else LightGlass).let {
        if (amoled) it.copy(bgTop = Color.Black, bgBottom = Color.Black, isAmoled = true) else it
    }
    val scheme = when {
        dynamic -> (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).let {
            if (amoled) it.copy(background = Color.Black, surface = base.surface) else it
        }
        dark -> darkColorScheme(primary = primary, onPrimary = base.onAccent,
            secondary = primary, tertiary = primary,
            primaryContainer = primary.copy(alpha = 0.16f), onPrimaryContainer = primary,
            background = base.bgBottom, surface = base.surface, onSurface = base.text,
            onSurfaceVariant = base.textFaint, outline = base.strokeHi, error = base.err)
        else -> lightColorScheme(primary = primary, onPrimary = base.onAccent,
            secondary = primary, tertiary = primary,
            primaryContainer = primary.copy(alpha = 0.14f), onPrimaryContainer = primary,
            background = base.bgBottom, surface = base.surface, onSurface = base.text,
            onSurfaceVariant = base.textFaint, outline = base.strokeHi, error = base.err)
    }
    val colors = base.copy(teal = scheme.primary, blue = scheme.secondary, violet = scheme.tertiary,
        blob1 = scheme.primary, blob2 = scheme.secondary, blob3 = scheme.tertiary)
    MaterialTheme(colorScheme = scheme, typography = Typography(
        headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    ), shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
        CompositionLocalProvider(LocalGlass provides colors, LocalIsDark provides dark) { content() }
    }
}

val LocalIsDark = staticCompositionLocalOf { true }

@Composable
fun rememberGlass(dark: Boolean?): GlassColors {
    val sysDark = isSystemInDarkTheme()
    return if (dark == true) DarkGlass else if (dark == false) LightGlass else if (sysDark) DarkGlass else LightGlass
}
