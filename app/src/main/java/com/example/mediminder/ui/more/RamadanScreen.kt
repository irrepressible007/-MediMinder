package com.example.mediminder.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.RamadanManager
import com.example.mediminder.data.settings.RamadanSettings
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.MealSchedule
import com.example.mediminder.domain.Ramadan
import com.example.mediminder.ui.components.AnimatedArt
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.NoticeCard
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.SwitchRow
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.ui.components.ValueRow
import com.example.mediminder.ui.components.showDatePicker
import com.example.mediminder.ui.components.showTimePicker
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.util.CareText
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One dose time: where it is normally, and where it is during Ramadan. */
data class RamadanMove(
    val medicationId: Long,
    val name: String,
    val kind: CareKind,
    val subtype: String?,
    val from: Int,
    val to: Int,
)

data class RamadanUi(
    val r: RamadanSettings,
    /** This year's (or the next) expected first day. */
    val start: LocalDate?,
    /** Meal-based dose times, which move by themselves. */
    val moves: List<RamadanMove>,
    /** Times set by hand that fall while fasting. */
    val duringFast: List<RamadanMove>,
)

@HiltViewModel
class RamadanViewModel @Inject constructor(
    settings: SettingsRepository,
    medicines: MedicineRepository,
    private val ramadan: RamadanManager,
) : ViewModel() {
    val state: StateFlow<RamadanUi?> = combine(settings.settings, medicines.observeAll()) { s, meds ->
        val r = s.ramadan
        val usual = r.usualMeals ?: s.mealTimes
        val fasting = Ramadan.meals(r)
        val active = meds.filter { !it.medication.archived && !it.medication.isAsNeeded }
        val moves = active.filter { it.medication.kind.isMedicine }.flatMap { item ->
            val m = item.medication
            item.schedules.filter { it.enabled && it.slot != null }.sortedBy { it.minuteOfDay }.map { sch ->
                RamadanMove(
                    m.id, listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "), m.kind, m.subtype,
                    from = MealSchedule.minuteFor(sch.slot!!, m.mealTiming, usual, s.beforeMealMinutes, s.afterMealMinutes),
                    to = MealSchedule.minuteFor(sch.slot, m.mealTiming, fasting, s.beforeMealMinutes, s.afterMealMinutes),
                )
            }
        }
        val duringFast = active.flatMap { item ->
            val m = item.medication
            item.schedules.filter { it.enabled && it.slot == null && Ramadan.duringFast(it.minuteOfDay, r) }.map { sch ->
                RamadanMove(m.id, listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "), m.kind, m.subtype, sch.minuteOfDay, sch.minuteOfDay)
            }
        }
        RamadanUi(r, Ramadan.estimatedStart(LocalDate.now()), moves, duringFast)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setOn(on: Boolean) = viewModelScope.launch {
        val ui = state.value ?: return@launch
        if (on) {
            val until = ui.r.until?.let(LocalDate::ofEpochDay)
                ?: ui.start?.let(Ramadan::estimatedEnd)?.takeIf { !it.isBefore(LocalDate.now()) }
                ?: LocalDate.now().plusDays(Ramadan.DAYS - 1L)
            val moved = ramadan.turnOn(until)
            FeedbackBus.show(FeedbackKind.SAVED, R.string.feedback_ramadan_on, moved.toString())
        } else {
            ramadan.turnOff()
            FeedbackBus.show(FeedbackKind.SAVED, R.string.feedback_ramadan_off)
        }
    }

    fun setSehri(m: Int) = viewModelScope.launch { ramadan.setTimes(sehri = m) }
    fun setIftar(m: Int) = viewModelScope.launch { ramadan.setTimes(iftar = m) }
    fun setLateNight(m: Int) = viewModelScope.launch { ramadan.setTimes(lateNight = m) }
    fun setUntil(d: LocalDate) = ramadan.setUntil(d)
}

/** Ramadan mode: medicines taken with meals move to Sehri and Iftar for the month. */
@Composable
fun RamadanScreen(
    onBack: () -> Unit,
    onOpenItem: (kind: CareKind, id: Long) -> Unit,
    viewModel: RamadanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.state.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.ramadan_title), onBack = onBack) },
    ) { padding ->
        val s = ui ?: return@Scaffold
        val r = s.r
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedArt(Art.CRESCENT, Hue.NIGHT, size = 96.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ramadan_intro), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            MediCard {
                SwitchRow(
                    title = stringResource(R.string.ramadan_title),
                    subtitle = when {
                        r.on && r.until != null -> stringResource(R.string.ramadan_on_until, Formats.date(context, LocalDate.ofEpochDay(r.until)))
                        s.start != null -> stringResource(R.string.ramadan_suggest_title, Formats.date(context, s.start))
                        else -> null
                    },
                    checked = r.on,
                    onChange = viewModel::setOn,
                )
            }
            MediCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp, horizontal = 2.dp)) {
                ValueRow(stringResource(R.string.ramadan_sehri), Formats.time(context, r.sehri), onClick = { showTimePicker(context, r.sehri, viewModel::setSehri) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ValueRow(stringResource(R.string.ramadan_iftar), Formats.time(context, r.iftar), onClick = { showTimePicker(context, r.iftar, viewModel::setIftar) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ValueRow(stringResource(R.string.ramadan_late), Formats.time(context, r.lateNight), onClick = { showTimePicker(context, r.lateNight, viewModel::setLateNight) })
                if (r.on && r.until != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val until = LocalDate.ofEpochDay(r.until)
                    ValueRow(
                        stringResource(R.string.ramadan_until),
                        Formats.date(context, until),
                        onClick = { showDatePicker(context, until, minDate = LocalDate.now(), onPicked = viewModel::setUntil) },
                        subtitle = stringResource(R.string.ramadan_until_hint),
                    )
                }
            }

            SectionTitle(stringResource(R.string.ramadan_moves_title))
            if (s.moves.isEmpty()) {
                Text(stringResource(R.string.ramadan_moves_none), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MediCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                    s.moves.forEachIndexed { i, move ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MoveRow(move, onClick = { onOpenItem(move.kind, move.medicationId) })
                    }
                }
            }

            if (s.duringFast.isNotEmpty()) {
                NoticeCard(stringResource(R.string.ramadan_fast_body), Icons.Default.Warning, Tone.WARNING)
                MediCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                    s.duringFast.forEachIndexed { i, move ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MoveRow(move, onClick = { onOpenItem(move.kind, move.medicationId) })
                    }
                }
            }
            NoticeCard(stringResource(R.string.ramadan_doctor_note), Icons.Default.Info, Tone.INFO)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MoveRow(move: RamadanMove, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(CareText.name(context, move.kind, move.subtype, move.name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            if (move.from == move.to) Formats.time(context, move.to)
            else stringResource(R.string.ramadan_move_line, Formats.time(context, move.from), Formats.time(context, move.to)),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
