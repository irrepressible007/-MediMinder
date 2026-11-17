package com.example.mediminder.ui.more

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.Adherence
import com.example.mediminder.domain.AdherenceSummary
import com.example.mediminder.ui.theme.MissedRed
import com.example.mediminder.ui.theme.TakenGreen
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

    val userName: String get() = settings.current.userName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val days by viewModel.days.collectAsState()
    val summary by viewModel.summary.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
                actions = {
                    summary?.let { s ->
                        IconButton(onClick = {
                            val text = reportText(context, s, days, viewModel.userName)
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                        }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share)) }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(7, 30, 90).forEachIndexed { i, n ->
                        SegmentedButton(days == n, { viewModel.days.value = n }, SegmentedButtonDefaults.itemShape(i, 3)) {
                            Text(context.resources.getQuantityString(R.plurals.days_count, n, n))
                        }
                    }
                }
            }
            val s = summary ?: return@LazyColumn
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.rate?.let { Formats.number(context, it) + "%" } ?: "—", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.stats_rate_label), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Stat(stringResource(R.string.state_taken), Formats.number(context, s.taken))
                            Stat(stringResource(R.string.state_missed), Formats.number(context, s.missed))
                            Stat(stringResource(R.string.state_skipped), Formats.number(context, s.skipped))
                            Stat(stringResource(R.string.stats_streak), Formats.number(context, s.streak))
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.stats_by_day), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                DayBars(s)
            }
            if (s.perMedication.isNotEmpty()) {
                item { Text(stringResource(R.string.stats_by_medicine), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(s.perMedication, key = { it.medicationId }) { m ->
                    Column {
                        Row {
                            Text(m.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(
                                (m.rate?.let { Formats.number(context, it) + "%" } ?: "—") + "  (" + Formats.number(context, m.taken) + "/" + Formats.number(context, m.expected) + ")",
                            )
                        }
                        LinearProgressIndicator(progress = { (m.rate ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
            if (s.expected == 0) {
                item { Text(stringResource(R.string.stats_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** One bar per day: green = taken share, red = missed share. */
@Composable
private fun DayBars(s: AdherenceSummary) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp)) {
        val n = s.days.size.coerceAtLeast(1)
        val gap = if (n > 40) 1.dp.toPx() else 4.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        s.days.forEachIndexed { i, d ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 4))
            if (d.expected > 0) {
                val takenH = size.height * d.taken / d.expected
                val missedH = size.height * d.missed / d.expected
                drawRoundRect(TakenGreen, Offset(x, size.height - takenH), Size(w, takenH), CornerRadius(w / 4))
                if (missedH > 0) drawRoundRect(MissedRed.copy(alpha = 0.8f), Offset(x, 0f), Size(w, missedH), CornerRadius(w / 4))
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
