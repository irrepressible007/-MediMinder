package com.example.mediminder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@kotlinx.serialization.Serializable
enum class DoseStatus { TAKEN, SKIPPED, SNOOZED }

/**
 * What the user did about a dose.
 *
 * A scheduled dose has at most one log per (scheduleId, scheduledDay), which is
 * updated as the user changes their mind (snoozed → taken). As-needed doses have no
 * schedule and get one log per intake. A missing log for a past dose means "missed".
 */
@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["medicationId"]),
        Index(value = ["scheduledDay"]),
        // Not UNIQUE: SQLite before 3.9 (older Android phones) returns a single row for
        // `scheduleId IS NULL` on a unique index, which would undercount as-needed doses.
        // DoseActions keeps one log per scheduled dose instead.
        Index(value = ["scheduleId", "scheduledDay"]),
    ]
)
@kotlinx.serialization.Serializable
data class DoseLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    /** Null for as-needed doses. */
    val scheduleId: Long? = null,
    /** Epoch day the dose was due (or taken, for as-needed). */
    val scheduledDay: Long,
    val scheduledMinute: Int? = null,
    val status: DoseStatus,
    val quantity: Double = 1.0,
    val loggedAt: Long = System.currentTimeMillis(),
    /** For SNOOZED: when the reminder rings again. */
    val snoozedUntil: Long? = null,
    /** Optional symptom / diary note. */
    val note: String? = null,
)
