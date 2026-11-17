package com.example.mediminder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Part of the day a dose belongs to, from the "সকাল + দুপুর + রাত" prescription pattern. */
@kotlinx.serialization.Serializable
enum class DoseSlot { MORNING, NOON, EVENING, NIGHT }

/** One daily dose time of a medicine. */
@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId"])]
)
@kotlinx.serialization.Serializable
data class Schedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    /** Minutes after midnight, 0..1439, in the phone's local time. */
    val minuteOfDay: Int,
    /** Weekdays the dose applies — see [DaysOfWeek]; 127 = every day. */
    val daysMask: Int = DaysOfWeek.EVERY_DAY,
    /** Units per dose: 0.5 for half a tablet, 5 for 5 ml… */
    val quantity: Double = 1.0,
    val alertType: AlertType = AlertType.REMINDER,
    val enabled: Boolean = true,
    /** Set when the time came from the meal-time pattern; such times follow meal-time changes. */
    val slot: DoseSlot? = null,
)

/** Weekday bit mask: bit 0 = Monday … bit 6 = Sunday (java.time DayOfWeek.value - 1). */
object DaysOfWeek {
    const val EVERY_DAY = 0b1111111

    fun bit(dayOfWeek: java.time.DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

    fun includes(mask: Int, dayOfWeek: java.time.DayOfWeek): Boolean = mask and bit(dayOfWeek) != 0

    fun count(mask: Int): Int = Integer.bitCount(mask and EVERY_DAY)
}
