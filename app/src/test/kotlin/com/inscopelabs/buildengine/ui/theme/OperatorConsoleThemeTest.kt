package com.inscopelabs.buildengine.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class OperatorConsoleThemeTest {

    @Test
    fun `light and dark schemes separate success primaryContainer from errorContainer`() {
        assertNotEquals(
            ConsoleLightColorScheme.primaryContainer,
            ConsoleLightColorScheme.errorContainer,
        )
        assertNotEquals(
            ConsoleDarkColorScheme.primaryContainer,
            ConsoleDarkColorScheme.errorContainer,
        )
    }

    @Test
    fun `light and dark color schemes meet WCAG AA text contrast on key semantic pairs`() {
        listOf(ConsoleLightColorScheme, ConsoleDarkColorScheme).forEach { scheme ->
            assertContrastAtLeast(4.5, scheme.onPrimary, scheme.primary, "onPrimary/primary")
            assertContrastAtLeast(4.5, scheme.onPrimaryContainer, scheme.primaryContainer, "onPrimaryContainer/primaryContainer")
            assertContrastAtLeast(4.5, scheme.onSecondary, scheme.secondary, "onSecondary/secondary")
            assertContrastAtLeast(4.5, scheme.onSecondaryContainer, scheme.secondaryContainer, "onSecondaryContainer/secondaryContainer")
            assertContrastAtLeast(4.5, scheme.onTertiary, scheme.tertiary, "onTertiary/tertiary")
            assertContrastAtLeast(4.5, scheme.onTertiaryContainer, scheme.tertiaryContainer, "onTertiaryContainer/tertiaryContainer")
            assertContrastAtLeast(4.5, scheme.onError, scheme.error, "onError/error")
            assertContrastAtLeast(4.5, scheme.onErrorContainer, scheme.errorContainer, "onErrorContainer/errorContainer")
            assertContrastAtLeast(4.5, scheme.onSurface, scheme.surface, "onSurface/surface")
            assertContrastAtLeast(4.5, scheme.onSurfaceVariant, scheme.surfaceVariant, "onSurfaceVariant/surfaceVariant")
            assertContrastAtLeast(4.5, scheme.onBackground, scheme.background, "onBackground/background")
        }
    }

    @Test
    fun `typography assigns custom bundled fonts and scalable sp sizes`() {
        val styles = listOf(
            ConsoleTypography.displayLarge,
            ConsoleTypography.displayMedium,
            ConsoleTypography.displaySmall,
            ConsoleTypography.headlineLarge,
            ConsoleTypography.headlineMedium,
            ConsoleTypography.headlineSmall,
            ConsoleTypography.titleLarge,
            ConsoleTypography.titleMedium,
            ConsoleTypography.titleSmall,
            ConsoleTypography.bodyLarge,
            ConsoleTypography.bodyMedium,
            ConsoleTypography.bodySmall,
            ConsoleTypography.labelLarge,
            ConsoleTypography.labelMedium,
            ConsoleTypography.labelSmall,
        )
        styles.forEach { style ->
            assertTrue("Expected sp fontSize on $style", style.fontSize != TextUnit.Unspecified && style.fontSize.isSp)
            assertTrue("Expected positive fontSize on $style", style.fontSize.value >= 11f)
            assertTrue("Expected lineHeight >= fontSize on $style", style.lineHeight.value >= style.fontSize.value)
        }
        assertEquals(ConsoleDisplayFontFamily, ConsoleTypography.headlineLarge.fontFamily)
        assertEquals(ConsoleBodyFontFamily, ConsoleTypography.bodyLarge.fontFamily)
        assertNotEquals(ConsoleDisplayFontFamily, ConsoleMonospaceFontFamily)
    }

    private fun assertContrastAtLeast(minRatio: Double, fg: Color, bg: Color, label: String) {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val ratio = (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
        assertTrue("Expected contrast >= $minRatio for $label, got $ratio", ratio >= minRatio)
    }

    private fun relativeLuminance(c: Color): Double {
        fun linear(v: Float): Double =
            if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)
    }
}
