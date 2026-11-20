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
    fun fixedThemesUseOppositeNeutralColors() {
        val customColors = Appearance(background = 0xFF213244.toInt(), accent = 0xFFFFA068.toInt())
        val light = playLyColorScheme(customColors.copy(mode = ThemeMode.LIGHT))
        val dark = playLyColorScheme(customColors.copy(mode = ThemeMode.DARK))
        assertEquals(Color.White, light.background)
        assertEquals(Color.Black, light.primary)
        assertEquals(Color.Black, dark.background)
        assertEquals(Color.White, dark.primary)
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
