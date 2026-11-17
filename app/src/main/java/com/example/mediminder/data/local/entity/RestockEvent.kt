package com.example.mediminder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** "I bought more": a stock top-up, with what it cost and where. */
@Entity(
    tableName = "restocks",
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
data class RestockEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val quantity: Double,
    /** Total paid, in taka. */
    val pricePaid: Double? = null,
    val pharmacy: String? = null,
    val note: String? = null,
    val date: Long = System.currentTimeMillis(),
)
