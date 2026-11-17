package com.example.mediminder.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A dose reminder that has gone off and is waiting for an answer (Take / Snooze / Skip).
 *
 * The alarm screen and the "ringing" notification show every row here; a row is removed
 * as soon as the user answers that dose. Keeping it in the database (not in memory) means
 * the alarm screen still knows what is ringing after the process was killed.
 */
@Entity(
    tableName = "active_alerts",
    indices = [Index(value = ["scheduleId", "scheduledDay"], unique = true)]
)
data class ActiveAlert(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scheduleId: Long,
    val medicationId: Long,
    val scheduledDay: Long,
    val scheduledMinute: Int,
    val alertType: AlertType,
    val firedAt: Long = System.currentTimeMillis(),
    /** How many times this dose has re-alerted because it was not answered. */
    val repeatCount: Int = 0,
)
