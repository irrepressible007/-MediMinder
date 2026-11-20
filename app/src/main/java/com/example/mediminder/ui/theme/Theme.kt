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
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4DA),
    onSecondaryContainer = Color(0xFF7A2512),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFBE123C),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEEBE6),
    onSurfaceVariant = InkSoft,
    surfaceTint = Blue700,
    surfaceDim = Color(0xFFE2DED8),
    surfaceBright = Card,
    surfaceContainerLowest = Card,
    surfaceContainerLow = Color(0xFFFBFAF8),
    surfaceContainer = Color(0xFFF2EFEB),
    surfaceContainerHigh = Color(0xFFECE8E3),
    surfaceContainerHighest = Color(0xFFE5E0DA),
    inverseSurface = Color(0xFF2A2D3A),
    inverseOnSurface = Mist,
    inversePrimary = Blue300,
    outline = Color(0xFF78716C),
    outlineVariant = Color(0xFFDDD7CF),
)

private val DarkColors = darkColorScheme(
    primary = Blue300,
    onPrimary = Color(0xFF0B2266),
    primaryContainer = Color(0xFF22408F),
    onPrimaryContainer = Blue50,
    secondary = Color(0xFFFFB59E),
    onSecondary = Color(0xFF5C1F10),
    secondaryContainer = Color(0xFF6B2A17),
    onSecondaryContainer = Color(0xFFFFE4DA),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFFDA4AF),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF5B1023),
    onErrorContainer = Color(0xFFFFE4E6),
    background = Night,
    onBackground = Mist,
    surface = NightCard,
    onSurface = Mist,
    surfaceVariant = Color(0xFF262A38),
    onSurfaceVariant = MistSoft,
    surfaceTint = Blue300,
    surfaceDim = Night,
    surfaceBright = Color(0xFF2E3344),
    surfaceContainerLowest = Color(0xFF0C0E15),
    surfaceContainerLow = Color(0xFF161925),
    surfaceContainer = Color(0xFF1E2230),
    surfaceContainerHigh = Color(0xFF262A38),
    surfaceContainerHighest = Color(0xFF2F3445),
    inverseSurface = Color(0xFFE4E6EE),
    inverseOnSurface = Color(0xFF1F2230),
    inversePrimary = Blue700,
    outline = Color(0xFF8E93A6),
    outlineVariant = Color(0xFF353A4C),
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
        LocalDarkTheme provides darkTheme,
        // Bigger text on top of the phone's own setting, capped at 2x so layouts never break
        LocalDensity provides Density(density.density, (density.fontScale * textScale).coerceAtMost(2f)),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
