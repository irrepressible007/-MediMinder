package com.example.mediminder.ui.health

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.HealthRepository
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.DosePlanner
import com.example.mediminder.domain.MeasureType
import com.example.mediminder.domain.Readings
import com.example.mediminder.domain.SugarTiming
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.ui.components.AnimatedArt
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.Arrive
import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.components.CareAvatar
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.rememberEntranceState
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.util.CareText
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import javax.inject.Inject

data class HabitToday(val item: MedicationWithSchedules, val done: Int, val total: Int)

data class HealthUi(
    /** Readings by kind, newest first; only kinds that have any. */
    val readings: Map<MeasureType, List<Reading>>,
    val checks: List<MedicationWithSchedules>,
    val habits: List<HabitToday>,
) {
    val isEmpty: Boolean get() = readings.isEmpty() && checks.isEmpty() && habits.isEmpty()
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    medicines: MedicineRepository,
    health: HealthRepository,
    db: MediMinderDatabase,
) : ViewModel() {
    private val today = LocalDate.now()

    val state: StateFlow<HealthUi?> = combine(
        medicines.observeAll(),
        health.observeAll(),
        db.doseLogDao().observeRange(today.toEpochDay(), today.toEpochDay()),
    ) { meds, readings, logs ->
        val active = meds.filter { !it.medication.archived }
        val habits = active.filter { it.medication.kind == CareKind.HABIT }
        val due = DosePlanner.occurrencesOn(habits, today)
        val done = logs.filter { it.status == DoseStatus.TAKEN && it.scheduleId != null }.map { it.scheduleId to it.scheduledDay }.toSet()
        HealthUi(
            readings = MeasureType.entries.associateWith { t -> readings.filter { it.type == t.name } }.filterValues { it.isNotEmpty() },
            checks = active.filter { it.medication.kind == CareKind.MEASURE },
            habits = habits.map { h ->
                val mine = due.filter { it.medication.id == h.medication.id }
                HabitToday(h, mine.count { (it.schedule.id to it.day) in done }, mine.size)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Health checks (readings and reminders to check) and healthy habits, on one page. */
@Composable
fun HealthScreen(
    onBack: () -> Unit,
    onRecord: (type: String) -> Unit,
    onOpenReadings: (type: String) -> Unit,
    onAdd: (kind: CareKind) -> Unit,
    onEdit: (id: Long) -> Unit,
    viewModel: HealthViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.state.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.health_title), onBack = onBack) },
    ) { padding ->
        val state = ui ?: return@Scaffold
        val arrival = rememberEntranceState()
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.isEmpty) {
                item {
                    EmptyState(
                        icon = Icons.Default.EditNote,
                        title = stringResource(R.string.health_empty_title),
                        body = stringResource(R.string.health_empty_body),
                        hue = Hue.ROSE,
                        art = Art.CUFF,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton(stringResource(R.string.health_record), onClick = { onRecord("") }, icon = Icons.Default.EditNote)
                            SecondaryButton(stringResource(R.string.health_add_check), onClick = { onAdd(CareKind.MEASURE) }, icon = Icons.Default.Add)
                            SecondaryButton(stringResource(R.string.health_add_habit), onClick = { onAdd(CareKind.HABIT) }, icon = Icons.Default.Add)
                        }
                    }
                }
                return@LazyColumn
            }

            item { Arrive(arrival, 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedArt(Art.CUFF, Hue.ROSE, size = 96.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.health_intro), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }}
            item { Arrive(arrival, 1) { PrimaryButton(stringResource(R.string.health_record), onClick = { onRecord("") }, icon = Icons.Default.EditNote) }}

            // Readings, one card per kind
            item { Arrive(arrival, 2) { SectionTitle(stringResource(R.string.health_readings)) }}
            if (state.readings.isEmpty()) item {
                Text(stringResource(R.string.health_no_readings), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.readings.entries.toList(), key = { "type-${it.key}" }) { (type, list) ->
                ReadingTypeCard(type, list, onClick = { onOpenReadings(type.name) })
            }

            // Reminders to check
            item { SectionTitle(stringResource(R.string.health_checks)) }
            items(state.checks, key = { "check-${it.medication.id}" }) { item ->
                CareRow(item, subtitle = timesSummary(context, item), onClick = { onEdit(item.medication.id) })
            }
            item { SecondaryButton(stringResource(R.string.health_add_check), onClick = { onAdd(CareKind.MEASURE) }, icon = Icons.Default.Add) }

            // Habits, with how many are done today
            item { SectionTitle(stringResource(R.string.health_habits)) }
            items(state.habits, key = { "habit-${it.item.medication.id}" }) { h ->
                CareRow(h.item, subtitle = timesSummary(context, h.item), onClick = { onEdit(h.item.medication.id) }) {
                    if (h.total > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.health_habit_progress, Formats.number(context, h.done), Formats.number(context, h.total)),
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { h.done / h.total.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                            color = LocalStatusColors.current.taken,
                            trackColor = MaterialTheme.colorScheme.surfaceContainer,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
            }
            item { SecondaryButton(stringResource(R.string.health_add_habit), onClick = { onAdd(CareKind.HABIT) }, icon = Icons.Default.Add) }

            item {
                Text(
                    stringResource(R.string.reading_ranges_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ReadingTypeCard(type: MeasureType, readings: List<Reading>, onClick: () -> Unit) {
    val context = LocalContext.current
    val latest = readings.first()
    MediCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CareAvatar(CareKind.MEASURE, type.name, colorFor(CareKind.MEASURE, type.name), size = 48)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(CareText.measureName(context, type), style = MaterialTheme.typography.titleLarge)
                Text(
                    Formats.digits(context, LocalResources.current.getQuantityString(R.plurals.readings_count, readings.size, readings.size)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(CareText.value(context, latest), style = MaterialTheme.typography.headlineSmall)
                Text(whenText(context, latest.takenAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LevelChip(Readings.level(type, latest.value, latest.value2, SugarTiming.entries.firstOrNull { it.name == latest.timing }))
        }
        if (readings.size >= 2) {
            Spacer(Modifier.height(8.dp))
            ReadingChart(type, readings, height = 88.dp, limit = 14, axis = false)
        }
    }
}

/** A health check or habit on the list: what, when, and (for habits) how today is going. */
@Composable
private fun CareRow(item: MedicationWithSchedules, subtitle: String, onClick: () -> Unit, extra: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val m = item.medication
    MediCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CareAvatar(m.kind, m.subtype, m.color, size = 48)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(CareText.name(context, m.kind, m.subtype, m.name), style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        extra()
    }
}

/** "8:00 AM, 8:00 PM" and, unless every day, which days. */
internal fun timesSummary(context: Context, item: MedicationWithSchedules): String {
    val schedules = item.schedules.filter { it.enabled }.sortedBy { it.minuteOfDay }
    if (schedules.isEmpty()) return context.getString(R.string.health_no_reminder)
    val times = schedules.joinToString(", ") { Formats.time(context, it.minuteOfDay) }
    val mask = schedules.first().daysMask
    if (mask == DaysOfWeek.EVERY_DAY) return times
    val locale = Formats.locale(context)
    val days = WEEK.filter { DaysOfWeek.includes(mask, it) }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    return "$times · $days"
}

/** Bangladesh's week starts on Saturday. */
internal val WEEK = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

/** "Today, 8:05 AM" or "Mon, 3 Mar, 8:05 AM". */
internal fun whenText(context: Context, millis: Long): String {
    val at: LocalDateTime = millis.toLocalDateTime()
    val minute = at.hour * 60 + at.minute
    val date = at.toLocalDate()
    val day = if (date == LocalDate.now()) context.getString(R.string.tab_today) else Formats.shortDate(context, date)
    return day + ", " + Formats.time(context, minute)
}
