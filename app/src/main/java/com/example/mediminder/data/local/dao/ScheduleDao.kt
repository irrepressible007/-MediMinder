package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mediminder.data.local.entity.Schedule

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Long): Schedule?

    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId ORDER BY minuteOfDay")
    suspend fun forMedication(medicationId: Long): List<Schedule>

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<Schedule>

    @Insert
    suspend fun insert(schedule: Schedule): Long

    @Insert
    suspend fun insertAll(schedules: List<Schedule>)

    @Update
    suspend fun update(schedule: Schedule): Int

    @Update
    suspend fun updateAll(schedules: List<Schedule>): Int

    @Query("DELETE FROM schedules WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()
}
