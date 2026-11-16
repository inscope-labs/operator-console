package com.inscopelabs.buildengine.manualpipeline.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val MpPrimaryLight = Color(0xFF146C2E)
private val MpOnPrimaryLight = Color(0xFFFFFFFF)
private val MpPrimaryContainerLight = Color(0xFFA7F5B4)
private val MpOnPrimaryContainerLight = Color(0xFF002109)
private val MpSecondaryLight = Color(0xFF4F6353)
private val MpSecondaryContainerLight = Color(0xFFD2E8D4)
private val MpTertiaryLight = Color(0xFF3A6470)
private val MpTertiaryContainerLight = Color(0xFFBEEAF7)
private val MpErrorLight = Color(0xFFB3261E)
private val MpErrorContainerLight = Color(0xFFF9DEDC)
private val MpSurfaceLight = Color(0xFFF7FBF6)
private val MpSurfaceVariantLight = Color(0xFFDDE5DC)

private val MpPrimaryDark = Color(0xFF8CD89A)
private val MpOnPrimaryDark = Color(0xFF003915)
private val MpPrimaryContainerDark = Color(0xFF005321)
private val MpOnPrimaryContainerDark = Color(0xFFA7F5B4)
private val MpSecondaryDark = Color(0xFFB6CCB8)
private val MpSecondaryContainerDark = Color(0xFF384B3C)
private val MpTertiaryDark = Color(0xFFA2CEE0)
private val MpTertiaryContainerDark = Color(0xFF214C58)
private val MpErrorDark = Color(0xFFF2B8B5)
private val MpErrorContainerDark = Color(0xFF8C1D18)
private val MpSurfaceDark = Color(0xFF101511)
private val MpSurfaceVariantDark = Color(0xFF414941)

private val LightColorScheme = lightColorScheme(
    primary = MpPrimaryLight,
    onPrimary = MpOnPrimaryLight,
    primaryContainer = MpPrimaryContainerLight,
    onPrimaryContainer = MpOnPrimaryContainerLight,
    secondary = MpSecondaryLight,
    secondaryContainer = MpSecondaryContainerLight,
    tertiary = MpTertiaryLight,
    tertiaryContainer = MpTertiaryContainerLight,
    error = MpErrorLight,
    errorContainer = MpErrorContainerLight,
    surface = MpSurfaceLight,
    surfaceVariant = MpSurfaceVariantLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = MpPrimaryDark,
    onPrimary = MpOnPrimaryDark,
    primaryContainer = MpPrimaryContainerDark,
    onPrimaryContainer = MpOnPrimaryContainerDark,
    secondary = MpSecondaryDark,
    secondaryContainer = MpSecondaryContainerDark,
    tertiary = MpTertiaryDark,
    tertiaryContainer = MpTertiaryContainerDark,
    error = MpErrorDark,
    errorContainer = MpErrorContainerDark,
    surface = MpSurfaceDark,
    surfaceVariant = MpSurfaceVariantDark,
)

@Composable
fun ManualPipelineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
