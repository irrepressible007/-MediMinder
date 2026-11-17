package com.example.mediminder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * The phone's own font (Roboto + Noto Sans Bengali on Android): the most legible choice for
 * both Bangla and English, and it matches how the rest of the phone reads.
 * Headings are large; body text is 17sp (a step above Material's 16) and nothing is
 * smaller than 13sp, with roomy line heights so Bangla vowel signs never collide.
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
    titleLarge = style(21, 28, FontWeight.SemiBold),
    titleMedium = style(18, 25, FontWeight.SemiBold),
    titleSmall = style(16, 22, FontWeight.SemiBold),
    bodyLarge = style(17, 25),
    bodyMedium = style(16, 23),
    bodySmall = style(14, 20),
    labelLarge = style(17, 22, FontWeight.SemiBold),
    labelMedium = style(14, 19, FontWeight.Medium),
    labelSmall = style(13, 17, FontWeight.Medium),
)
