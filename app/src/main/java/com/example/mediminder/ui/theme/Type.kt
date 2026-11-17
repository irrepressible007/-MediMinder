package com.example.mediminder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * The phone's own font (Roboto + Noto Sans Bengali on Android): the most legible choice for
 * both Bangla and English, and it matches how the rest of the phone reads.
 * Sizes start about two steps above Material's defaults — body text is 18sp, nothing is
 * smaller than 14sp — with roomy line heights so Bangla vowel signs never collide.
 * The user's "Text size" setting scales all of this further (see MediMinderTheme).
 */
private val Sans = FontFamily.Default

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Sans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp)

val Typography = Typography(
    displayLarge = style(52, 60, FontWeight.Bold),
    displayMedium = style(44, 52, FontWeight.Bold),
    displaySmall = style(36, 44, FontWeight.Bold),
    headlineLarge = style(32, 40, FontWeight.Bold),
    headlineMedium = style(28, 36, FontWeight.Bold),
    headlineSmall = style(24, 32, FontWeight.Bold),
    titleLarge = style(22, 30, FontWeight.SemiBold),
    titleMedium = style(19, 27, FontWeight.SemiBold),
    titleSmall = style(17, 24, FontWeight.SemiBold),
    bodyLarge = style(18, 27),
    bodyMedium = style(17, 25),
    bodySmall = style(15, 22),
    labelLarge = style(18, 24, FontWeight.SemiBold),
    labelMedium = style(15, 20, FontWeight.Medium),
    labelSmall = style(14, 18, FontWeight.Medium),
)
