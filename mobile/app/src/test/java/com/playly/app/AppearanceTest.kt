package com.playly.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.playly.app.data.Appearance
import com.playly.app.data.ThemeMode
import com.playly.app.ui.accentForeground
import com.playly.app.ui.contrastRatio
import com.playly.app.ui.isDark
import com.playly.app.ui.parseHexColor
import com.playly.app.ui.playLyColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {
    @Test
    fun fixedThemesUseBrandColorsWithReadableText() {
        val customColors = Appearance(background = 0xFF213244.toInt(), accent = 0xFFFFA068.toInt())
        val light = playLyColorScheme(customColors.copy(mode = ThemeMode.LIGHT))
        val dark = playLyColorScheme(customColors.copy(mode = ThemeMode.DARK))
        assertEquals(0xFFF8F6EF.toInt(), light.background.toArgb())
        assertEquals(0xFF4D338B.toInt(), light.primary.toArgb())
        assertEquals(0xFF151421.toInt(), dark.background.toArgb())
        assertEquals(0xFFD8FA76.toInt(), dark.primary.toArgb())
        for (scheme in listOf(light, dark)) {
            val roles = listOf(
                scheme.onBackground to scheme.background,
                scheme.onSurface to scheme.surfaceContainer,
                scheme.onSurfaceVariant to scheme.surfaceContainerHigh,
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondaryFixed to scheme.secondaryFixed,
                scheme.onErrorContainer to scheme.errorContainer,
            )
            for ((foreground, background) in roles) {
                assertTrue(contrastRatio(foreground, background) >= 4.5f)
            }
        }
    }

    @Test
    fun customBackgroundAndAccentRemainIndependentlyUsableAtLowContrast() {
        val gray = 0xFF767676.toInt()
        val scheme = playLyColorScheme(Appearance(ThemeMode.CUSTOM, accent = gray, background = gray))
        assertEquals(gray, scheme.background.toArgb())
        val surfaces = listOf(
            scheme.background, scheme.surfaceContainerLow, scheme.surfaceContainer,
            scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
        )
        surfaces.forEach { surface ->
            assertTrue(contrastRatio(scheme.onSurface, surface) >= 4.5f)
            assertTrue(contrastRatio(scheme.primary, surface) >= 4.5f)
        }
        assertTrue(contrastRatio(Color(gray), accentForeground(Color(gray))) >= 4.5f)
        assertTrue(contrastRatio(scheme.secondaryContainer, scheme.onSecondaryContainer) >= 4.5f)
    }

    @Test
    fun systemBarsFollowCustomBackgroundAndHexInputRejectsInvalidColors() {
        val custom = Appearance(ThemeMode.CUSTOM)
        assertTrue(custom.copy(background = requireNotNull("#151D39".parseHexColor())).isDark())
        assertFalse(custom.copy(background = requireNotNull("f4eadd".parseHexColor())).isDark())
        assertNull("+12345".parseHexColor())
        assertNull("123".parseHexColor())
        assertNull("#12345g".parseHexColor())
    }
}
