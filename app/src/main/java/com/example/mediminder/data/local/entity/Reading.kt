package com.example.mediminder.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One health-check reading (blood pressure, sugar, weight…). Kept even if the health check that
 * reminded about it is later deleted, so the doctor still sees the whole picture.
 */
@Entity(tableName = "readings", indices = [Index("takenAt"), Index("medicationId")])
@kotlinx.serialization.Serializable
data class Reading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** The health check that asked for it, or null for one recorded any time. */
    val medicationId: Long? = null,
    /** A MeasureType name. */
    val type: String,
    /** Blood pressure: the top number. Otherwise the reading itself. */
    val value: Double,
    /** Blood pressure: the bottom number. */
    val value2: Double? = null,
    val pulse: Int? = null,
    /** Blood sugar: a SugarTiming name. */
    val timing: String? = null,
    val takenAt: Long = System.currentTimeMillis(),
    val note: String? = null,
)
