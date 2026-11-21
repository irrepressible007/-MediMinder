package com.example.mediminder.ui.health

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.HabitType
import com.example.mediminder.domain.MeasureType
import com.example.mediminder.ui.components.AlarmPermissionNote
import com.example.mediminder.ui.components.AnimatedArt
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.components.ChoiceTile
import com.example.mediminder.ui.components.ConfirmDialog
import com.example.mediminder.ui.components.DiscardDialog
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.TimePill
import com.example.mediminder.ui.components.careIcon
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.util.AppLocale
import com.example.mediminder.util.CareText
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import javax.inject.Inject

data class CareTimeDraft(val key: Long, val scheduleId: Long = 0, val minuteOfDay: Int)

data class CareEditorState(
    val loading: Boolean = true,
    val id: Long = 0,
    val original: Medication? = null,
    val kind: CareKind = CareKind.MEASURE,
    /** A MeasureType or HabitType name; null until one is chosen. */
    val subtype: String? = null,
    /** Only for a habit of the person's own ("Something else"). */
    val name: String = "",
    val times: List<CareTimeDraft> = emptyList(),
    /** Once the person moves a time, choosing another kind no longer resets the times. */
    val timesEdited: Boolean = false,
    val daysMask: Int = DaysOfWeek.EVERY_DAY,
    val alertType: AlertType = AlertType.REMINDER,
    @StringRes val error: Int? = null,
    val saving: Boolean = false,
    val savedId: Long? = null,
    val savedName: String = "",
    val deleted: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
}

@HiltViewModel
class CareEditorViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val repository: MedicineRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CareEditorState())
    val state: StateFlow<CareEditorState> = _state
    private var nextKey = 1L
    private var loaded = false

    fun load(kind: CareKind, medicationId: Long) {
        if (loaded) return
        loaded = true
        if (medicationId == 0L) {
            _state.update { it.copy(loading = false, kind = kind) }
            return
        }
        viewModelScope.launch {
            val item = repository.get(medicationId) ?: run { _state.update { it.copy(loading = false, kind = kind) }; return@launch }
            val m = item.medication
            val schedules = item.schedules.sortedBy { it.minuteOfDay }
            _state.update {
                it.copy(
                    loading = false,
                    id = m.id,
                    original = m,
                    kind = m.kind,
                    subtype = m.subtype,
                    name = if (CareText.habitOf(m.subtype) == HabitType.OTHER) m.name else "",
                    times = schedules.map { s -> CareTimeDraft(nextKey++, s.id, s.minuteOfDay) },
                    timesEdited = true,
                    daysMask = schedules.firstOrNull()?.daysMask ?: DaysOfWeek.EVERY_DAY,
                    alertType = schedules.firstOrNull()?.alertType ?: AlertType.REMINDER,
                )
            }
        }
    }

    fun onSubtype(subtype: String) = _state.update { s ->
        if (s.timesEdited) s.copy(subtype = subtype, error = null)
        else s.copy(
            subtype = subtype,
            times = presetTimes(subtype).map { CareTimeDraft(nextKey++, minuteOfDay = it) },
            daysMask = presetDays(subtype),
            error = null,
        )
    }

    fun onName(v: String) = _state.update { it.copy(name = v, error = null) }

    fun addTime() = _state.update { s ->
        val last = s.times.maxOfOrNull { it.minuteOfDay }
        val minute = if (last == null) 9 * 60 else (last + 120).coerceAtMost(23 * 60)
        s.copy(times = (s.times + CareTimeDraft(nextKey++, minuteOfDay = minute)).sortedBy { it.minuteOfDay }, timesEdited = true, error = null)
    }

    fun removeTime(key: Long) = _state.update { s -> s.copy(times = s.times.filter { it.key != key }, timesEdited = true) }

    fun onTime(key: Long, minute: Int) = _state.update { s ->
        s.copy(times = s.times.map { if (it.key == key) it.copy(minuteOfDay = minute) else it }.sortedBy { it.minuteOfDay }, timesEdited = true)
    }

    fun onDays(mask: Int) = _state.update { it.copy(daysMask = mask, error = null) }
    fun onAlert(type: AlertType) = _state.update { it.copy(alertType = type) }

    fun save() {
        val s = _state.value
        val subtype = s.subtype ?: return _state.update { it.copy(error = if (s.kind == CareKind.MEASURE) R.string.care_what_check else R.string.care_what_habit) }
        val own = s.kind == CareKind.HABIT && subtype == HabitType.OTHER.name
        when {
            own && s.name.isBlank() -> return _state.update { it.copy(error = R.string.care_error_name) }
            s.times.isEmpty() -> return _state.update { it.copy(error = R.string.care_error_times) }
            s.daysMask == 0 -> return _state.update { it.copy(error = R.string.editor_error_days) }
        }
        // Kept in the app's language for lists such as History; shown by type everywhere else
        val name = if (own) s.name.trim() else CareText.name(AppLocale.localized(context), s.kind, subtype, subtype)
        val medication = (s.original ?: Medication(name = name)).copy(
            name = name,
            kind = s.kind,
            subtype = subtype,
            strength = "",
            form = "",
            unit = "tablet",
            mealTiming = MealTiming.ANY,
            color = colorFor(s.kind, subtype),
            isAsNeeded = false,
            trackStock = false,
            startDate = s.original?.startDate ?: LocalDate.now().toEpochDay(),
        )
        val schedules = s.times.map { t ->
            Schedule(id = t.scheduleId, medicationId = s.id, minuteOfDay = t.minuteOfDay, daysMask = s.daysMask, quantity = 1.0, alertType = s.alertType, enabled = true)
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val id = repository.save(medication, schedules)
            _state.update { it.copy(saving = false, savedId = id, savedName = name) }
        }
    }

    fun delete() = viewModelScope.launch {
        val s = _state.value
        if (s.isNew) return@launch
        repository.delete(s.id, keepHistory = true)
        FeedbackBus.show(FeedbackKind.DELETED, R.string.feedback_care_deleted, s.original?.name.orEmpty())
        _state.update { it.copy(deleted = true) }
    }

    companion object {
        /** Sensible first times, which the person can change. */
        fun presetTimes(subtype: String): List<Int> = when (subtype) {
            MeasureType.BLOOD_PRESSURE.name -> listOf(8 * 60, 20 * 60)
            MeasureType.BLOOD_SUGAR.name -> listOf(7 * 60)
            MeasureType.TEMPERATURE.name -> listOf(8 * 60, 14 * 60, 20 * 60)
            MeasureType.WEIGHT.name, MeasureType.OXYGEN.name -> listOf(8 * 60)
            HabitType.WATER.name -> (8..20 step 2).map { it * 60 }
            HabitType.WALK.name -> listOf(17 * 60)
            HabitType.EXERCISE.name -> listOf(7 * 60 + 30)
            HabitType.FOOT_CHECK.name -> listOf(21 * 60)
            HabitType.SLEEP.name -> listOf(22 * 60)
            else -> listOf(9 * 60)
        }

        /** Weight is usually checked once a week; Friday morning. */
        fun presetDays(subtype: String): Int =
            if (subtype == MeasureType.WEIGHT.name) DaysOfWeek.bit(DayOfWeek.FRIDAY) else DaysOfWeek.EVERY_DAY
    }
}

/** Setting up (or changing) a reminder to check something, or a healthy habit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CareEditorScreen(
    kind: CareKind,
    medicationId: Long,
    onBack: () -> Unit,
    viewModel: CareEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load(kind, medicationId) }
    val context = LocalContext.current
    val s by viewModel.state.collectAsState()
    LaunchedEffect(s.savedId) {
        if (s.savedId == null) return@LaunchedEffect
        FeedbackBus.show(if (s.isNew) FeedbackKind.ADDED else FeedbackKind.SAVED, if (s.isNew) R.string.feedback_medicine_added else R.string.feedback_medicine_saved, s.savedName)
        onBack()
    }
    LaunchedEffect(s.deleted) { if (s.deleted) onBack() }
    val started = s.isNew && s.subtype != null
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = started) { confirmLeave = true }
    if (confirmLeave) DiscardDialog(stringResource(R.string.care_discard_title), onDiscard = { confirmLeave = false; onBack() }, onKeep = { confirmLeave = false })
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.care_delete_title),
            text = stringResource(R.string.care_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
    val measure = s.kind == CareKind.MEASURE
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(
                    when {
                        measure && s.isNew -> R.string.care_new_check
                        measure -> R.string.care_edit_check
                        s.isNew -> R.string.care_new_habit
                        else -> R.string.care_edit_habit
                    }
                ),
                onBack = { if (started) confirmLeave = true else onBack() },
                cancel = true,
                actions = {
                    if (!s.isNew) IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding().imePadding()) {
                s.error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(stringResource(R.string.action_save), onClick = viewModel::save, enabled = !s.saving && !s.loading)
            }
        },
    ) { padding ->
        if (s.loading) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (LocalConfiguration.current.screenHeightDp >= 480) {
                    AnimatedArt(if (measure) Art.THERMO else Art.GLASS, if (measure) Hue.CORAL else Hue.SKY, size = 84.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(stringResource(if (measure) R.string.care_what_check else R.string.care_what_habit), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            if (measure) {
                MeasureType.entries.forEach { t ->
                    ChoiceTile(
                        title = CareText.measureName(context, t),
                        subtitle = CareText.measureSub(context, t),
                        icon = careIcon(CareKind.MEASURE, t.name),
                        selected = s.subtype == t.name,
                        onClick = { viewModel.onSubtype(t.name) },
                    )
                }
            } else {
                HabitType.entries.forEach { t ->
                    ChoiceTile(
                        title = CareText.habitName(context, t),
                        subtitle = CareText.habitSub(context, t),
                        icon = careIcon(CareKind.HABIT, t.name),
                        selected = s.subtype == t.name,
                        onClick = { viewModel.onSubtype(t.name) },
                    )
                }
                if (s.subtype == HabitType.OTHER.name) {
                    OutlinedTextField(
                        value = s.name,
                        onValueChange = viewModel::onName,
                        label = { Text(stringResource(R.string.care_name)) },
                        placeholder = { Text(stringResource(R.string.care_name_hint)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (s.subtype == null) return@Column

            SectionTitle(stringResource(R.string.care_when))
            s.times.forEach { t ->
                MediCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimePill(t.minuteOfDay, onChange = { viewModel.onTime(t.key, it) }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.removeTime(t.key) }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            SecondaryButton(stringResource(R.string.action_add_time), onClick = viewModel::addTime, icon = Icons.Default.Add)

            Text(stringResource(R.string.editor_days), style = MaterialTheme.typography.titleMedium)
            val everyDay = s.daysMask == DaysOfWeek.EVERY_DAY
            ChoiceTile(title = stringResource(R.string.days_every_day), selected = everyDay, onClick = { viewModel.onDays(DaysOfWeek.EVERY_DAY) })
            ChoiceTile(
                title = stringResource(R.string.days_selected),
                selected = !everyDay,
                onClick = { if (everyDay) viewModel.onDays(DaysOfWeek.bit(DayOfWeek.FRIDAY)) },
            )
            if (!everyDay) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WEEK.forEach { day ->
                        DayChip(day.getDisplayName(TextStyle.SHORT, Formats.locale(context)), DaysOfWeek.includes(s.daysMask, day)) {
                            viewModel.onDays(s.daysMask xor DaysOfWeek.bit(day))
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.wiz_alert_title))
            listOf(AlertType.REMINDER, AlertType.ALARM).forEach { type ->
                ChoiceTile(
                    title = stringResource(if (type == AlertType.ALARM) R.string.wiz_alert_alarm else R.string.wiz_alert_reminder),
                    subtitle = stringResource(if (type == AlertType.ALARM) R.string.wiz_alert_alarm_sub else R.string.wiz_alert_reminder_sub),
                    icon = if (type == AlertType.ALARM) Icons.Default.Alarm else Icons.Default.Notifications,
                    selected = s.alertType == type,
                    onClick = { viewModel.onAlert(type) },
                )
            }
            if (s.alertType == AlertType.ALARM) AlarmPermissionNote()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DayChip(label: String, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (on) scheme.primary else scheme.surface)
            .border(1.5.dp, if (on) scheme.primary else scheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) scheme.onPrimary else scheme.onSurface)
    }
}
