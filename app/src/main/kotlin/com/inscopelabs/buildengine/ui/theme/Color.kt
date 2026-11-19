package com.inscopelabs.buildengine.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Light scheme — crisp mineral surfaces anchored by deep operational emerald and slate-teal.
internal val ConsolePrimaryLight = Color(0xFF146C2E)
internal val ConsoleOnPrimaryLight = Color(0xFFFFFFFF)
internal val ConsolePrimaryContainerLight = Color(0xFFA7F5B4)
internal val ConsoleOnPrimaryContainerLight = Color(0xFF002109)

internal val ConsoleSecondaryLight = Color(0xFF2F6373)
internal val ConsoleOnSecondaryLight = Color(0xFFFFFFFF)
internal val ConsoleSecondaryContainerLight = Color(0xFFB3E8FA)
internal val ConsoleOnSecondaryContainerLight = Color(0xFF001F28)

internal val ConsoleTertiaryLight = Color(0xFF8A5000)
internal val ConsoleOnTertiaryLight = Color(0xFFFFFFFF)
internal val ConsoleTertiaryContainerLight = Color(0xFFFFDCBC)
internal val ConsoleOnTertiaryContainerLight = Color(0xFF2C1600)

internal val ConsoleErrorLight = Color(0xFFBA1A1A)
internal val ConsoleOnErrorLight = Color(0xFFFFFFFF)
internal val ConsoleErrorContainerLight = Color(0xFFFFDAD6)
internal val ConsoleOnErrorContainerLight = Color(0xFF410002)

internal val ConsoleBackgroundLight = Color(0xFFF6FBF6)
internal val ConsoleOnBackgroundLight = Color(0xFF171D18)
internal val ConsoleSurfaceLight = Color(0xFFF6FBF6)
internal val ConsoleOnSurfaceLight = Color(0xFF171D18)
internal val ConsoleSurfaceVariantLight = Color(0xFFDDE5DC)
internal val ConsoleOnSurfaceVariantLight = Color(0xFF414941)
internal val ConsoleOutlineLight = Color(0xFF717971)
internal val ConsoleOutlineVariantLight = Color(0xFFC1C9BF)

internal val ConsoleSurfaceContainerLowestLight = Color(0xFFFFFFFF)
internal val ConsoleSurfaceContainerLowLight = Color(0xFFF0F5F0)
internal val ConsoleSurfaceContainerLight = Color(0xFFEBEFEA)
internal val ConsoleSurfaceContainerHighLight = Color(0xFFE5EAE4)
internal val ConsoleSurfaceContainerHighestLight = Color(0xFFDFE4DF)

internal val ConsoleInverseSurfaceLight = Color(0xFF2C322D)
internal val ConsoleInverseOnSurfaceLight = Color(0xFFEDF2EC)
internal val ConsoleInversePrimaryLight = Color(0xFF8CD89A)

// Dark scheme — tuned for low-glare console operation with clear semantic separation.
internal val ConsolePrimaryDark = Color(0xFF8CD89A)
internal val ConsoleOnPrimaryDark = Color(0xFF003915)
internal val ConsolePrimaryContainerDark = Color(0xFF005321)
internal val ConsoleOnPrimaryContainerDark = Color(0xFFA7F5B4)

internal val ConsoleSecondaryDark = Color(0xFF97CCDD)
internal val ConsoleOnSecondaryDark = Color(0xFF003542)
internal val ConsoleSecondaryContainerDark = Color(0xFF124B5A)
internal val ConsoleOnSecondaryContainerDark = Color(0xFFB3E8FA)

internal val ConsoleTertiaryDark = Color(0xFFFFB960)
internal val ConsoleOnTertiaryDark = Color(0xFF4A2800)
internal val ConsoleTertiaryContainerDark = Color(0xFF693C00)
internal val ConsoleOnTertiaryContainerDark = Color(0xFFFFDCBC)

internal val ConsoleErrorDark = Color(0xFFFFB4AB)
internal val ConsoleOnErrorDark = Color(0xFF690005)
internal val ConsoleErrorContainerDark = Color(0xFF93000A)
internal val ConsoleOnErrorContainerDark = Color(0xFFFFDAD6)

internal val ConsoleBackgroundDark = Color(0xFF0F1510)
internal val ConsoleOnBackgroundDark = Color(0xFFDFE4DF)
internal val ConsoleSurfaceDark = Color(0xFF0F1510)
internal val ConsoleOnSurfaceDark = Color(0xFFDFE4DF)
internal val ConsoleSurfaceVariantDark = Color(0xFF414941)
internal val ConsoleOnSurfaceVariantDark = Color(0xFFC1C9BF)
internal val ConsoleOutlineDark = Color(0xFF8B938A)
internal val ConsoleOutlineVariantDark = Color(0xFF414941)

internal val ConsoleSurfaceContainerLowestDark = Color(0xFF0A0F0B)
internal val ConsoleSurfaceContainerLowDark = Color(0xFF171D18)
internal val ConsoleSurfaceContainerDark = Color(0xFF1B211C)
internal val ConsoleSurfaceContainerHighDark = Color(0xFF252B26)
internal val ConsoleSurfaceContainerHighestDark = Color(0xFF303631)

internal val ConsoleInverseSurfaceDark = Color(0xFFDFE4DF)
internal val ConsoleInverseOnSurfaceDark = Color(0xFF2C322D)
internal val ConsoleInversePrimaryDark = Color(0xFF146C2E)

val ConsoleLightColorScheme: ColorScheme = lightColorScheme(
    primary = ConsolePrimaryLight,
    onPrimary = ConsoleOnPrimaryLight,
    primaryContainer = ConsolePrimaryContainerLight,
    onPrimaryContainer = ConsoleOnPrimaryContainerLight,
    secondary = ConsoleSecondaryLight,
    onSecondary = ConsoleOnSecondaryLight,
    secondaryContainer = ConsoleSecondaryContainerLight,
    onSecondaryContainer = ConsoleOnSecondaryContainerLight,
    tertiary = ConsoleTertiaryLight,
    onTertiary = ConsoleOnTertiaryLight,
    tertiaryContainer = ConsoleTertiaryContainerLight,
    onTertiaryContainer = ConsoleOnTertiaryContainerLight,
    error = ConsoleErrorLight,
    onError = ConsoleOnErrorLight,
    errorContainer = ConsoleErrorContainerLight,
    onErrorContainer = ConsoleOnErrorContainerLight,
    background = ConsoleBackgroundLight,
    onBackground = ConsoleOnBackgroundLight,
    surface = ConsoleSurfaceLight,
    onSurface = ConsoleOnSurfaceLight,
    surfaceVariant = ConsoleSurfaceVariantLight,
    onSurfaceVariant = ConsoleOnSurfaceVariantLight,
    outline = ConsoleOutlineLight,
    outlineVariant = ConsoleOutlineVariantLight,
    surfaceContainerLowest = ConsoleSurfaceContainerLowestLight,
    surfaceContainerLow = ConsoleSurfaceContainerLowLight,
    surfaceContainer = ConsoleSurfaceContainerLight,
    surfaceContainerHigh = ConsoleSurfaceContainerHighLight,
    surfaceContainerHighest = ConsoleSurfaceContainerHighestLight,
    inverseSurface = ConsoleInverseSurfaceLight,
    inverseOnSurface = ConsoleInverseOnSurfaceLight,
    inversePrimary = ConsoleInversePrimaryLight,
)

val ConsoleDarkColorScheme: ColorScheme = darkColorScheme(
    primary = ConsolePrimaryDark,
    onPrimary = ConsoleOnPrimaryDark,
    primaryContainer = ConsolePrimaryContainerDark,
    onPrimaryContainer = ConsoleOnPrimaryContainerDark,
    secondary = ConsoleSecondaryDark,
    onSecondary = ConsoleOnSecondaryDark,
    secondaryContainer = ConsoleSecondaryContainerDark,
    onSecondaryContainer = ConsoleOnSecondaryContainerDark,
    tertiary = ConsoleTertiaryDark,
    onTertiary = ConsoleOnTertiaryDark,
    tertiaryContainer = ConsoleTertiaryContainerDark,
    onTertiaryContainer = ConsoleOnTertiaryContainerDark,
    error = ConsoleErrorDark,
    onError = ConsoleOnErrorDark,
    errorContainer = ConsoleErrorContainerDark,
    onErrorContainer = ConsoleOnErrorContainerDark,
    background = ConsoleBackgroundDark,
    onBackground = ConsoleOnBackgroundDark,
    surface = ConsoleSurfaceDark,
    onSurface = ConsoleOnSurfaceDark,
    surfaceVariant = ConsoleSurfaceVariantDark,
    onSurfaceVariant = ConsoleOnSurfaceVariantDark,
    outline = ConsoleOutlineDark,
    outlineVariant = ConsoleOutlineVariantDark,
    surfaceContainerLowest = ConsoleSurfaceContainerLowestDark,
    surfaceContainerLow = ConsoleSurfaceContainerLowDark,
    surfaceContainer = ConsoleSurfaceContainerDark,
    surfaceContainerHigh = ConsoleSurfaceContainerHighDark,
    surfaceContainerHighest = ConsoleSurfaceContainerHighestDark,
    inverseSurface = ConsoleInverseSurfaceDark,
    inverseOnSurface = ConsoleInverseOnSurfaceDark,
    inversePrimary = ConsoleInversePrimaryDark,
)
