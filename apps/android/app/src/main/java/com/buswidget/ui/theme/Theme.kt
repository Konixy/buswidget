package com.buswidget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BusBlue = Color(0xFF1565C0)
private val BusBlueDark = Color(0xFF5E92F3)
private val BusGreen = Color(0xFF2E7D32)
private val BusGreenDark = Color(0xFF69F0AE)
private val SurfaceDark = Color(0xFF1A1C1E)
private val BackgroundDark = Color(0xFF111316)

private val LightColorScheme = lightColorScheme(
    primary = BusBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF546E9A),
    onSecondary = Color.White,
    background = Color(0xFFF8F9FF),
    surface = Color.White,
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE3E8F3),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
)

private val DarkColorScheme = darkColorScheme(
    primary = BusBlueDark,
    onPrimary = Color(0xFF002984),
    primaryContainer = Color(0xFF0A3880),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF9DB4DE),
    onSecondary = Color(0xFF243354),
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF292C32),
    onSurfaceVariant = Color(0xFFC5C7D4),
    outline = Color(0xFF8F929E),
)

@Composable
fun BusWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
