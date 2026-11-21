package com.example.mediminder.ui.more

import com.example.mediminder.data.HealthRepository
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.MeasureType
import com.example.mediminder.domain.MedicationAdherence
import com.example.mediminder.domain.Readings
import com.example.mediminder.domain.SugarTiming
import com.example.mediminder.domain.toEpochMillis
import com.example.mediminder.ui.health.LevelChip
import com.example.mediminder.ui.health.averageOf
import com.example.mediminder.util.CareText
import kotlinx.coroutines.flow.map
import com.example.mediminder.ui.components.AnimatedArt
import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.components.Arrive
import com.example.mediminder.ui.components.rememberEntranceState
import com.example.mediminder.ui.components.ProgressRing
import com.example.mediminder.ui.components.animatedCount
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.Adherence
import com.example.mediminder.domain.AdherenceSummary
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.ChoiceChips
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    medicines: MedicineRepository,
    db: MediMinderDatabase,
    health: HealthRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val days = MutableStateFlow(7)
    private val today = LocalDate.now()

    private val all = days.flatMapLatest { n ->
        val from = today.minusDays(n - 1L)
        combine(
            medicines.observeAll(),
            db.doseLogDao().observeRange(from.toEpochDay(), today.toEpochDay()),
            health.observeSince(from.atStartOfDay().toEpochMillis()),
        ) { meds, logs, readings ->
            val now = LocalDateTime.now()
            // The percentage is about medicines; habits are counted on their own
            val medicinesOnly = Adherence.summarise(meds.filter { it.medication.kind.isMedicine }, logs, from, today, now)
            val habits = Adherence.summarise(meds.filter { it.medication.kind == CareKind.HABIT }, logs, from, today, now)
            medicinesOnly to StatsExtra(
                habits = habits.perMedication.filter { it.expected > 0 },
                readings = MeasureType.entries.associateWith { t -> readings.filter { it.type == t.name } }.filterValues { it.isNotEmpty() },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val summary: StateFlow<AdherenceSummary?> = all.map { it?.first }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val extra: StateFlow<StatsExtra?> = all.map { it?.second }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every dose ever taken earns points; shown here rather than on the busy Today screen. */
    val points: StateFlow<Int> = combine(db.doseLogDao().observeCount(DoseStatus.TAKEN), days) { taken, _ -> Adherence.points(taken) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val userName: String get() = settings.current.userName
}

@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val status = LocalStatusColors.current
    val days by viewModel.days.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val extra by viewModel.extra.collectAsState()
    val points by viewModel.points.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.stats_title), onBack = onBack) },
    ) { padding ->
        val arrival = rememberEntranceState()
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Arrive(arrival, 0) {
                ChoiceChips(listOf(7, 30, 90), days, { n -> Formats.digits(context, context.resources.getQuantityString(R.plurals.days_count, n, n)) }, { viewModel.days.value = it })
            }}
            val s = summary ?: return@LazyColumn
            item { Arrive(arrival, 1) {
                MediCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val rate = s.rate
                        val shown = animatedCount(rate ?: 0, durationMillis = 1100)
                        ProgressRing(
                            progress = shown / 100f,
                            color = LocalStatusColors.current.taken,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            size = 168.dp,
                            stroke = 16.dp,
                        ) {
                            Text(
                                rate?.let { Formats.number(context, shown) + "%" } ?: "—",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.stats_rate_label), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
            }}
            item { Arrive(arrival, 2) {
                MediCard {
                    StatRow(stringResource(R.string.state_taken), Formats.number(context, s.taken))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatRow(stringResource(R.string.state_missed), Formats.number(context, s.missed))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatRow(stringResource(R.string.state_skipped), Formats.number(context, s.skipped))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatRow(stringResource(R.string.stats_streak), stringResource(R.string.today_streak, Formats.number(context, s.streak)))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatRow(stringResource(R.string.stats_points), Formats.number(context, points))
                }
            }}
            item { Arrive(arrival, 3) {
                SectionTitle(stringResource(R.string.stats_by_day))
                Text(stringResource(R.string.stats_by_day_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DayBars(s)
            }}
            if (s.perMedication.isNotEmpty()) {
                item { Arrive(arrival, 4) { SectionTitle(stringResource(R.string.stats_by_medicine)) }}
                items(s.perMedication, key = { it.medicationId }) { m -> RateCard(m) }
            }
            val more = extra
            if (more != null && more.habits.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.health_habits)) }
                items(more.habits, key = { "habit-${it.medicationId}" }) { m -> RateCard(m) }
            }
            if (more != null && more.readings.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.stats_readings)) }
                items(more.readings.entries.toList(), key = { "reading-${it.key}" }) { (type, list) ->
                    val latest = list.first()
                    val (avg, avg2) = averageOf(type, list)
                    MediCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(CareText.measureName(context, type), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            LevelChip(Readings.level(type, latest.value, latest.value2, SugarTiming.entries.firstOrNull { it.name == latest.timing }))
                        }
                        Text(
                            stringResource(R.string.stats_reading_line, CareText.value(context, latest), CareText.value(context, type, avg, avg2)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            Formats.digits(context, LocalResources.current.getQuantityString(R.plurals.readings_count, list.size, list.size)),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (s.expected == 0 && more?.habits.isNullOrEmpty() && more?.readings.isNullOrEmpty()) {
                item { Arrive(arrival, 5) {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedArt(Art.CHART, Hue.SKY, size = 140.dp)
                        Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }}
            }
            item { Arrive(arrival, 6) {
                SecondaryButton(
                    stringResource(R.string.stats_share),
                    onClick = {
                        val text = reportText(context, s, days, viewModel.userName, extra)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    },
                    icon = Icons.Default.Share,
                )
            }}
        }
    }
}

/** Habits, and medicines and the like that are counted for the report. */
data class StatsExtra(
    val habits: List<MedicationAdherence>,
    /** Readings in the period, newest first, by kind. */
    val readings: Map<MeasureType, List<Reading>>,
)

@Composable
private fun RateCard(m: MedicationAdherence) {
    val context = LocalContext.current
    MediCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(m.rate?.let { Formats.number(context, it) + "%" } ?: "—", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(R.string.stats_doses_of, Formats.number(context, m.taken), Formats.number(context, m.expected)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (m.rate ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            color = LocalStatusColors.current.taken,
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** One bar per day: green from the bottom = taken share, red from the top = missed share. */
@Composable
private fun DayBars(s: AdherenceSummary) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val status = LocalStatusColors.current
    Canvas(Modifier.fillMaxWidth().height(130.dp).padding(top = 10.dp)) {
        val n = s.days.size.coerceAtLeast(1)
        val gap = if (n > 40) 1.dp.toPx() else 4.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        s.days.forEachIndexed { i, d ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 4))
            if (d.expected > 0) {
                val takenH = size.height * d.taken / d.expected
                val missedH = size.height * d.missed / d.expected
                drawRoundRect(status.taken, Offset(x, size.height - takenH), Size(w, takenH), CornerRadius(w / 4))
                if (missedH > 0) drawRoundRect(status.missed, Offset(x, 0f), Size(w, missedH), CornerRadius(w / 4))
            }
        }
    }
    // Which day each bar is (the week view has room for the day's initial)
    if (s.days.size in 2..10) {
        val locale = Formats.locale(LocalContext.current)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            s.days.forEach { d ->
                Text(
                    d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A short report to send to a doctor or family member. */
private fun reportText(context: android.content.Context, s: AdherenceSummary, days: Int, name: String, extra: StatsExtra?): String = buildString {
    appendLine(context.getString(R.string.stats_report_header, name.ifBlank { context.getString(R.string.app_name) }, Formats.number(context, days)))
    appendLine(context.getString(R.string.stats_report_rate, s.rate?.let { Formats.number(context, it) + "%" } ?: "—", Formats.number(context, s.taken), Formats.number(context, s.expected)))
    appendLine(context.getString(R.string.stats_report_missed, Formats.number(context, s.missed)))
    s.perMedication.forEach { m ->
        appendLine("• ${m.name}: " + (m.rate?.let { Formats.number(context, it) + "%" } ?: "—"))
    }
    if (extra != null && extra.readings.isNotEmpty()) {
        appendLine()
        appendLine(context.getString(R.string.stats_report_readings))
        extra.readings.forEach { (type, list) ->
            val (avg, avg2) = averageOf(type, list)
            appendLine(
                context.getString(
                    R.string.stats_report_reading_line,
                    CareText.measureName(context, type),
                    CareText.value(context, list.first()),
                    CareText.value(context, type, avg, avg2),
                    Formats.number(context, list.size),
                )
            )
        }
    }
    if (extra != null && extra.habits.isNotEmpty()) {
        appendLine()
        appendLine(context.getString(R.string.stats_report_habits))
        extra.habits.forEach { m -> appendLine("• ${m.name}: " + (m.rate?.let { Formats.number(context, it) + "%" } ?: "—")) }
    }
    append(context.getString(R.string.stats_report_footer))
}
