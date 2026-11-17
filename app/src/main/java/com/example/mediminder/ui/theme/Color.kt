package com.example.mediminder.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * MediMinder palette, chosen for older eyes and for meaning:
 *  - royal blue         → actions and navigation (trust, never alarming)
 *  - warm coral         → the friendly accent: adding things, highlights
 *  - warm paper         → backgrounds, so the app feels homely rather than clinical
 *  - green              → only "taken" / done
 *  - amber              → "due now"
 *  - rose               → missed, warnings, delete
 *  - violet             → snoozed
 *  - time-of-day hues   → small icons and tiles only (see [Hue])
 * Every text colour meets WCAG AA (4.5:1) on its background; most reach AAA (7:1).
 * Status is never shown by colour alone — always with an icon and a word.
 */

// ── Light ──
internal val Blue700 = Color(0xFF2754C5)      // primary, 6.6:1 on white
internal val Blue50 = Color(0xFFE4ECFF)
internal val Blue900 = Color(0xFF12306E)
internal val Coral = Color(0xFFC2462B)        // accent, 5:1 on white
internal val Ink = Color(0xFF1C1B1F)          // body text, 16:1
internal val InkSoft = Color(0xFF57534E)      // secondary text, 7:1 on paper
internal val Paper = Color(0xFFF7F5F2)        // background: warm, not glaring white
internal val Card = Color(0xFFFFFFFF)

// ── Dark ──
internal val Blue300 = Color(0xFFA9C1FF)
internal val Night = Color(0xFF11131C)
internal val NightCard = Color(0xFF1A1D29)
internal val Mist = Color(0xFFF1F2F6)
internal val MistSoft = Color(0xFFC5C8D6)

/** Dose-status colours, which Material's scheme has no slots for. */
@Immutable
data class StatusColors(
    val taken: Color,
    val onTaken: Color,
    val takenContainer: Color,
    val onTakenContainer: Color,
    val due: Color,
    val dueContainer: Color,
    val onDueContainer: Color,
    val missed: Color,
    val missedContainer: Color,
    val onMissedContainer: Color,
    val snoozed: Color,
    val snoozedContainer: Color,
    val onSnoozedContainer: Color,
    val quiet: Color,
    val quietContainer: Color,
    val onQuietContainer: Color,
)

val LightStatusColors = StatusColors(
    taken = Color(0xFF15803D),
    onTaken = Color.White,
    takenContainer = Color(0xFFDCFCE7),
    onTakenContainer = Color(0xFF14532D),
    due = Color(0xFFB45309),
    dueContainer = Color(0xFFFEF3C7),
    onDueContainer = Color(0xFF78350F),
    missed = Color(0xFFBE123C),
    missedContainer = Color(0xFFFFE4E6),
    onMissedContainer = Color(0xFF881337),
    snoozed = Color(0xFF6D28D9),
    snoozedContainer = Color(0xFFEDE9FE),
    onSnoozedContainer = Color(0xFF4C1D95),
    quiet = InkSoft,
    quietContainer = Color(0xFFEEEBE6),
    onQuietContainer = Color(0xFF44403C),
)

val DarkStatusColors = StatusColors(
    taken = Color(0xFF4ADE80),
    onTaken = Color(0xFF052E16),
    takenContainer = Color(0xFF14532D),
    onTakenContainer = Color(0xFFDCFCE7),
    due = Color(0xFFFBBF24),
    dueContainer = Color(0xFF78350F),
    onDueContainer = Color(0xFFFEF3C7),
    missed = Color(0xFFFDA4AF),
    missedContainer = Color(0xFF5B1023),
    onMissedContainer = Color(0xFFFFE4E6),
    snoozed = Color(0xFFC4B5FD),
    snoozedContainer = Color(0xFF3B1F78),
    onSnoozedContainer = Color(0xFFEDE9FE),
    quiet = MistSoft,
    quietContainer = Color(0xFF272B3A),
    onQuietContainer = Color(0xFFE2E4EC),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** The full-screen alarm always uses this deep night blue, day or night. */
val AlarmBackgroundTop = Color(0xFF1D3A8A)
val AlarmBackgroundBottom = Color(0xFF0E1C4D)
val AlarmTakenButton = Color(0xFF16A34A)

/*
 * Personality: a small set of friendly hues, each a pair of (strong icon colour, soft tile).
 * They mark *meaning* — the time of day of a dose, which feature a row opens — and are only
 * used on icons, tiles and small accents, never on large areas or body text.
 */
enum class Hue(
    private val lightFg: Long, private val lightBg: Long,
    private val darkFg: Long, private val darkBg: Long,
) {
    SUNRISE(0xFFEA580C, 0xFFFFEDD5, 0xFFFDBA74, 0xFF431407),   // morning
    SUN(0xFFCA8A04, 0xFFFEF9C3, 0xFFFDE047, 0xFF3F2A04),       // afternoon
    SUNSET(0xFFDB2777, 0xFFFCE7F3, 0xFFF9A8D4, 0xFF4A0D2A),    // evening
    NIGHT(0xFF4F46E5, 0xFFE0E7FF, 0xFFA5B4FC, 0xFF1E1B4B),     // night
    SKY(0xFF0284C7, 0xFFE0F2FE, 0xFF7DD3FC, 0xFF0C2D48),
    VIOLET(0xFF7C3AED, 0xFFEDE9FE, 0xFFC4B5FD, 0xFF2E1065),
    TEAL(0xFF0D9488, 0xFFCCFBF1, 0xFF5EEAD4, 0xFF06302C),
    ROSE(0xFFE11D48, 0xFFFFE4E6, 0xFFFDA4AF, 0xFF4C0519),
    AMBER(0xFFD97706, 0xFFFEF3C7, 0xFFFCD34D, 0xFF3B2405),
    GREEN(0xFF16A34A, 0xFFDCFCE7, 0xFF86EFAC, 0xFF0B2E17),
    CORAL(0xFFF2545B, 0xFFFFE4E1, 0xFFFFA4A0, 0xFF451414),
    SLATE(0xFF475569, 0xFFE2E8F0, 0xFFCBD5E1, 0xFF1E293B);

    fun fg(dark: Boolean) = Color(if (dark) darkFg else lightFg)
    fun bg(dark: Boolean) = Color(if (dark) darkBg else lightBg)
}

/** Whether the current theme is dark (the alarm forces dark, so this beats the system flag). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The hue's strong colour in the current theme (icons, small accents). */
val Hue.color: Color @androidx.compose.runtime.Composable get() = fg(LocalDarkTheme.current)

/** The hue's soft colour in the current theme (icon tiles, chips). */
val Hue.soft: Color @androidx.compose.runtime.Composable get() = bg(LocalDarkTheme.current)
