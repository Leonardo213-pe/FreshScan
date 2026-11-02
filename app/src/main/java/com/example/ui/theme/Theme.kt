package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = MinimalPrimary,
    onPrimary = MinimalOnPrimary,
    primaryContainer = MinimalPrimaryContainer,
    onPrimaryContainer = MinimalOnPrimaryContainer,
    secondary = MinimalSecondary,
    onSecondary = MinimalOnSecondary,
    secondaryContainer = MinimalSecondaryContainer,
    onSecondaryContainer = MinimalOnSecondaryContainer,
    tertiary = MinimalTertiary,
    onTertiary = MinimalOnTertiary,
    tertiaryContainer = MinimalTertiaryContainer,
    onTertiaryContainer = MinimalOnTertiaryContainer,
    error = StatusAlertRed,
    background = MinimalBackground,
    onBackground = MinimalOnBackground,
    surface = MinimalSurface,
    onSurface = MinimalOnSurface,
    surfaceVariant = MinimalSurfaceVariant,
    onSurfaceVariant = MinimalOnSurfaceVariant,
    outline = MinimalOutline
)

private val DarkColors = darkColorScheme(
    primary = MinimalDarkPrimary,
    onPrimary = MinimalDarkOnPrimary,
    primaryContainer = MinimalDarkPrimaryContainer,
    onPrimaryContainer = MinimalDarkOnPrimaryContainer,
    secondary = MinimalDarkSecondary,
    onSecondary = MinimalDarkOnSecondary,
    secondaryContainer = MinimalDarkSecondaryContainer,
    onSecondaryContainer = MinimalDarkOnSecondaryContainer,
    tertiary = MinimalDarkTertiary,
    onTertiary = MinimalDarkOnTertiary,
    tertiaryContainer = MinimalDarkTertiaryContainer,
    onTertiaryContainer = MinimalDarkOnTertiaryContainer,
    error = StatusAlertRed,
    background = MinimalDarkBackground,
    onBackground = MinimalDarkOnBackground,
    surface = MinimalDarkSurface,
    onSurface = MinimalDarkOnSurface,
    surfaceVariant = MinimalDarkSurfaceVariant,
    onSurfaceVariant = MinimalDarkOnSurfaceVariant,
    outline = MinimalDarkOutline
)

@Composable
fun FreshScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    FreshScanTheme(darkTheme = darkTheme, content = content)
}
