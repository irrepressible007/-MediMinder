package com.example.mediminder.ui.health

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.HealthRepository
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.MeasureType
import com.example.mediminder.domain.ReadingLevel
import com.example.mediminder.domain.Readings
import com.example.mediminder.domain.SugarTiming
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.domain.timeOf
import com.example.mediminder.domain.toEpochMillis
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.CareAvatar
import com.example.mediminder.ui.components.ChoiceChips
import com.example.mediminder.ui.components.ChoiceTile
import com.example.mediminder.ui.components.DiscardDialog
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.ui.components.NoticeCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.ui.components.ValueRow
import com.example.mediminder.ui.components.careIcon
import com.example.mediminder.ui.components.showDatePicker
import com.example.mediminder.ui.components.showTimePicker
import com.example.mediminder.util.AppLocale
import com.example.mediminder.util.CareText
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class RecordState(
    val loading: Boolean = true,
    val type: MeasureType? = null,
    /** Came from a reminder or a kind's own page: the kind is already known. */
    val fixedType: Boolean = false,
    val medicationId: Long? = null,
    val scheduleId: Long? = null,
    val day: Long? = null,
    val value: String = "",
    val value2: String = "",
    val pulse: String = "",
    /** Sugar meters show mmol/L or mg/dL; thermometers °F or °C. Saved as mmol/L and °F. */
    val mgdl: Boolean = false,
    val celsius: Boolean = false,
    val timing: SugarTiming = SugarTiming.FASTING,
    val date: LocalDate = LocalDate.now(),
    val minute: Int = minuteOf(LocalTime.now()),
    val note: String = "",
    @StringRes val error: Int? = null,
    val saving: Boolean = false,
    /** The saved reading, in words, once saved. */
    val saved: String? = null,
) {
    val started: Boolean get() = value.isNotBlank() || value2.isNotBlank() || pulse.isNotBlank() || note.isNotBlank()
}

@HiltViewModel
class RecordViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val health: HealthRepository,
    private val db: MediMinderDatabase,
) : ViewModel() {
    private val _state = MutableStateFlow(RecordState())
    val state: StateFlow<RecordState> = _state

    private var loaded = false

    fun load(type: String, medicationId: Long, scheduleId: Long, day: Long) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val fromSchedule = scheduleId.takeIf { it != 0L }?.let { db.scheduleDao().getById(it) }
            val medId = fromSchedule?.medicationId ?: medicationId.takeIf { it != 0L }
            val medication = medId?.let { db.medicationDao().getById(it) }
            val known = MeasureType.entries.firstOrNull { it.name == type }
                ?: medication?.takeIf { it.kind == CareKind.MEASURE }?.let { m -> MeasureType.entries.firstOrNull { it.name == m.subtype } }
            val hour = LocalTime.now().hour
            _state.update {
                it.copy(
                    loading = false,
                    type = known,
                    fixedType = known != null,
                    medicationId = medication?.id,
                    scheduleId = fromSchedule?.id,
                    day = fromSchedule?.let { day.takeIf { d -> d >= 0 } },
                    // first thing in the morning is usually the fasting test
                    timing = if (hour < 10) SugarTiming.FASTING else SugarTiming.RANDOM,
                )
            }
        }
    }

    fun onType(t: MeasureType) = _state.update { it.copy(type = t, value = "", value2 = "", pulse = "", error = null) }
    fun onValue(v: String) = _state.update { it.copy(value = v, error = null) }
    fun onValue2(v: String) = _state.update { it.copy(value2 = v, error = null) }
    fun onPulse(v: String) = _state.update { it.copy(pulse = v, error = null) }
    fun onMgdl(on: Boolean) = _state.update { it.copy(mgdl = on, error = null) }
    fun onCelsius(on: Boolean) = _state.update { it.copy(celsius = on, error = null) }
    fun onTiming(t: SugarTiming) = _state.update { it.copy(timing = t) }
    fun onDate(d: LocalDate) = _state.update { it.copy(date = d) }
    fun onMinute(m: Int) = _state.update { it.copy(minute = m) }
    fun onNote(v: String) = _state.update { it.copy(note = v) }

    fun save() {
        val s = _state.value
        val reading = readingOf(s)
        if (reading == null) {
            _state.update { it.copy(error = R.string.record_error) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            health.record(reading, s.scheduleId, s.day)
            val ctx = AppLocale.localized(context)
            val type = s.type ?: return@launch
            val words = listOfNotNull(
                CareText.value(ctx, reading),
                CareText.levelName(ctx, Readings.level(type, reading.value, reading.value2, s.timing)),
            ).joinToString(" · ")
            _state.update { it.copy(saving = false, saved = words) }
        }
    }

    /** What would be saved, in stored units, or null if something is missing or implausible. */
    fun readingOf(s: RecordState): Reading? {
        val type = s.type ?: return null
        var value = number(s.value) ?: return null
        val value2 = if (type == MeasureType.BLOOD_PRESSURE) number(s.value2) ?: return null else null
        val pulse = s.pulse.takeIf { it.isNotBlank() }?.let { number(it)?.toInt() ?: return null }
        if (type == MeasureType.BLOOD_SUGAR && s.mgdl) value = Readings.mgdlToMmol(value)
        if (type == MeasureType.TEMPERATURE && s.celsius) value = Readings.celsiusToF(value)
        if (!Readings.plausible(type, value, value2, pulse)) return null
        return Reading(
            medicationId = s.medicationId,
            type = type.name,
            value = value,
            value2 = value2,
            pulse = pulse,
            timing = if (type == MeasureType.BLOOD_SUGAR) s.timing.name else null,
            takenAt = s.date.atTime(timeOf(s.minute)).toEpochMillis(),
            note = s.note.trim().ifBlank { null },
        )
    }

    companion object {
        /** Bangla keyboards type ০-৯; some use a comma for the decimal point. */
        fun number(text: String): Double? {
            val ascii = text.trim().map { ch -> if (ch in '০'..'৯') '0' + (ch - '০') else ch }.joinToString("").replace(',', '.')
            return ascii.toDoubleOrNull()?.takeIf { it.isFinite() }
        }
    }
}

/** Writing down one reading: blood pressure, sugar, weight, temperature or oxygen. */
@Composable
fun RecordReadingScreen(
    type: String,
    medicationId: Long,
    scheduleId: Long,
    day: Long,
    onBack: () -> Unit,
    viewModel: RecordViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load(type, medicationId, scheduleId, day) }
    val context = LocalContext.current
    val s by viewModel.state.collectAsState()
    LaunchedEffect(s.saved) {
        val words = s.saved ?: return@LaunchedEffect
        FeedbackBus.show(FeedbackKind.SAVED, R.string.feedback_reading_saved, words)
        onBack()
    }
    var confirmLeave by remember { mutableStateOf(false) }
    BackHandler(enabled = s.started) { confirmLeave = true }
    if (confirmLeave) DiscardDialog(stringResource(R.string.care_discard_title), onDiscard = { confirmLeave = false; onBack() }, onKeep = { confirmLeave = false })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.health_record), onBack = { if (s.started) confirmLeave = true else onBack() }, cancel = true) },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding().imePadding()) {
                s.error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(
                    stringResource(R.string.record_save),
                    onClick = viewModel::save,
                    enabled = s.type != null && s.value.isNotBlank() && !s.saving,
                )
            }
        },
    ) { padding ->
        if (s.loading) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val chosen = s.type
            if (!s.fixedType) {
                Text(stringResource(R.string.record_what), style = MaterialTheme.typography.headlineSmall)
                MeasureType.entries.forEach { t ->
                    ChoiceTile(
                        title = CareText.measureName(context, t),
                        subtitle = CareText.measureSub(context, t),
                        icon = careIcon(CareKind.MEASURE, t.name),
                        selected = chosen == t,
                        onClick = { viewModel.onType(t) },
                    )
                }
            } else if (chosen != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CareAvatar(CareKind.MEASURE, chosen.name, colorFor(CareKind.MEASURE, chosen.name), size = 56)
                    Spacer(Modifier.width(14.dp))
                    Text(CareText.measureName(context, chosen), style = MaterialTheme.typography.headlineSmall)
                }
            }
            if (chosen == null) return@Column
            if (!s.fixedType) SectionTitle(stringResource(R.string.record_value))

            when (chosen) {
                MeasureType.BLOOD_PRESSURE -> {
                    Text(stringResource(R.string.record_bp_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(s.value, viewModel::onValue, stringResource(R.string.record_top), Modifier.weight(1f), decimal = false)
                        NumberField(s.value2, viewModel::onValue2, stringResource(R.string.record_bottom), Modifier.weight(1f), decimal = false)
                    }
                    NumberField(s.pulse, viewModel::onPulse, stringResource(R.string.record_pulse), decimal = false)
                }
                MeasureType.BLOOD_SUGAR -> {
                    NumberField(s.value, viewModel::onValue, stringResource(R.string.record_value), suffix = stringResource(if (s.mgdl) R.string.unit_mgdl else R.string.unit_mmol))
                    ChoiceChips(listOf(false, true), s.mgdl, { stringResource(if (it) R.string.unit_mgdl else R.string.unit_mmol) }, viewModel::onMgdl)
                    Text(stringResource(R.string.record_timing), style = MaterialTheme.typography.titleMedium)
                    SugarTiming.entries.forEach { t ->
                        ChoiceTile(title = sugarTimingName(t), selected = s.timing == t, onClick = { viewModel.onTiming(t) })
                    }
                }
                MeasureType.WEIGHT ->
                    NumberField(s.value, viewModel::onValue, stringResource(R.string.record_value), suffix = stringResource(R.string.unit_kg))
                MeasureType.TEMPERATURE -> {
                    NumberField(s.value, viewModel::onValue, stringResource(R.string.record_value), suffix = stringResource(if (s.celsius) R.string.unit_c else R.string.unit_f))
                    ChoiceChips(listOf(false, true), s.celsius, { stringResource(if (it) R.string.unit_c else R.string.unit_f) }, viewModel::onCelsius)
                }
                MeasureType.OXYGEN -> {
                    NumberField(s.value, viewModel::onValue, stringResource(R.string.record_value), suffix = "%", decimal = false)
                    NumberField(s.pulse, viewModel::onPulse, stringResource(R.string.record_pulse), decimal = false)
                }
            }

            // As soon as the numbers make sense, say how they compare
            viewModel.readingOf(s)?.let { r ->
                val level = Readings.level(chosen, r.value, r.value2, s.timing)
                if (level != ReadingLevel.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(CareText.value(context, chosen, r.value, r.value2), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        LevelChip(level)
                    }
                }
                if (Readings.urgent(level)) NoticeCard(stringResource(R.string.reading_urgent), Icons.Default.Warning, Tone.DANGER)
            }

            ValueRow(stringResource(R.string.record_time), Formats.time(context, s.minute), onClick = { showTimePicker(context, s.minute, viewModel::onMinute) })
            ValueRow(
                stringResource(R.string.record_date),
                Formats.date(context, s.date),
                onClick = { showDatePicker(context, s.date, onPicked = viewModel::onDate) },
            )
            OutlinedTextField(
                value = s.note,
                onValueChange = viewModel::onNote,
                label = { Text(stringResource(R.string.record_note)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Text(stringResource(R.string.reading_ranges_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(6)) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it, style = MaterialTheme.typography.titleMedium) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun sugarTimingName(t: SugarTiming): String = stringResource(
    when (t) {
        SugarTiming.FASTING -> R.string.sugar_fasting
        SugarTiming.AFTER_MEAL -> R.string.sugar_after
        SugarTiming.RANDOM -> R.string.sugar_random
    }
)
