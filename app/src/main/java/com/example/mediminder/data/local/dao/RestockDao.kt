package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mediminder.data.local.entity.RestockEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface RestockDao {
    @Query("SELECT * FROM restocks WHERE medicationId = :medicationId ORDER BY date DESC")
    fun observeForMedication(medicationId: Long): Flow<List<RestockEvent>>

    /** Total spent on medicine since a point in time, in taka. */
    @Query("SELECT COALESCE(SUM(pricePaid), 0) FROM restocks WHERE date >= :since")
    fun observeSpentSince(since: Long): Flow<Double>

    @Query("SELECT * FROM restocks")
    suspend fun getAll(): List<RestockEvent>

    @Insert
    suspend fun insert(event: RestockEvent): Long

    @Insert
    suspend fun insertAll(events: List<RestockEvent>)

    @Query("DELETE FROM restocks")
    suspend fun deleteAll()
}
