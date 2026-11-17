package com.example.mediminder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = BlueSecondaryDark,
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = AmberDark,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    // Every surface role is set, so no component falls back to Material's default grey/lilac
    surfaceTint = GreenPrimaryDark,
    surfaceDim = BackgroundDark,
    surfaceBright = Color(0xFF334155),
    surfaceContainerLowest = Color(0xFF0B1222),
    surfaceContainerLow = Color(0xFF172133),
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = Color(0xFF273449),
    surfaceContainerHighest = Color(0xFF243247),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF1E293B),
    inversePrimary = Color(0xFF059669),
    onSecondary = BackgroundDark,
    onTertiary = BackgroundDark,
    onError = Color(0xFF450A0A),
    error = ErrorDark,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
)

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = BlueSecondaryLight,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = AmberLight,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceTint = GreenPrimaryLight,
    surfaceDim = Color(0xFFE2E8F0),
    surfaceBright = SurfaceLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = BackgroundLight,
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFEEF2F7),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    inversePrimary = Color(0xFF6EE7B7),
    onSecondary = Color.White,
    onTertiary = Color(0xFF1C1917),
    onError = Color.White,
    error = ErrorLight,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

@Composable
fun MediMinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // keep the MediMinder green by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
