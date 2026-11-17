package com.example.mediminder.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mediminder.R
import com.example.mediminder.domain.DoseState
import com.example.mediminder.ui.theme.DueAmber
import com.example.mediminder.ui.theme.MissedRed
import com.example.mediminder.ui.theme.SkippedGrey
import com.example.mediminder.ui.theme.SnoozedBlue
import com.example.mediminder.ui.theme.TakenGreen
import com.example.mediminder.util.Formats
import java.time.LocalDate

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** Coloured banner for warnings / tips, optionally tappable. */
@Composable
fun Banner(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (actionLabel != null) {
                Spacer(Modifier.width(8.dp))
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Round coloured badge with the medicine's first letter. */
@Composable
fun PillAvatar(name: String, colorHex: String, modifier: Modifier = Modifier, size: Int = 44) {
    val color = parseColor(colorHex)
    Box(
        modifier.size(size.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "•" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (size >= 44) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
        )
    }
}

fun parseColor(hex: String, fallback: Color = Color(0xFF10B981)): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { fallback }

fun stateColor(state: DoseState): Color = when (state) {
    DoseState.TAKEN -> TakenGreen
    DoseState.MISSED -> MissedRed
    DoseState.DUE -> DueAmber
    DoseState.SKIPPED -> SkippedGrey
    DoseState.SNOOZED -> SnoozedBlue
    DoseState.UPCOMING -> Color(0xFF94A3B8)
}

@Composable
fun stateLabel(state: DoseState): String = stringResource(
    when (state) {
        DoseState.TAKEN -> R.string.state_taken
        DoseState.MISSED -> R.string.state_missed
        DoseState.DUE -> R.string.state_due
        DoseState.SKIPPED -> R.string.state_skipped
        DoseState.SNOOZED -> R.string.state_snoozed
        DoseState.UPCOMING -> R.string.state_upcoming
    }
)

@Composable
fun StatusChip(state: DoseState, label: String = stateLabel(state)) {
    val color = stateColor(state)
    Text(
        label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** A row of single-choice chips that wraps onto several lines. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

/** − value + stepper. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, minusEnabled: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus, enabled = minusEnabled) { Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_decrease)) }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onPlus) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_increase)) }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Label + value line used on detail screens. */
@Composable
fun InfoRow(label: String, value: String?, modifier: Modifier = Modifier) {
    if (value.isNullOrBlank()) return
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

fun showTimePicker(context: Context, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minuteOfDay / 60, minuteOfDay % 60, false).show()
}

fun showDatePicker(context: Context, date: LocalDate, minDate: LocalDate? = null, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
        minDate?.let { datePicker.minDate = it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
    }.show()
}

/** Tappable time pill, e.g. "সকাল ৮:০০". */
@Composable
fun TimePill(minuteOfDay: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Text(
        Formats.time(context, minuteOfDay),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            .clickable { showTimePicker(context, minuteOfDay, onChange) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.size(20.dp))
            action()
        }
    }
}
