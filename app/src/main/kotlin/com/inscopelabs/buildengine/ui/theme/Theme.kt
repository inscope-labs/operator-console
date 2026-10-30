package com.inscopelabs.buildengine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
private val LightColors = lightColorScheme(
    primary = ConsolePrimaryLight,
    secondary = ConsoleSecondaryLight,
    error = ConsoleErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = ConsolePrimaryDark,
    secondary = ConsoleSecondaryDark,
    error = ConsoleErrorDark,
)

@Composable
fun OperatorConsoleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ConsoleTypography,
        content = content,
    )
}
