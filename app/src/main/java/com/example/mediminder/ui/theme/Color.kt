package com.example.mediminder.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * MediMinder palette, chosen for older eyes and for meaning:
 *  - calm medical blue  → actions and navigation (trust, never alarming)
 *  - green              → only "taken" / done
 *  - amber              → "due now"
 *  - red                → missed, warnings, delete
 * Every text colour meets WCAG AA (4.5:1) on its background; most reach AAA (7:1).
 * Status is never shown by colour alone — always with an icon and a word.
 */

// ── Light ──
internal val Blue700 = Color(0xFF0369A1)      // primary, 5.9:1 on white
internal val Blue50 = Color(0xFFE0F2FE)
internal val Blue900 = Color(0xFF0C4A6E)
internal val Ink = Color(0xFF0F172A)          // body text, 17:1
internal val InkSoft = Color(0xFF475569)      // secondary text, 7.5:1
internal val Paper = Color(0xFFF3F6F9)        // background: soft, not glaring white
internal val Card = Color(0xFFFFFFFF)

// ── Dark ──
internal val Blue300 = Color(0xFF7DD3FC)
internal val Night = Color(0xFF0B1117)
internal val NightCard = Color(0xFF141B23)
internal val Mist = Color(0xFFF1F5F9)
internal val MistSoft = Color(0xFFCBD5E1)

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
    missed = Color(0xFFB91C1C),
    missedContainer = Color(0xFFFEE2E2),
    onMissedContainer = Color(0xFF7F1D1D),
    snoozed = Color(0xFF1D4ED8),
    snoozedContainer = Color(0xFFDBEAFE),
    onSnoozedContainer = Color(0xFF1E3A8A),
    quiet = InkSoft,
    quietContainer = Color(0xFFE7EDF3),
    onQuietContainer = Color(0xFF334155),
)

val DarkStatusColors = StatusColors(
    taken = Color(0xFF4ADE80),
    onTaken = Color(0xFF052E16),
    takenContainer = Color(0xFF14532D),
    onTakenContainer = Color(0xFFDCFCE7),
    due = Color(0xFFFBBF24),
    dueContainer = Color(0xFF78350F),
    onDueContainer = Color(0xFFFEF3C7),
    missed = Color(0xFFFCA5A5),
    missedContainer = Color(0xFF7F1D1D),
    onMissedContainer = Color(0xFFFEE2E2),
    snoozed = Color(0xFF93C5FD),
    snoozedContainer = Color(0xFF1E3A8A),
    onSnoozedContainer = Color(0xFFDBEAFE),
    quiet = MistSoft,
    quietContainer = Color(0xFF27323E),
    onQuietContainer = Color(0xFFE2E8F0),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** The full-screen alarm always uses this deep blue, day or night. */
val AlarmBackgroundTop = Color(0xFF0C4A6E)
val AlarmBackgroundBottom = Color(0xFF082F49)
val AlarmTakenButton = Color(0xFF16A34A)
