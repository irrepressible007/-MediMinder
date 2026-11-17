package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.DoseStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE scheduledDay BETWEEN :fromDay AND :toDay")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs WHERE scheduledDay BETWEEN :fromDay AND :toDay")
    suspend fun getRange(fromDay: Long, toDay: Long): List<DoseLog>

    @Query("SELECT * FROM dose_logs WHERE scheduleId = :scheduleId AND scheduledDay = :day LIMIT 1")
    suspend fun forOccurrence(scheduleId: Long, day: Long): DoseLog?

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun getById(id: Long): DoseLog?

    /** Days in [fromDay, toDay] on which this dose was already taken or skipped. */
    @Query("SELECT scheduledDay FROM dose_logs WHERE scheduleId = :scheduleId AND scheduledDay BETWEEN :fromDay AND :toDay AND status IN ('TAKEN', 'SKIPPED')")
    suspend fun answeredDays(scheduleId: Long, fromDay: Long, toDay: Long): List<Long>

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId ORDER BY loggedAt DESC LIMIT :limit")
    fun observeForMedication(medicationId: Long, limit: Int = 60): Flow<List<DoseLog>>

    @Query("SELECT COUNT(*) FROM dose_logs WHERE medicationId = :medicationId AND scheduleId IS NULL AND scheduledDay = :day AND status = :status")
    suspend fun countAsNeeded(medicationId: Long, day: Long, status: DoseStatus = DoseStatus.TAKEN): Int

    @Query("SELECT COUNT(*) FROM dose_logs WHERE status = :status")
    fun observeCount(status: DoseStatus = DoseStatus.TAKEN): Flow<Int>

    @Query("SELECT * FROM dose_logs")
    suspend fun getAll(): List<DoseLog>

    @Insert
    suspend fun insert(log: DoseLog): Long

    @Insert
    suspend fun insertAll(logs: List<DoseLog>)

    @Update
    suspend fun update(log: DoseLog): Int

    @Query("DELETE FROM dose_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM dose_logs")
    suspend fun deleteAll()
}
