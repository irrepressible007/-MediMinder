package com.example.mediminder.data.local.dao

import com.example.mediminder.data.local.entity.Medication
import androidx.room.Embedded
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

    /**
     * Intake history, newest first: every dose taken or skipped since [fromDay], with its
     * medicine — including medicines deleted with their history kept.
     */
    @Query(
        "SELECT dose_logs.*, medications.name AS name, medications.strength AS strength, medications.unit AS unit, " +
            "medications.color AS color, medications.photoUri AS photoUri, medications.deletedAt AS deletedAt " +
            "FROM dose_logs JOIN medications ON medications.id = dose_logs.medicationId " +
            "WHERE dose_logs.scheduledDay >= :fromDay AND dose_logs.status IN ('TAKEN', 'SKIPPED') " +
            "AND (:medicationId IS NULL OR dose_logs.medicationId = :medicationId) " +
            "ORDER BY dose_logs.scheduledDay DESC, COALESCE(dose_logs.scheduledMinute, 1440) DESC, dose_logs.loggedAt DESC"
    )
    fun observeHistory(fromDay: Long, medicationId: Long?): Flow<List<HistoryEntry>>

    /** Medicines that have any history, for the history filter. */
    @Query(
        "SELECT * FROM medications WHERE id IN (SELECT DISTINCT medicationId FROM dose_logs WHERE status IN ('TAKEN', 'SKIPPED')) " +
            "ORDER BY deletedAt IS NOT NULL, name COLLATE NOCASE"
    )
    fun observeMedicinesWithHistory(): Flow<List<Medication>>

    @Query("SELECT COUNT(*) FROM dose_logs WHERE medicationId = :medicationId")
    suspend fun countForMedication(medicationId: Long): Int

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

/** One dose from the history, with the medicine it belongs to. */
data class HistoryEntry(
    @Embedded val log: DoseLog,
    val name: String,
    val strength: String,
    val unit: String,
    val color: String,
    val photoUri: String?,
    val deletedAt: Long?,
)
