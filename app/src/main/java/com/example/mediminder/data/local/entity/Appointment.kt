package com.example.mediminder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A doctor the user visits, with their chamber details (for calling to book a serial). */
@Entity(tableName = "doctors")
@kotlinx.serialization.Serializable
data class Doctor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val specialty: String? = null,
    /** Hospital, clinic or chamber name */
    val hospital: String? = null,
    val address: String? = null,
    /** Number to call for a serial / appointment */
    val phone: String? = null,
    /** e.g. "Sat–Thu, 5 PM – 9 PM" */
    val visitingHours: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@kotlinx.serialization.Serializable
enum class AppointmentStatus { UPCOMING, DONE, CANCELLED }

@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = Doctor::class,
            parentColumns = ["id"],
            childColumns = ["doctorId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["doctorId"]), Index(value = ["dateTime"])]
)
@kotlinx.serialization.Serializable
data class Appointment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val doctorId: Long? = null,
    // Copied from the doctor so the appointment still reads correctly if the doctor is deleted
    val doctorName: String,
    val specialty: String? = null,
    val hospital: String? = null,
    val address: String? = null,
    val phone: String? = null,
    /** Epoch millis of the visit. */
    val dateTime: Long,
    /** Serial number given by the chamber (common practice in Bangladesh). */
    val serialNumber: String? = null,
    /** Consultation fee in taka. */
    val fee: Double? = null,
    val purpose: String? = null,
    val notes: String? = null,
    val status: AppointmentStatus = AppointmentStatus.UPCOMING,
    /** Minutes before the visit to remind; 0 = no reminder. */
    val reminderMinutesBefore: Int = 120,
    /** Also remind the evening before (8 PM). */
    val remindDayBefore: Boolean = true,
    val reminderAlertType: AlertType = AlertType.REMINDER,
    val createdAt: Long = System.currentTimeMillis(),
)
