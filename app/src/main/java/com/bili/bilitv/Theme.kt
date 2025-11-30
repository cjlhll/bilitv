package com.bili.bilitv

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 浅色主题配色
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0091EA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF81D4FA),
    onPrimaryContainer = Color(0xFF004D71),
    
    secondary = Color(0xFFFF4081),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFF80AB),
    onSecondaryContainer = Color(0xFF880E4F),
    
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1C1C),
    
    surface = Color.White,
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF424242)
)

// 深色主题配色
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF40C4FF),
    onPrimary = Color(0xFF003049),
    primaryContainer = Color(0xFF005280),
    onPrimaryContainer = Color(0xFFB3E5FC),
    
    secondary = Color(0xFFFF4081),
    onSecondary = Color(0xFF5D0032),
    secondaryContainer = Color(0xFFC2185B),
    onSecondaryContainer = Color(0xFFFCE4EC),
    
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF424242),
    onSurfaceVariant = Color(0xFFBDBDBD)
)

/**
 * BiliTV应用主题
 */
@Composable
fun BiliTVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
