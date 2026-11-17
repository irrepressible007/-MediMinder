package com.example.mediminder.ui.more

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
    private val settings: SettingsRepository,
) : ViewModel() {
    val days = MutableStateFlow(7)
    private val today = LocalDate.now()

    val summary: StateFlow<AdherenceSummary?> = days.flatMapLatest { n ->
        combine(medicines.observeAll(), db.doseLogDao().observeRange(today.minusDays(n - 1L).toEpochDay(), today.toEpochDay())) { meds, logs ->
            Adherence.summarise(meds, logs, today.minusDays(n - 1L), today, LocalDateTime.now())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
    val points by viewModel.points.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.stats_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ChoiceChips(listOf(7, 30, 90), days, { n -> Formats.digits(context, context.resources.getQuantityString(R.plurals.days_count, n, n)) }, { viewModel.days.value = it })
            }
            val s = summary ?: return@LazyColumn
            item {
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
            }
            item {
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
            }
            item {
                SectionTitle(stringResource(R.string.stats_by_day))
                Text(stringResource(R.string.stats_by_day_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DayBars(s)
            }
            if (s.perMedication.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.stats_by_medicine)) }
                items(s.perMedication, key = { it.medicationId }) { m ->
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
                            color = status.taken,
                            trackColor = MaterialTheme.colorScheme.surfaceContainer,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
            }
            if (s.expected == 0) {
                item { Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                SecondaryButton(
                    stringResource(R.string.stats_share),
                    onClick = {
                        val text = reportText(context, s, days, viewModel.userName)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    },
                    icon = Icons.Default.Share,
                )
            }
        }
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
}

/** A short report to send to a doctor or family member. */
private fun reportText(context: android.content.Context, s: AdherenceSummary, days: Int, name: String): String = buildString {
    appendLine(context.getString(R.string.stats_report_header, name.ifBlank { context.getString(R.string.app_name) }, Formats.number(context, days)))
    appendLine(context.getString(R.string.stats_report_rate, s.rate?.let { Formats.number(context, it) + "%" } ?: "—", Formats.number(context, s.taken), Formats.number(context, s.expected)))
    appendLine(context.getString(R.string.stats_report_missed, Formats.number(context, s.missed)))
    s.perMedication.forEach { m ->
        appendLine("• ${m.name}: " + (m.rate?.let { Formats.number(context, it) + "%" } ?: "—"))
    }
    append(context.getString(R.string.stats_report_footer))
}
