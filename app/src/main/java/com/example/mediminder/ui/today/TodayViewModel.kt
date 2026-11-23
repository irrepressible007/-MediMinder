package com.example.mediminder.ui.today

import com.example.mediminder.data.RamadanManager
import com.example.mediminder.data.settings.RamadanSettings
import com.example.mediminder.domain.Ramadan
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.CareSites
import com.example.mediminder.domain.OpenedItems
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.data.AppointmentRepository
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.AppointmentStatus
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.DosePlanner
import com.example.mediminder.domain.DoseState
import com.example.mediminder.domain.Stock
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.domain.toEpochMillis
import com.example.mediminder.reminders.AsNeededResult
import com.example.mediminder.reminders.DoseActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class DoseRow(
    val scheduleId: Long,
    val day: Long,
    val minuteOfDay: Int,
    val medicationId: Long,
    val name: String,
    val strength: String,
    val quantity: Double,
    val unit: String,
    val mealTiming: MealTiming,
    val color: String,
    val state: DoseState,
    val loggedAt: Long?,
    val alertType: AlertType,
    val ringing: Boolean,
    /** When a snoozed dose rings again (epoch millis). */
    val snoozedUntil: Long? = null,
    /** Photo of the box or strip, if the user took one. */
    val photo: String? = null,
    val kind: CareKind = CareKind.PILL,
    val subtype: String? = null,
    /** Where it goes this time (a body area, "eye_left", or this dose's injection site). */
    val site: String? = null,
)

data class TodayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val rows: List<DoseRow>,
    val asNeeded: List<Medication>,
    val asNeededTakenToday: Map<Long, Int>,
    val taken: Int,
    val total: Int,
    val lowStock: Int,
    val nextAppointment: Appointment?,
    val userName: String,
    val hasMedicines: Boolean,
    val nowMinute: Int,
    val snoozeMinutes: Int,
    /** Opened too long ago: time for a new one. */
    val replace: List<Medication> = emptyList(),
    val ramadan: RamadanSettings = RamadanSettings(),
    /** Ramadan is near and the mode is off: the expected first day, to suggest it. */
    val ramadanStart: LocalDate? = null,
    val simple: Boolean = false,
)

sealed class TodayEvent {
    data class LimitReached(val medication: Medication, val takenToday: Int, val limit: Int) : TodayEvent()
    data class AsNeededLogged(val name: String) : TodayEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    medicines: MedicineRepository,
    appointments: AppointmentRepository,
    db: MediMinderDatabase,
    settings: SettingsRepository,
    private val doseActions: DoseActions,
    private val ramadanManager: RamadanManager,
) : ViewModel() {

    init {
        // the day after Ramadan, the usual times come back even if the phone's daily check was missed
        viewModelScope.launch { ramadanManager.endIfOver() }
    }

    fun dismissRamadan(year: Int) = ramadanManager.dismissSuggestion(year)

    val selectedDate = MutableStateFlow(LocalDate.now())

    private val _events = MutableSharedFlow<TodayEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TodayEvent> = _events

    /** Re-evaluates "due" / "missed" as time passes. */
    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }

    private val medications = medicines.observeAll()
    /**
     * The chosen day together with *its* dose records. Keeping them paired means a newly chosen
     * day is never shown with the previous day's records (which would briefly mark the wrong
     * doses as taken while the new day loads).
     */
    private val dayLogs = selectedDate.flatMapLatest { d ->
        db.doseLogDao().observeRange(d.toEpochDay(), d.toEpochDay()).map { logs -> d to logs.filter { it.scheduledDay == d.toEpochDay() } }
    }
    private val extras = combine(
        appointments.observeAppointments(),
        db.activeAlertDao().observeAll(),
        settings.settings,
    ) { appts, alerts, prefs -> Extras(appts, alerts.map { it.scheduleId to it.scheduledDay }.toSet(), prefs.userName, prefs.snoozeMinutes, prefs.ramadan, prefs.simpleView) }

    private data class Extras(
        val appointments: List<Appointment>,
        val ringing: Set<Pair<Long, Long>>,
        val userName: String,
        val snoozeMinutes: Int,
        val ramadan: RamadanSettings,
        val simple: Boolean,
    )

    val state: StateFlow<TodayUi?> = combine(dayLogs, medications, clock, extras) { (date, logs), meds, now, extra ->
        val logByOccurrence = logs.filter { it.scheduleId != null }.associateBy { it.scheduleId!! }
        val schedulesOf = meds.associate { it.medication.id to it.schedules }
        val rows = DosePlanner.occurrencesOn(meds, date).map { occ ->
            val log = logByOccurrence[occ.schedule.id]
            DoseRow(
                scheduleId = occ.schedule.id,
                day = occ.day,
                minuteOfDay = occ.minuteOfDay,
                medicationId = occ.medication.id,
                name = occ.medication.name,
                strength = occ.medication.strength,
                quantity = occ.schedule.quantity,
                unit = occ.medication.unit,
                mealTiming = occ.medication.mealTiming,
                color = occ.medication.color,
                state = DosePlanner.stateOf(occ, log, now),
                loggedAt = log?.loggedAt,
                alertType = occ.schedule.alertType,
                ringing = (occ.schedule.id to occ.day) in extra.ringing,
                snoozedUntil = log?.takeIf { it.status == DoseStatus.SNOOZED }?.snoozedUntil,
                photo = occ.medication.photoUri,
                kind = occ.medication.kind,
                subtype = occ.medication.subtype,
                site = CareSites.siteFor(occ.medication, schedulesOf[occ.medication.id].orEmpty(), occ.schedule, occ.day),
            )
        }
        val todayEpoch = now.toLocalDate().toEpochDay()
        val asNeeded = meds.filter { it.medication.isAsNeeded && DosePlanner.isCourseActiveOn(it.medication, date) }.map { it.medication }
        val asNeededTaken = logs.filter { it.scheduleId == null && it.status == DoseStatus.TAKEN }.groupingBy { it.medicationId }.eachCount()
        val nowMillis = now.toEpochMillis()
        TodayUi(
            date = date,
            isToday = date == now.toLocalDate(),
            rows = rows,
            asNeeded = asNeeded,
            asNeededTakenToday = asNeededTaken,
            taken = rows.count { it.state == DoseState.TAKEN },
            total = rows.size,
            lowStock = meds.count { Stock.isLow(it.medication, it.schedules, todayEpoch) },
            nextAppointment = extra.appointments
                .filter { it.status == AppointmentStatus.UPCOMING && it.dateTime >= nowMillis - 60 * 60_000 }
                .minByOrNull { it.dateTime },
            userName = extra.userName,
            snoozeMinutes = extra.snoozeMinutes,
            simple = extra.simple,
            hasMedicines = meds.any { !it.medication.archived },
            nowMinute = minuteOf(now.toLocalTime()),
            replace = meds.map { it.medication }.filter { !it.archived && OpenedItems.needsReplacing(it, todayEpoch) },
            ramadan = extra.ramadan,
            ramadanStart = now.toLocalDate().let { today ->
                if (extra.ramadan.on || !Ramadan.isNear(today)) null
                else Ramadan.estimatedStart(today)?.takeIf { it.year != extra.ramadan.dismissedYear }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousDay() { selectedDate.value = selectedDate.value.minusDays(1) }
    fun nextDay() { selectedDate.value = selectedDate.value.plusDays(1) }
    fun goToToday() { selectedDate.value = LocalDate.now() }

    fun take(row: DoseRow) = viewModelScope.launch { doseActions.take(row.scheduleId, row.day) }
    fun skip(row: DoseRow) = viewModelScope.launch { doseActions.skip(row.scheduleId, row.day) }
    fun snooze(row: DoseRow) = viewModelScope.launch { doseActions.snooze(row.scheduleId, row.day) }
    fun undo(row: DoseRow) = viewModelScope.launch { doseActions.undo(row.scheduleId, row.day) }

    fun takeAsNeeded(medication: Medication, override: Boolean = false) = viewModelScope.launch {
        when (val result = doseActions.takeAsNeeded(medication.id, 1.0, override)) {
            is AsNeededResult.LimitReached -> _events.emit(TodayEvent.LimitReached(medication, result.takenToday, result.limit))
            AsNeededResult.Logged -> _events.emit(TodayEvent.AsNeededLogged(medication.name))
        }
    }
}
