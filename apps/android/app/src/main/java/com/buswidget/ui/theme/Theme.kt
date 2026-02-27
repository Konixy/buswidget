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

private val BusBlue = Color(0xFF2563EB) // Bleu moderne éclatant
private val BusBlueDark = Color(0xFF3B82F6) // Bleu lumineux la nuit
private val SurfaceDark = Color(0xFF18181B) // Zinc 900 (très lisible pour les Cartes)
private val BackgroundDark = Color(0xFF000000) // Noir OLED absolu

private val LightColorScheme = lightColorScheme(
    primary = BusBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFF9CA3AF),
)

private val DarkColorScheme = darkColorScheme(
    primary = BusBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    background = BackgroundDark,
    onBackground = Color(0xFFF9FAFB),
    surface = SurfaceDark,
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFD1D5DB),
    outline = Color(0xFF6B7280),
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
