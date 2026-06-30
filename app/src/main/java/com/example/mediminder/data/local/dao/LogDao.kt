package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mediminder.data.local.entity.Log
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs WHERE medicationId = :medicationId ORDER BY timestamp DESC")
    fun getLogsForMedication(medicationId: Long): Flow<List<Log>>

    @Query("SELECT * FROM logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getLogsInTimeRange(startTime: Long, endTime: Long): Flow<List<Log>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: Log): Long

    @Update
    suspend fun updateLog(log: Log): Int

    @Delete
    suspend fun deleteLog(log: Log): Int
}
