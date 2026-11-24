package com.playly.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val action = 56.dp
    val thumbnailWidth = 72.dp
    val readingWidth = 600.dp
    val railBreakpoint = 720.dp
}

private val Paper = Color(0xFFF8F6EF)
private val Ink = Color(0xFF151421)
private val Lime = Color(0xFFD8FA76)
private val Plum = Color(0xFF4D338B)

internal val accents = listOf(
    0xFFC6FF4D.toInt(), 0xFFAA91FF.toInt(), 0xFF9F6BFF.toInt(), 0xFF8DEBC7.toInt(),
    0xFFA3DBFA.toInt(), 0xFFFF8ABA.toInt(), 0xFFFFA068.toInt(),
)

internal val backgrounds = listOf(
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF151D39.toInt(), 0xFF15332E.toInt(),
    0xFF372445.toInt(), 0xFFF4EADD.toInt(), 0xFFDCEEE8.toInt(),
)

internal fun Appearance.backgroundColor(): Color = when (mode) {
    ThemeMode.LIGHT -> Paper
    ThemeMode.DARK -> Ink
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
    extraSmall = RoundedCornerShape(Space.sm),
    small = RoundedCornerShape(Space.md),
    medium = RoundedCornerShape(Space.lg),
    large = RoundedCornerShape(Space.xl),
    extraLarge = RoundedCornerShape(Space.xxl),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val typography = Typography().let { defaults ->
    val display = defaults.headlineLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 43.sp,
        letterSpacing = (-1.7).sp,
    )
    defaults.copy(
        headlineLarge = display,
        headlineLargeEmphasized = display,
        headlineMedium = defaults.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.65).sp),
        titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.45).sp),
        titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

private val lightBrand = lightColorScheme(
    primary = Plum, onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DFFF), onPrimaryContainer = Color(0xFF2C165E),
    secondary = Color(0xFF47600C), onSecondary = Color.White,
    secondaryContainer = Lime, onSecondaryContainer = Color(0xFF253600),
    tertiary = Color(0xFF924635), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCE), onTertiaryContainer = Color(0xFF641F12),
    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFE8E3DB), onSurfaceVariant = Color(0xFF565160),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1EEE6),
    surfaceContainer = Color(0xFFECE8DF), surfaceContainerHigh = Color(0xFFE7E2D8),
    surfaceContainerHighest = Color(0xFFDED8CE), surfaceBright = Paper, surfaceDim = Color(0xFFE1DCD2),
    inverseSurface = Ink, inverseOnSurface = Paper, inversePrimary = Color(0xFFD8C3FF),
    outline = Color(0xFF77717F), outlineVariant = Color(0xFFD2CBC8),
    primaryFixed = Color(0xFFE9DFFF), primaryFixedDim = Color(0xFFD8C3FF),
    onPrimaryFixed = Color(0xFF211045), onPrimaryFixedVariant = Plum,
    secondaryFixed = Lime, secondaryFixedDim = Color(0xFFBDDD5B),
    onSecondaryFixed = Color(0xFF1D2D00), onSecondaryFixedVariant = Color(0xFF3A5203),
)

private val darkBrand = darkColorScheme(
    primary = Lime, onPrimary = Color(0xFF253600),
    primaryContainer = Color(0xFF354616), onPrimaryContainer = Color(0xFFE1FB9C),
    secondary = Color(0xFFD8C3FF), onSecondary = Color(0xFF3E266B),
    secondaryContainer = Color(0xFF4D366D), onSecondaryContainer = Color(0xFFEFDDFF),
    tertiary = Color(0xFFFFB9A6), onTertiary = Color(0xFF561B10),
    tertiaryContainer = Color(0xFF6A3125), onTertiaryContainer = Color(0xFFFFDBCE),
    background = Ink, onBackground = Paper, surface = Ink, onSurface = Paper,
    surfaceVariant = Color(0xFF343141), onSurfaceVariant = Color(0xFFC9C2D4),
    surfaceContainerLowest = Color(0xFF100F1A), surfaceContainerLow = Color(0xFF1C1A29),
    surfaceContainer = Color(0xFF242232), surfaceContainerHigh = Color(0xFF2C293B),
    surfaceContainerHighest = Color(0xFF353144), surfaceBright = Color(0xFF3A3649), surfaceDim = Ink,
    inverseSurface = Paper, inverseOnSurface = Ink, inversePrimary = Color(0xFF47600C),
    outline = Color(0xFF9891A6), outlineVariant = Color(0xFF444054),
    primaryFixed = lightBrand.primaryFixed, primaryFixedDim = lightBrand.primaryFixedDim,
    onPrimaryFixed = lightBrand.onPrimaryFixed, onPrimaryFixedVariant = lightBrand.onPrimaryFixedVariant,
    secondaryFixed = Lime, secondaryFixedDim = lightBrand.secondaryFixedDim,
    onSecondaryFixed = lightBrand.onSecondaryFixed, onSecondaryFixedVariant = lightBrand.onSecondaryFixedVariant,
)

private val LocalPlayLyAccent = staticCompositionLocalOf { Color.Unspecified }

internal val playLyAccent: Color
    @Composable get() = LocalPlayLyAccent.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayLyTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val scheme = remember(appearance) { playLyColorScheme(appearance) }
    val accent = if (appearance.mode == ThemeMode.CUSTOM) Color(appearance.accent) else scheme.primary
    CompositionLocalProvider(LocalPlayLyAccent provides accent) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

internal fun playLyColorScheme(appearance: Appearance): ColorScheme {
    when (appearance.mode) {
        ThemeMode.LIGHT -> return lightBrand
        ThemeMode.DARK -> return darkBrand
        ThemeMode.CUSTOM -> Unit
    }
    val background = appearance.backgroundColor()
    val foreground = accentForeground(background)
    val dark = foreground == Color.White
    val accent = Color(appearance.accent)
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
    val base = if (dark) darkBrand else lightBrand
    val error = readableAccent(base.error, contrastSurface)
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
internal fun PlayLyMark(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.secondary) {
    Icon(
        painter = painterResource(R.drawable.ic_playly_mark),
        contentDescription = null,
        modifier = modifier.size(Space.xxl),
        tint = tint,
    )
}
