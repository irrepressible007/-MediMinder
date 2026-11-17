package com.example.mediminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Blue700,
    onPrimary = Color.White,
    primaryContainer = Blue50,
    onPrimaryContainer = Blue900,
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFB91C1C),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7EDF3),
    onSurfaceVariant = InkSoft,
    surfaceTint = Blue700,
    surfaceDim = Color(0xFFDCE3EA),
    surfaceBright = Card,
    surfaceContainerLowest = Card,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFEEF2F6),
    surfaceContainerHigh = Color(0xFFE6ECF2),
    surfaceContainerHighest = Color(0xFFDDE5EC),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Mist,
    inversePrimary = Blue300,
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFFCBD5E1),
)

private val DarkColors = darkColorScheme(
    primary = Blue300,
    onPrimary = Color(0xFF082F49),
    primaryContainer = Blue900,
    onPrimaryContainer = Blue50,
    secondary = Color(0xFF67E8F9),
    onSecondary = Color(0xFF083344),
    secondaryContainer = Color(0xFF155E75),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Night,
    onBackground = Mist,
    surface = NightCard,
    onSurface = Mist,
    surfaceVariant = Color(0xFF1F2933),
    onSurfaceVariant = MistSoft,
    surfaceTint = Blue300,
    surfaceDim = Night,
    surfaceBright = Color(0xFF2A3540),
    surfaceContainerLowest = Color(0xFF070B10),
    surfaceContainerLow = Color(0xFF10161D),
    surfaceContainer = Color(0xFF18212B),
    surfaceContainerHigh = Color(0xFF1F2933),
    surfaceContainerHighest = Color(0xFF27323E),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF1E293B),
    inversePrimary = Blue700,
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFF334155),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * @param textScale the in-app "Text size" choice (1.0 normal, 1.15 large, 1.3 extra large),
 *   applied on top of the phone's own font-size setting.
 */
@Composable
fun MediMinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        LocalDensity provides Density(density.density, density.fontScale * textScale),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
