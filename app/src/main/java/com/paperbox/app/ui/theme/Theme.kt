package com.paperbox.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌色 - 蓝色主题
val Primary = Color(0xFF1976D2)       // 蓝色
val PrimaryDark = Color(0xFF0D47A1)
val Secondary = Color(0xFF64B5F6)     // 浅蓝
val Background = Color(0xFFF5F5F5)    // 浅灰白
val Surface = Color(0xFFFFFFFF)
val OnPrimary = Color(0xFFFFFFFF)
val OnBackground = Color(0xFF1A1A1A)
val OnSurface = Color(0xFF333333)
val Error = Color(0xFFD32F2F)
val Success = Color(0xFF388E3C)
val Warning = Color(0xFFF57C00)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Secondary,
    secondary = Secondary,
    background = Background,
    surface = Surface,
    onBackground = OnBackground,
    onSurface = OnSurface,
    error = Error,
)

private val DarkColorScheme = darkColorScheme(
    primary = Secondary,
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = PrimaryDark,
    secondary = Primary,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    error = Color(0xFFEF5350),
)

@Composable
fun PaperboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
