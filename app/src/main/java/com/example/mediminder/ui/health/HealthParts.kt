package com.example.mediminder.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.ReadingLevel
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.util.CareText

/** "Normal" in green, "A bit high" in amber, "High" / "Low" in red. Nothing for weight. */
@Composable
fun LevelChip(level: ReadingLevel, modifier: Modifier = Modifier) {
    val label = CareText.levelName(LocalContext.current, level) ?: return
    val status = LocalStatusColors.current
    val (bg, fg) = when (level) {
        ReadingLevel.NORMAL -> status.taken to status.onTaken
        ReadingLevel.A_BIT_HIGH, ReadingLevel.A_BIT_LOW -> status.dueContainer to status.onDueContainer
        else -> status.missedContainer to status.missed
    }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Each health check and habit keeps its own colour (green stays for "taken"). */
fun colorFor(kind: CareKind, subtype: String?): String = when (kind) {
    CareKind.MEASURE -> when (subtype) {
        "BLOOD_SUGAR" -> "#8B5CF6"
        "WEIGHT" -> "#64748B"
        "TEMPERATURE" -> "#F59E0B"
        "OXYGEN" -> "#3B82F6"
        else -> "#EF4444"
    }
    else -> when (subtype) {
        "WATER" -> "#3B82F6"
        "WALK" -> "#F59E0B"
        "EXERCISE" -> "#EC4899"
        "FOOT_CHECK" -> "#8B5CF6"
        "SLEEP" -> "#64748B"
        else -> "#14B8A6"
    }
}
