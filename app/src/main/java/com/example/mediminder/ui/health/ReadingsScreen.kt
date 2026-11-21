package com.example.mediminder.ui.health

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.HealthRepository
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.domain.MeasureType
import com.example.mediminder.domain.Readings
import com.example.mediminder.domain.SugarTiming
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.ConfirmDialog
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.util.CareText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadingsViewModel @Inject constructor(private val health: HealthRepository) : ViewModel() {
    private val type = MutableStateFlow<String?>(null)

    val readings: StateFlow<List<Reading>?> = type.filterNotNull().flatMapLatest { health.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(t: String) { type.value = t }

    fun delete(reading: Reading) = viewModelScope.launch { health.delete(reading.id) }
}

/** The average of [readings]: blood pressure as whole numbers, everything else to one decimal. */
internal fun averageOf(type: MeasureType, readings: List<Reading>): Pair<Double, Double?> {
    fun round(v: Double) = if (type == MeasureType.BLOOD_PRESSURE || type == MeasureType.OXYGEN) Math.round(v).toDouble() else Math.round(v * 10) / 10.0
    val main = round(readings.map { it.value }.average())
    val second = readings.mapNotNull { it.value2 }.takeIf { it.isNotEmpty() }?.average()?.let(::round)
    return main to second
}

/** Every reading of one kind, with a chart over time. */
@Composable
fun ReadingsScreen(
    type: String,
    onBack: () -> Unit,
    onRecord: (type: String) -> Unit,
    viewModel: ReadingsViewModel = hiltViewModel(),
) {
    LaunchedEffect(type) { viewModel.load(type) }
    val context = LocalContext.current
    val measure = MeasureType.entries.firstOrNull { it.name == type } ?: return
    val list by viewModel.readings.collectAsState()
    var deleting by remember { mutableStateOf<Reading?>(null) }
    deleting?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.readings_delete_title),
            text = CareText.value(context, r) + "\n" + whenText(context, r.takenAt),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.delete(r)
                FeedbackBus.show(FeedbackKind.DELETED, R.string.feedback_care_deleted, CareText.value(context, r))
                deleting = null
            },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = CareText.measureName(context, measure), onBack = onBack) },
    ) { padding ->
        val readings = list ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (readings.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.EditNote,
                        title = CareText.measureName(context, measure),
                        body = stringResource(R.string.health_no_readings),
                        hue = Hue.ROSE,
                    ) { PrimaryButton(stringResource(R.string.health_record), onClick = { onRecord(type) }, icon = Icons.Default.EditNote) }
                }
                return@LazyColumn
            }
            val latest = readings.first()
            item {
                MediCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(CareText.value(context, latest), style = MaterialTheme.typography.headlineMedium)
                            Text(whenText(context, latest.takenAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LevelChip(Readings.level(measure, latest.value, latest.value2, SugarTiming.entries.firstOrNull { it.name == latest.timing }))
                    }
                    Spacer(Modifier.height(12.dp))
                    if (readings.size >= 2) {
                        ReadingChart(measure, readings, height = 200.dp)
                        Spacer(Modifier.height(8.dp))
                        val shown = readings.take(30)
                        val (avg, avg2) = averageOf(measure, shown)
                        Text(
                            stringResource(R.string.readings_average, CareText.value(context, measure, avg, avg2)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        Text(stringResource(R.string.readings_chart_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { PrimaryButton(stringResource(R.string.health_record), onClick = { onRecord(type) }, icon = Icons.Default.EditNote) }
            item { SectionTitle(stringResource(R.string.readings_all)) }
            items(readings, key = { it.id }) { r ->
                MediCard(contentPadding = PaddingValues(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(CareText.value(context, r), style = MaterialTheme.typography.titleLarge)
                            val timing = SugarTiming.entries.firstOrNull { it.name == r.timing }
                            Text(
                                listOfNotNull(whenText(context, r.takenAt), timing?.let { sugarTimingName(it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            r.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                        LevelChip(Readings.level(measure, r.value, r.value2, SugarTiming.entries.firstOrNull { it.name == r.timing }))
                        IconButton(onClick = { deleting = r }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_reading), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.reading_ranges_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
