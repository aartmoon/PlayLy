package com.playly.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.playly.app.data.Appearance
import com.playly.app.data.ThemeMode

internal object Space {
    val outline = 1.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val touch = 48.dp
}

internal val accents = listOf(
    0xFFC6FF4D.toInt(), 0xFFAA91FF.toInt(), 0xFF9F6BFF.toInt(), 0xFF8DEBC7.toInt(),
    0xFFA3DBFA.toInt(), 0xFFFF8ABA.toInt(), 0xFFFFA068.toInt(),
)

internal fun Appearance.isDark(): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.CUSTOM -> customDark
}

internal fun accentForeground(color: Color) =
    if (color.luminance() >= 0.179f) Color.Black else Color.White

private val shapes = Shapes(
    small = RoundedCornerShape(Space.md),
    medium = RoundedCornerShape(Space.lg),
    large = RoundedCornerShape(Space.xl),
)

private val typography = Typography().let { defaults ->
    defaults.copy(
        headlineLarge = defaults.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = defaults.headlineMedium.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
internal fun PlayLyTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = appearance.isDark()
    val custom = appearance.mode == ThemeMode.CUSTOM
    val primary = when {
        custom -> Color(appearance.accent)
        dark -> Color(0xFFC6FF4D)
        else -> Color(0xFF3A6000)
    }
    val onPrimary = accentForeground(primary)
    val onSurfacePrimary = if (custom) {
        val contrast = if (dark) (primary.luminance() + .05f) / (Color(0xFF10110F).luminance() + .05f)
        else (Color(0xFFFAFBF4).luminance() + .05f) / (primary.luminance() + .05f)
        if (contrast >= 4.5f) primary else if (dark) Color(0xFFF5F5EC) else Color(0xFF20221B)
    } else primary
    val scheme = if (dark) darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = Color(0xFF303A19), onPrimaryContainer = Color(0xFFE5F8B9),
        secondary = onSurfacePrimary, secondaryContainer = Color(0xFF25291E), onSecondaryContainer = onSurfacePrimary,
        background = Color(0xFF10110F), onBackground = Color(0xFFF5F5EC),
        surface = Color(0xFF10110F), onSurface = Color(0xFFF5F5EC),
        surfaceContainer = Color(0xFF191B17), surfaceContainerHigh = Color(0xFF22241F),
        surfaceVariant = Color(0xFF272A23), onSurfaceVariant = Color(0xFFBEC1B6),
        outline = Color(0xFF828578), outlineVariant = Color(0xFF34372E),
    ) else lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = Color(0xFFDFF6AF), onPrimaryContainer = Color(0xFF1D3007),
        secondary = onSurfacePrimary, secondaryContainer = Color(0xFFE7ECD8), onSecondaryContainer = onSurfacePrimary,
        background = Color(0xFFFAFBF4), onBackground = Color(0xFF20221B),
        surface = Color(0xFFFAFBF4), onSurface = Color(0xFF20221B),
        surfaceContainer = Color(0xFFF0F1E8), surfaceContainerHigh = Color(0xFFEAECE2),
        surfaceVariant = Color(0xFFE4E8D9), onSurfaceVariant = Color(0xFF4B5143),
        outline = Color(0xFF717869), outlineVariant = Color(0xFFCCD0C0),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
