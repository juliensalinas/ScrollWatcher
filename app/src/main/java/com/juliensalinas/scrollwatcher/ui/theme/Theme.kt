package com.juliensalinas.scrollwatcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EB6FF),
    onPrimary = Color(0xFF003258),
    secondary = Color(0xFF9ECAFF),
    background = Color(0xFF121A24),
    surface = Color(0xFF1A2430),
    onBackground = Color(0xFFE8EEF5),
    onSurface = Color(0xFFE8EEF5),
    error = Color(0xFFE57373),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A6BB5),
    onPrimary = Color.White,
    secondary = Color(0xFF3D7AB5),
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    onBackground = Color(0xFF121A24),
    onSurface = Color(0xFF121A24),
    error = Color(0xFFC62828),
)

@Composable
fun ScrollWatcherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
