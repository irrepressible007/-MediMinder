package com.example.mediminder.data

import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.reminders.DoseActions
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Health-check readings (blood pressure, sugar, weight…). */
@Singleton
class HealthRepository @Inject constructor(
    private val db: MediMinderDatabase,
    private val doseActions: DoseActions,
) {
    fun observeAll(): Flow<List<Reading>> = db.readingDao().observeAll()

    fun observe(type: String): Flow<List<Reading>> = db.readingDao().observe(type)

    fun observeSince(from: Long): Flow<List<Reading>> = db.readingDao().observeSince(from)

    /**
     * Saves a reading. When it answers a reminder to check ([scheduleId] and [day]), that
     * reminder is marked done, so it stops repeating.
     */
    suspend fun record(reading: Reading, scheduleId: Long? = null, day: Long? = null): Long {
        val id = db.readingDao().insert(reading)
        if (scheduleId != null && scheduleId != 0L && day != null) doseActions.take(scheduleId, day)
        return id
    }

    suspend fun delete(id: Long) = db.readingDao().delete(id)
}
