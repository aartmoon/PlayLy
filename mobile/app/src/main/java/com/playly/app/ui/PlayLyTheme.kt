package com.playly.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.playly.app.R
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

internal val backgrounds = listOf(
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF151D39.toInt(), 0xFF15332E.toInt(),
    0xFF372445.toInt(), 0xFFF4EADD.toInt(), 0xFFDCEEE8.toInt(),
)

internal fun Appearance.backgroundColor(): Color = when (mode) {
    ThemeMode.LIGHT -> Color.White
    ThemeMode.DARK -> Color.Black
    ThemeMode.CUSTOM -> Color(background)
}

internal fun Appearance.isDark() = accentForeground(backgroundColor()) == Color.White

internal fun contrastRatio(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
}

internal fun accentForeground(color: Color): Color =
    if (contrastRatio(color, Color.Black) >= contrastRatio(color, Color.White)) Color.Black else Color.White

private fun readableAccent(accent: Color, background: Color): Color {
    if (contrastRatio(accent, background) >= 4.5f) return accent
    val foreground = accentForeground(background)
    var low = 0f
    var high = 1f
    repeat(12) {
        val fraction = (low + high) / 2
        if (contrastRatio(lerp(accent, foreground, fraction), background) >= 4.5f) high = fraction
        else low = fraction
    }
    return lerp(accent, foreground, high)
}

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

private val LocalPlayLyAccent = staticCompositionLocalOf { Color.Unspecified }

internal val playLyAccent: Color
    @Composable get() = LocalPlayLyAccent.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary

@Composable
internal fun PlayLyTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val scheme = remember(appearance) { playLyColorScheme(appearance) }
    val accent = if (appearance.mode == ThemeMode.CUSTOM) Color(appearance.accent) else scheme.primary
    CompositionLocalProvider(LocalPlayLyAccent provides accent) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}

internal fun playLyColorScheme(appearance: Appearance): ColorScheme {
    val background = appearance.backgroundColor()
    val foreground = accentForeground(background)
    val dark = foreground == Color.White
    val accent = if (appearance.mode == ThemeMode.CUSTOM) Color(appearance.accent) else foreground
    val inverseForeground = accentForeground(foreground)
    fun surface(fraction: Float): Color {
        val tinted = tint(background, foreground, fraction)
        return if (contrastRatio(tinted, foreground) >= 4.5f) tinted else tint(background, inverseForeground, fraction)
    }
    val lowContainer = surface(.04f)
    val container = surface(.08f)
    val highContainer = surface(.12f)
    val variant = surface(.16f)
    val contrastSurface = listOf(background, lowContainer, container, highContainer, variant)
        .minBy { contrastRatio(it, foreground) }
    val primary = readableAccent(accent, contrastSurface)
    val accentContainer = tint(container, accent, .19f)
    val onAccentContainer = readableAccent(accent, accentContainer)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val error = if (appearance.mode == ThemeMode.CUSTOM) readableAccent(base.error, contrastSurface) else foreground
    return base.copy(
        primary = primary,
        onPrimary = accentForeground(primary),
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        inversePrimary = readableAccent(accent, foreground),
        secondary = primary,
        onSecondary = accentForeground(primary),
        secondaryContainer = accentContainer,
        onSecondaryContainer = onAccentContainer,
        tertiary = primary,
        onTertiary = accentForeground(primary),
        tertiaryContainer = accentContainer,
        onTertiaryContainer = onAccentContainer,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceContainerLowest = background,
        surfaceContainerLow = lowContainer,
        surfaceContainer = container,
        surfaceContainerHigh = highContainer,
        surfaceContainerHighest = variant,
        surfaceDim = if (dark) background else variant,
        surfaceBright = if (dark) variant else background,
        surfaceVariant = variant,
        onSurfaceVariant = readableAccent(tint(background, foreground, .68f), variant),
        surfaceTint = primary,
        inverseSurface = foreground,
        inverseOnSurface = inverseForeground,
        outline = tint(background, foreground, .55f),
        outlineVariant = tint(background, foreground, .26f),
        error = error,
        onError = accentForeground(error),
        errorContainer = variant,
        onErrorContainer = accentForeground(variant),
    )
}

// Mix UI surfaces in sRGB so a small fraction gives a small visual tint.
private fun tint(from: Color, to: Color, fraction: Float) = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
)

@Composable
internal fun PlayLyMark(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_playly_mark),
        contentDescription = null,
        modifier = modifier.size(Space.xxl),
        tint = MaterialTheme.colorScheme.secondary,
    )
}
