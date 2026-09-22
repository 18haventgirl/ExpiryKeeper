package com.expirykeeper.core.ui.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696D),
    secondary = Color(0xFF4B6364),
    tertiary = Color(0xFF8A4E00),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD8DC),
    secondary = Color(0xFFB0CCCD),
    tertiary = Color(0xFFFFB870),
)

@Composable
fun EkTheme(darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
