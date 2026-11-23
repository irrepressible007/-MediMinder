package com.example.mediminder.reminders

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.settings.SettingsRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed class AsNeededResult {
    data object Logged : AsNeededResult()
    /** The daily safety limit was reached; nothing was logged. */
    data class LimitReached(val takenToday: Int, val limit: Int) : AsNeededResult()
}

/**
 * What happens when the user answers a dose — from the app, a notification button or the
 * alarm screen. Logging and stock change in one transaction; stock is adjusted by the
 * difference, so changing your mind (or tapping twice) never double-counts.
 */
@Singleton
class DoseActions @Inject constructor(
    private val db: MediMinderDatabase,
    private val engine: ReminderEngine,
    private val settings: SettingsRepository,
) {
    /**
     * An answer, once started, always finishes. Callers often run it in a scope that ends
     * straight away: the alarm page closes itself the moment nothing is ringing, which used to
     * cancel the rest of the answer, so the alarm notification stayed after Skip.
     */
    private suspend fun <T> finishing(block: suspend () -> T): T = withContext(NonCancellable) { block() }

    private val medicationDao = db.medicationDao()
    private val scheduleDao = db.scheduleDao()
    private val doseLogDao = db.doseLogDao()

    suspend fun take(scheduleId: Long, day: Long, quantity: Double? = null) = finishing {
        val result = record(scheduleId, day, DoseStatus.TAKEN, quantity) ?: return@finishing
        engine.onDoseAnswered(scheduleId, day)
        if (result.usedStock) engine.checkLowStock(result.medicationId)
    }

    suspend fun skip(scheduleId: Long, day: Long) = finishing {
        record(scheduleId, day, DoseStatus.SKIPPED, null) ?: return@finishing
        engine.onDoseAnswered(scheduleId, day)
    }

    suspend fun snooze(scheduleId: Long, day: Long, minutes: Int = settings.current.snoozeMinutes) = finishing {
        val until = System.currentTimeMillis() + minutes * 60_000L
        record(scheduleId, day, DoseStatus.SNOOZED, null, snoozedUntil = until) ?: return@finishing
        engine.onDoseSnoozed(scheduleId, day, until)
    }

    /** Removes the answer for a scheduled dose (it becomes due/missed again). */
    suspend fun undo(scheduleId: Long, day: Long) = finishing {
        val log = doseLogDao.forOccurrence(scheduleId, day) ?: return@finishing
        removeLog(log)
        engine.onDoseReopened(scheduleId)
    }

    /** Removes any log (used for as-needed doses in the history). */
    suspend fun undoLog(logId: Long) = finishing {
        val log = doseLogDao.getById(logId) ?: return@finishing
        removeLog(log)
        log.scheduleId?.let { engine.onDoseReopened(it) }
    }

    private suspend fun removeLog(log: DoseLog) {
        db.withTransaction {
            doseLogDao.deleteById(log.id)
            if (log.status == DoseStatus.TAKEN) {
                val medication = medicationDao.getById(log.medicationId)
                if (medication?.trackStock == true) medicationDao.adjustStock(log.medicationId, log.quantity)
            }
        }
    }

    suspend fun takeAsNeeded(medicationId: Long, quantity: Double, override: Boolean = false, note: String? = null): AsNeededResult = finishing {
        val medication = medicationDao.getById(medicationId) ?: return@finishing AsNeededResult.Logged
        val today = LocalDate.now().toEpochDay()
        val limit = medication.maxDailyDoses
        if (limit != null && !override) {
            val taken = doseLogDao.countAsNeeded(medicationId, today)
            if (taken >= limit) return@finishing AsNeededResult.LimitReached(taken, limit)
        }
        db.withTransaction {
            doseLogDao.insert(
                DoseLog(medicationId = medicationId, scheduleId = null, scheduledDay = today, status = DoseStatus.TAKEN, quantity = quantity, note = note)
            )
            if (medication.trackStock) medicationDao.adjustStock(medicationId, -quantity)
        }
        engine.checkLowStock(medicationId)
        AsNeededResult.Logged
    }

    /** "Take all" on the alarm screen / notification. */
    suspend fun takeAllRinging() = finishing {
        engine.ringingAlerts().forEach { take(it.scheduleId, it.scheduledDay) }
    }

    suspend fun snoozeAllRinging(minutes: Int = settings.current.snoozeMinutes) = finishing {
        engine.ringingAlerts().forEach { snooze(it.scheduleId, it.scheduledDay, minutes) }
    }

    suspend fun skipAllRinging() = finishing {
        engine.ringingAlerts().forEach { skip(it.scheduleId, it.scheduledDay) }
    }

    private data class Recorded(val medicationId: Long, val usedStock: Boolean)

    /**
     * Writes the log for one scheduled dose and adjusts stock.
     * Returns null if the dose time no longer exists.
     */
    private suspend fun record(
        scheduleId: Long,
        day: Long,
        status: DoseStatus,
        quantity: Double?,
        snoozedUntil: Long? = null,
    ): Recorded? {
        val schedule = scheduleDao.getById(scheduleId) ?: return null
        val medication = medicationDao.getById(schedule.medicationId) ?: return null
        val amount = quantity ?: schedule.quantity
        var usedStock = false
        db.withTransaction {
            val existing = doseLogDao.forOccurrence(scheduleId, day)
            val log = DoseLog(
                id = existing?.id ?: 0,
                medicationId = medication.id,
                scheduleId = scheduleId,
                scheduledDay = day,
                scheduledMinute = schedule.minuteOfDay,
                status = status,
                quantity = amount,
                loggedAt = System.currentTimeMillis(),
                snoozedUntil = snoozedUntil,
                note = existing?.note,
            )
            if (existing == null) doseLogDao.insert(log) else doseLogDao.update(log)

            if (medication.trackStock) {
                val before = if (existing?.status == DoseStatus.TAKEN) existing.quantity else 0.0
                val after = if (status == DoseStatus.TAKEN) amount else 0.0
                if (after != before) {
                    medicationDao.adjustStock(medication.id, before - after)
                    usedStock = after > before
                }
            }
        }
        return Recorded(medication.id, usedStock)
    }
}
