package com.liubishchev.timebook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBE1),
    onPrimaryContainer = Color(0xFF0E2C1C),
    secondary = Color(0xFFB4803A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6E8D0),
    onSecondaryContainer = Color(0xFF3B2A11),
    background = Color(0xFFFAF8F3),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEFEBE2),
    onSurfaceVariant = Color(0xFF4E4A42),
    outline = Color(0xFFB5AFA3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCBA8),
    onPrimary = Color(0xFF0B3320),
    primaryContainer = Color(0xFF22472F),
    onPrimaryContainer = Color(0xFFD3EBDC),
    secondary = Color(0xFFE0BC7E),
    onSecondary = Color(0xFF3D2C0C),
    secondaryContainer = Color(0xFF55401B),
    onSecondaryContainer = Color(0xFFF8E3BE),
    background = Color(0xFF14130F),
    onBackground = Color(0xFFEBE6DC),
    surface = Color(0xFF1C1B17),
    onSurface = Color(0xFFEBE6DC),
    surfaceVariant = Color(0xFF2B2822),
    onSurfaceVariant = Color(0xFFC9C3B7),
    outline = Color(0xFF6E6960),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

@Composable
fun TimeBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
