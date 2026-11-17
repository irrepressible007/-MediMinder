package com.example.mediminder.reminders

import com.example.mediminder.data.local.dao.ActiveAlertDao
import com.example.mediminder.data.local.dao.AppointmentDao
import com.example.mediminder.data.local.dao.DoseLogDao
import com.example.mediminder.data.local.dao.MedicationDao
import com.example.mediminder.data.local.dao.ScheduleDao
import com.example.mediminder.data.local.entity.ActiveAlert
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.AppointmentStatus
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.DosePlanner
import com.example.mediminder.domain.Stock
import com.example.mediminder.domain.timeOf
import com.example.mediminder.domain.toEpochMillis
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.util.AppLocale
import com.example.mediminder.util.Formats
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when every reminder and alarm goes off, and what happens when one does.
 *
 *  • Each dose time has exactly one pending "regular" alarm: the next occurrence. When it
 *    fires, the following one is armed straight away, so the chain never breaks even if the
 *    user ignores the alert.
 *  • An unanswered dose re-alerts every few minutes (settings), and Snooze replaces that.
 *  • Everything is re-armed after reboot, app update, time/time-zone change, permission
 *    change, and every few hours by a WorkManager safety net — re-arming is idempotent.
 */
@Singleton
class ReminderEngine @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val doseLogDao: DoseLogDao,
    private val activeAlertDao: ActiveAlertDao,
    private val appointmentDao: AppointmentDao,
    private val scheduler: ReminderScheduler,
    private val notifier: Notifier,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()

    // ── Arming ──────────────────────────────────────────────────────────────

    /** Re-arms every alarm the app owns. Safe to call any number of times. */
    suspend fun rescheduleAll(now: LocalDateTime = LocalDateTime.now()) = mutex.withLock {
        notifier.ensureChannels()
        activeAlertDao.deleteFiredBefore(now.minusHours(12).toEpochMillis())

        val activeIds = mutableSetOf<Long>()
        for (item in medicationDao.getActiveWithSchedules()) {
            for (schedule in item.schedules) {
                activeIds += schedule.id
                armDose(item.medication, schedule, now, catchUp = true)
            }
        }
        // Dose times of stopped / deleted medicines must not ring
        scheduleDao.getAll().filter { it.id !in activeIds }.forEach { scheduler.cancelDose(it.id) }

        for (appointment in appointmentDao.upcoming(now.minusDays(1).toEpochMillis())) armAppointment(appointment, now)
        armRefillCheck(now)
    }

    /** Re-arms one medicine after it was added, edited, archived or deleted. */
    suspend fun rescheduleMedication(medicationId: Long, removedScheduleIds: Collection<Long> = emptyList()) = mutex.withLock {
        removedScheduleIds.forEach { scheduler.cancelDose(it) }
        val item = medicationDao.getWithSchedules(medicationId)
        if (item == null || item.medication.archived) {
            item?.schedules?.forEach { scheduler.cancelDose(it.id) }
            activeAlertDao.deleteForMedication(medicationId)
            refreshAlarmLocked()
            return@withLock
        }
        val now = LocalDateTime.now()
        item.schedules.forEach { armDose(item.medication, it, now, catchUp = false) }
    }

    private suspend fun armDose(medication: Medication, schedule: Schedule, now: LocalDateTime, catchUp: Boolean) {
        if (catchUp && catchUpIfJustMissed(medication, schedule, now)) return
        val answered = answeredDaysAround(schedule.id, now)
        val next = DosePlanner.nextOccurrence(medication, schedule, now) { day -> day in answered }
        if (next == null) {
            scheduler.cancelDose(schedule.id)
        } else {
            scheduler.scheduleDose(schedule.id, next.toLocalDate().toEpochDay(), schedule.minuteOfDay, next.toEpochMillis(), schedule.alertType)
        }
    }

    /**
     * After the phone was off (or the app was force-stopped) a dose due in the last
     * [CATCH_UP_MINUTES] minutes still alerts — a few seconds from now.
     */
    private suspend fun catchUpIfJustMissed(medication: Medication, schedule: Schedule, now: LocalDateTime): Boolean {
        val today = now.toLocalDate()
        if (!DosePlanner.occursOn(medication, schedule, today)) return false
        val due = today.atTime(timeOf(schedule.minuteOfDay))
        if (due.isAfter(now) || due.isBefore(now.minusMinutes(CATCH_UP_MINUTES))) return false
        val day = today.toEpochDay()
        if (isAnswered(schedule.id, day) || activeAlertDao.get(schedule.id, day) != null) return false
        val log = doseLogDao.forOccurrence(schedule.id, day)
        if (log?.status == DoseStatus.SNOOZED) return false
        scheduler.scheduleDose(schedule.id, day, schedule.minuteOfDay, System.currentTimeMillis() + 3_000, schedule.alertType)
        return true
    }

    /** Answered days from [from] to the end of the planner's look-ahead window. */
    private suspend fun answeredDaysAround(scheduleId: Long, from: LocalDateTime): Set<Long> {
        val start = from.toLocalDate().toEpochDay()
        return doseLogDao.answeredDays(scheduleId, start, start + 10).toSet()
    }

    private suspend fun isAnswered(scheduleId: Long, day: Long): Boolean {
        val status = doseLogDao.forOccurrence(scheduleId, day)?.status
        return status == DoseStatus.TAKEN || status == DoseStatus.SKIPPED
    }

    // ── A dose alarm went off ───────────────────────────────────────────────

    suspend fun onDoseAlarm(scheduleId: Long, day: Long, kind: String) = mutex.withLock {
        val schedule = scheduleDao.getById(scheduleId)
        val medication = schedule?.let { medicationDao.getById(it.medicationId) }
        if (schedule == null || medication == null) {
            scheduler.cancelDose(scheduleId)
            return@withLock
        }
        val date = LocalDate.ofEpochDay(day)
        val due = date.atTime(timeOf(schedule.minuteOfDay))
        val now = LocalDateTime.now()

        // Keep the daily chain going before anything else
        if (kind == ReminderScheduler.KIND_REGULAR) {
            // Strictly after this dose (or after now, if the alarm itself was very late)
            val after = maxOf(due, now)
            val answered = answeredDaysAround(scheduleId, after)
            val next = DosePlanner.nextOccurrence(medication, schedule, after) { d -> d in answered }
            if (next != null) {
                scheduler.scheduleDose(scheduleId, next.toLocalDate().toEpochDay(), schedule.minuteOfDay, next.toEpochMillis(), schedule.alertType)
            } else {
                scheduler.cancelDose(scheduleId)
            }
        }

        if (!DosePlanner.occursOn(medication, schedule, date)) return@withLock
        // Too late to be useful (phone was off for hours): it simply shows as missed
        if (now.isAfter(due.plusMinutes(DosePlanner.MISSED_AFTER_MINUTES))) return@withLock

        val log = doseLogDao.forOccurrence(scheduleId, day)
        if (log?.status == DoseStatus.TAKEN || log?.status == DoseStatus.SKIPPED) return@withLock
        if (kind == ReminderScheduler.KIND_REGULAR && log?.status == DoseStatus.SNOOZED &&
            (log.snoozedUntil ?: 0) > System.currentTimeMillis() + 60_000
        ) return@withLock

        val existing = activeAlertDao.get(scheduleId, day)
        val repeatCount = when (kind) {
            ReminderScheduler.KIND_REPEAT -> (existing?.repeatCount ?: 0) + 1
            ReminderScheduler.KIND_SNOOZE -> 0
            else -> existing?.repeatCount ?: 0
        }
        val alert = ActiveAlert(
            id = existing?.id ?: 0,
            scheduleId = scheduleId,
            medicationId = medication.id,
            scheduledDay = day,
            scheduledMinute = schedule.minuteOfDay,
            alertType = schedule.alertType,
            firedAt = System.currentTimeMillis(),
            repeatCount = repeatCount,
        )
        if (existing == null) activeAlertDao.insert(alert) else activeAlertDao.update(alert)

        val s = settings.current
        if (schedule.alertType == AlertType.ALARM) {
            refreshAlarmLocked()
        } else {
            notifier.showDoseReminder(doseInfo(medication, schedule, day), s.snoozeMinutes, repeatCount)
        }

        if (s.repeatReminders && repeatCount < s.maxRepeats) {
            scheduler.scheduleDoseRepeat(
                scheduleId, day,
                System.currentTimeMillis() + s.repeatIntervalMinutes * 60_000L,
                schedule.alertType, ReminderScheduler.KIND_REPEAT,
            )
        }
    }

    // ── The user answered a dose ────────────────────────────────────────────

    /** Clears the alert for an answered dose and re-arms that dose time's next alarm. */
    suspend fun onDoseAnswered(scheduleId: Long, day: Long) = mutex.withLock {
        clearAlertLocked(scheduleId, day)
        val schedule = scheduleDao.getById(scheduleId) ?: return@withLock
        val medication = medicationDao.getById(schedule.medicationId) ?: return@withLock
        armDose(medication, schedule, LocalDateTime.now(), catchUp = false)
        refreshAlarmLocked()
    }

    /** Snoozed: stop alerting now and ring again at [until]. */
    suspend fun onDoseSnoozed(scheduleId: Long, day: Long, until: Long) = mutex.withLock {
        clearAlertLocked(scheduleId, day)
        val schedule = scheduleDao.getById(scheduleId) ?: return@withLock
        scheduler.scheduleDoseRepeat(scheduleId, day, until, schedule.alertType, ReminderScheduler.KIND_SNOOZE)
        refreshAlarmLocked()
    }

    /** A logged dose was undone: if it is still due, its alarm may need to come back. */
    suspend fun onDoseReopened(scheduleId: Long) = mutex.withLock {
        val schedule = scheduleDao.getById(scheduleId) ?: return@withLock
        val medication = medicationDao.getById(schedule.medicationId) ?: return@withLock
        armDose(medication, schedule, LocalDateTime.now(), catchUp = false)
    }

    private suspend fun clearAlertLocked(scheduleId: Long, day: Long) {
        activeAlertDao.delete(scheduleId, day)
        scheduler.cancelDoseRepeat(scheduleId, day)
        notifier.cancelDose(scheduleId, day)
    }

    /** Re-posts (or removes) the ringing alarm notification for every alarm-type dose still waiting. */
    private suspend fun refreshAlarmLocked() {
        val ringing = activeAlertDao.getAll().filter { it.alertType == AlertType.ALARM }
        val doses = ringing.mapNotNull { alert ->
            val schedule = scheduleDao.getById(alert.scheduleId) ?: return@mapNotNull null
            val medication = medicationDao.getById(schedule.medicationId) ?: return@mapNotNull null
            doseInfo(medication, schedule, alert.scheduledDay)
        }
        val s = settings.current
        if (doses.isEmpty()) notifier.cancelAlarm() else notifier.showAlarm(doses, s.snoozeMinutes, s.alarmRingMinutes)
    }

    suspend fun ringingAlerts(): List<ActiveAlert> = activeAlertDao.getAll()

    fun doseInfo(medication: Medication, schedule: Schedule, day: Long) = DoseInfo(
        scheduleId = schedule.id,
        day = day,
        minuteOfDay = schedule.minuteOfDay,
        medicationId = medication.id,
        name = medication.name,
        strength = medication.strength,
        quantity = schedule.quantity,
        unit = medication.unit,
        mealTiming = medication.mealTiming,
        alertType = schedule.alertType,
    )

    // ── Stock ───────────────────────────────────────────────────────────────

    /** Warns once a day when a medicine has just dropped to its refill threshold. */
    suspend fun checkLowStock(medicationId: Long) {
        val item = medicationDao.getWithSchedules(medicationId) ?: return
        val medication = item.medication
        val today = LocalDate.now().toEpochDay()
        if (!medication.refillReminders || medication.lowStockAlertDay == today) return
        if (!Stock.isLow(medication, item.schedules, today)) return
        val ctx = AppLocale.localized(appContext)
        notifier.showLowStock(
            medication.id, medication.name,
            Formats.quantity(ctx, medication.stock, medication.unit),
            Stock.daysLeft(medication, item.schedules),
        )
        medicationDao.setLowStockAlertDay(medication.id, today)
    }

    private fun armRefillCheck(now: LocalDateTime) {
        var at = now.toLocalDate().atTime(timeOf(settings.current.refillCheckMinute))
        if (!at.isAfter(now)) at = at.plusDays(1)
        scheduler.scheduleRefillCheck(at.toEpochMillis())
    }

    /** Daily check: one summary notification for every medicine running low (at most every 2 days each). */
    suspend fun onRefillCheck() = mutex.withLock {
        val today = LocalDate.now().toEpochDay()
        val low = medicationDao.getActiveWithSchedules().filter { item ->
            val m = item.medication
            m.refillReminders && Stock.isLow(m, item.schedules, today) &&
                (m.lowStockAlertDay == null || today - m.lowStockAlertDay >= 2)
        }
        if (low.isNotEmpty()) {
            notifier.showRefillSummary(low.map { it.medication.name })
            low.forEach { medicationDao.setLowStockAlertDay(it.medication.id, today) }
        }
        armRefillCheck(LocalDateTime.now())
    }

    // ── Appointments ────────────────────────────────────────────────────────

    suspend fun rescheduleAppointment(appointmentId: Long) = mutex.withLock {
        scheduler.cancelAppointment(appointmentId)
        appointmentDao.getById(appointmentId)?.let { armAppointment(it, LocalDateTime.now()) }
    }

    fun cancelAppointment(appointmentId: Long) {
        scheduler.cancelAppointment(appointmentId)
        notifier.cancelAppointment(appointmentId)
    }

    private fun armAppointment(appointment: Appointment, now: LocalDateTime) {
        if (appointment.status != AppointmentStatus.UPCOMING) {
            scheduler.cancelAppointment(appointment.id)
            return
        }
        val visit = appointment.dateTime.toLocalDateTime()
        val nowMillis = now.toEpochMillis()
        if (appointment.reminderMinutesBefore > 0) {
            val at = visit.minusMinutes(appointment.reminderMinutesBefore.toLong()).toEpochMillis()
            if (at > nowMillis) scheduler.scheduleAppointment(appointment.id, ReminderScheduler.WHICH_MAIN, at, appointment.reminderAlertType)
        }
        if (appointment.remindDayBefore) {
            val evening = visit.toLocalDate().minusDays(1).atTime(LocalTime.of(20, 0)).toEpochMillis()
            if (evening > nowMillis) scheduler.scheduleAppointment(appointment.id, ReminderScheduler.WHICH_EVENING, evening, AlertType.REMINDER)
        }
    }

    suspend fun onAppointmentAlarm(appointmentId: Long, which: String) {
        val appointment = appointmentDao.getById(appointmentId) ?: return
        if (appointment.status != AppointmentStatus.UPCOMING) return
        notifier.showAppointment(appointment, evening = which == ReminderScheduler.WHICH_EVENING)
    }

    // ── Test ────────────────────────────────────────────────────────────────

    fun scheduleTest(alertType: AlertType, delaySeconds: Int = 10) {
        notifier.ensureChannels()
        scheduler.scheduleTest(System.currentTimeMillis() + delaySeconds * 1000L, alertType)
    }

    fun onTest(alertType: AlertType) = notifier.showTest(alertType)

    companion object {
        /** A dose missed by at most this much while the phone was off still alerts on boot. */
        const val CATCH_UP_MINUTES = 30L
    }
}
