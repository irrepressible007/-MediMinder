package com.example.mediminder.data

import androidx.room.withTransaction
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import com.example.mediminder.data.local.entity.RestockEvent
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.MealSchedule
import com.example.mediminder.reminders.ReminderEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Medicines, their dose times and stock. Every change re-arms the medicine's alarms. */
@Singleton
class MedicineRepository @Inject constructor(
    private val db: MediMinderDatabase,
    private val engine: ReminderEngine,
    private val settings: SettingsRepository,
) {
    private val medicationDao = db.medicationDao()
    private val scheduleDao = db.scheduleDao()
    private val restockDao = db.restockDao()

    fun observeAll(): Flow<List<MedicationWithSchedules>> = medicationDao.observeAllWithSchedules()

    fun observe(id: Long): Flow<MedicationWithSchedules?> = medicationDao.observeWithSchedules(id)

    suspend fun get(id: Long): MedicationWithSchedules? = medicationDao.getWithSchedules(id)

    fun observeRestocks(id: Long): Flow<List<RestockEvent>> = restockDao.observeForMedication(id)

    /**
     * Inserts or updates a medicine with its dose times. Dose times keep their ids when they
     * still exist (so history stays attached); removed ones are deleted.
     */
    suspend fun save(medication: Medication, schedules: List<Schedule>): Long {
        var removed: List<Long> = emptyList()
        val id = db.withTransaction {
            val now = System.currentTimeMillis()
            if (medication.id == 0L) {
                val newId = medicationDao.insert(medication.copy(createdAt = now, updatedAt = now))
                scheduleDao.insertAll(schedules.map { it.copy(id = 0, medicationId = newId) })
                newId
            } else {
                medicationDao.update(medication.copy(updatedAt = now))
                val existing = scheduleDao.forMedication(medication.id)
                val keptIds = schedules.map { it.id }.filter { it != 0L }.toSet()
                removed = existing.map { it.id }.filter { it !in keptIds }
                if (removed.isNotEmpty()) scheduleDao.deleteByIds(removed)
                schedules.filter { it.id != 0L }.map { it.copy(medicationId = medication.id) }.let { if (it.isNotEmpty()) scheduleDao.updateAll(it) }
                schedules.filter { it.id == 0L }.map { it.copy(medicationId = medication.id) }.let { if (it.isNotEmpty()) scheduleDao.insertAll(it) }
                medication.id
            }
        }
        engine.rescheduleMedication(id, removed)
        return id
    }

    /** Stop (or resume) a medicine: history is kept, reminders stop. */
    suspend fun setArchived(id: Long, archived: Boolean) {
        val medication = medicationDao.getById(id) ?: return
        medicationDao.update(medication.copy(archived = archived, updatedAt = System.currentTimeMillis()))
        engine.rescheduleMedication(id)
    }

    /** Deletes a medicine and its whole history. */
    suspend fun delete(id: Long) {
        val item = medicationDao.getWithSchedules(id) ?: return
        medicationDao.delete(item.medication)
        engine.rescheduleMedication(id, item.schedules.map { it.id })
    }

    /** "I bought more": adds stock and records the purchase. */
    suspend fun restock(medicationId: Long, quantity: Double, pricePaid: Double?, pharmacy: String?, note: String? = null) {
        if (quantity <= 0) return
        db.withTransaction {
            val medication = medicationDao.getById(medicationId) ?: return@withTransaction
            medicationDao.update(
                medication.copy(
                    trackStock = true,
                    stock = medication.stock + quantity,
                    lowStockAlertDay = null,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            restockDao.insert(RestockEvent(medicationId = medicationId, quantity = quantity, pricePaid = pricePaid, pharmacy = pharmacy?.takeIf { it.isNotBlank() }, note = note))
        }
    }

    /** Sets the count directly (after counting the strip / bottle). */
    suspend fun setStock(medicationId: Long, stock: Double) {
        val medication = medicationDao.getById(medicationId) ?: return
        medicationDao.update(medication.copy(trackStock = true, stock = stock.coerceAtLeast(0.0), lowStockAlertDay = null, updatedAt = System.currentTimeMillis()))
    }

    /**
     * After the user changes their meal times, moves every dose time that came from a
     * meal-based pattern ("after lunch") to match. Hand-picked times are left alone.
     */
    suspend fun applyMealTimes(): Int {
        val s = settings.current
        var changed = 0
        val touched = mutableSetOf<Long>()
        db.withTransaction {
            for (item in medicationDao.getActiveWithSchedules()) {
                val updates = item.schedules.mapNotNull { schedule ->
                    val slot = schedule.slot ?: return@mapNotNull null
                    val minute = MealSchedule.minuteFor(slot, item.medication.mealTiming, s.mealTimes, s.beforeMealMinutes, s.afterMealMinutes)
                    if (minute == schedule.minuteOfDay) null else schedule.copy(minuteOfDay = minute)
                }
                if (updates.isNotEmpty()) {
                    scheduleDao.updateAll(updates)
                    changed += updates.size
                    touched += item.medication.id
                }
            }
        }
        touched.forEach { engine.rescheduleMedication(it) }
        return changed
    }
}
