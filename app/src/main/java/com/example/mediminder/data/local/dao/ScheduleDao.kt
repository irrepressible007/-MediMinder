package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mediminder.data.local.entity.Schedule
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId")
    fun getSchedulesForMedication(medicationId: Long): Flow<List<Schedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: Schedule): Long

    @Update
    suspend fun updateSchedule(schedule: Schedule): Int

    @Query("SELECT * FROM schedules WHERE isActive = 1")
    suspend fun getAllActiveSchedules(): List<Schedule>

    @Delete
    suspend fun deleteSchedule(schedule: Schedule): Int
}
