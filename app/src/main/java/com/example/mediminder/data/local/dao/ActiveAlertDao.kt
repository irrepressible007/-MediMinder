package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mediminder.data.local.entity.ActiveAlert
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveAlertDao {
    @Query("SELECT * FROM active_alerts ORDER BY scheduledDay, scheduledMinute")
    fun observeAll(): Flow<List<ActiveAlert>>

    @Query("SELECT * FROM active_alerts ORDER BY scheduledDay, scheduledMinute")
    suspend fun getAll(): List<ActiveAlert>

    @Query("SELECT * FROM active_alerts WHERE scheduleId = :scheduleId AND scheduledDay = :day LIMIT 1")
    suspend fun get(scheduleId: Long, day: Long): ActiveAlert?

    @Insert
    suspend fun insert(alert: ActiveAlert): Long

    @Update
    suspend fun update(alert: ActiveAlert): Int

    @Query("DELETE FROM active_alerts WHERE scheduleId = :scheduleId AND scheduledDay = :day")
    suspend fun delete(scheduleId: Long, day: Long)

    @Query("DELETE FROM active_alerts WHERE medicationId = :medicationId")
    suspend fun deleteForMedication(medicationId: Long)

    /** Alerts for doses that are long past are no longer "ringing". */
    @Query("DELETE FROM active_alerts WHERE firedAt < :before")
    suspend fun deleteFiredBefore(before: Long)

    @Query("DELETE FROM active_alerts")
    suspend fun deleteAll()
}
