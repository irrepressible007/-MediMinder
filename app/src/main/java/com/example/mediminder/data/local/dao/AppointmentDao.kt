package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.AppointmentStatus
import com.example.mediminder.data.local.entity.Doctor
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY dateTime DESC")
    fun observeAll(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getById(id: Long): Appointment?

    @Query("SELECT * FROM appointments WHERE status = :status AND dateTime >= :after ORDER BY dateTime")
    suspend fun upcoming(after: Long, status: AppointmentStatus = AppointmentStatus.UPCOMING): List<Appointment>

    @Query("SELECT * FROM appointments")
    suspend fun getAll(): List<Appointment>

    @Insert
    suspend fun insert(appointment: Appointment): Long

    @Insert
    suspend fun insertAll(appointments: List<Appointment>)

    @Update
    suspend fun update(appointment: Appointment): Int

    @Delete
    suspend fun delete(appointment: Appointment): Int

    @Query("DELETE FROM appointments")
    suspend fun deleteAll()
}

@Dao
interface DoctorDao {
    @Query("SELECT * FROM doctors ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Doctor>>

    @Query("SELECT * FROM doctors WHERE id = :id")
    suspend fun getById(id: Long): Doctor?

    @Query("SELECT * FROM doctors")
    suspend fun getAll(): List<Doctor>

    @Insert
    suspend fun insert(doctor: Doctor): Long

    @Insert
    suspend fun insertAll(doctors: List<Doctor>)

    @Update
    suspend fun update(doctor: Doctor): Int

    @Delete
    suspend fun delete(doctor: Doctor): Int

    @Query("DELETE FROM doctors")
    suspend fun deleteAll()
}
