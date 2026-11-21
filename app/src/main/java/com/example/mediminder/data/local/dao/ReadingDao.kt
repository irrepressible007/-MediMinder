package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mediminder.data.local.entity.Reading
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert
    suspend fun insert(reading: Reading): Long

    @Insert
    suspend fun insertAll(readings: List<Reading>)

    @Query("SELECT * FROM readings WHERE type = :type ORDER BY takenAt DESC")
    fun observe(type: String): Flow<List<Reading>>

    @Query("SELECT * FROM readings ORDER BY takenAt DESC")
    fun observeAll(): Flow<List<Reading>>

    @Query("SELECT * FROM readings WHERE takenAt >= :from ORDER BY takenAt DESC")
    fun observeSince(from: Long): Flow<List<Reading>>

    @Query("SELECT * FROM readings ORDER BY takenAt DESC")
    suspend fun getAll(): List<Reading>

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM readings")
    suspend fun deleteAll()
}
