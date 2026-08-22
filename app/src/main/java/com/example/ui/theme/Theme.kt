package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DarkText,
    onPrimary = DarkBg,
    secondary = DarkAccent,
    onSecondary = DarkBg,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkBg,
    onSurface = DarkText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkAccent,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = LightText,
    onPrimary = LightBg,
    secondary = LightAccent,
    onSecondary = LightBg,
    background = LightBg,
    onBackground = LightText,
    surface = LightBg,
    onSurface = LightText,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightAccent,
    outline = LightBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
