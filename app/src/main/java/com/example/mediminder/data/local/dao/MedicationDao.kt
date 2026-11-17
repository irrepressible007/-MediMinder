package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Transaction
    @Query("SELECT * FROM medications ORDER BY archived ASC, name COLLATE NOCASE ASC")
    fun observeAllWithSchedules(): Flow<List<MedicationWithSchedules>>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    fun observeWithSchedules(id: Long): Flow<MedicationWithSchedules?>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getWithSchedules(id: Long): MedicationWithSchedules?

    @Transaction
    @Query("SELECT * FROM medications WHERE archived = 0")
    suspend fun getActiveWithSchedules(): List<MedicationWithSchedules>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): Medication?

    @Query("SELECT * FROM medications")
    suspend fun getAll(): List<Medication>

    @Insert
    suspend fun insert(medication: Medication): Long

    @Insert
    suspend fun insertAll(medications: List<Medication>)

    @Update
    suspend fun update(medication: Medication): Int

    @Delete
    suspend fun delete(medication: Medication): Int

    @Query("DELETE FROM medications")
    suspend fun deleteAll()

    /** Adds (or with a negative delta, removes) stock; never goes below zero. */
    @Query("UPDATE medications SET stock = MAX(0, stock + :delta), updatedAt = :now WHERE id = :id")
    suspend fun adjustStock(id: Long, delta: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE medications SET lowStockAlertDay = :day WHERE id = :id")
    suspend fun setLowStockAlertDay(id: Long, day: Long?)
}
