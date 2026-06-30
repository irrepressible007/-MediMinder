package com.example.mediminder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String,
    val shape: String, // e.g., "Round", "Capsule", "Oblong"
    val color: String, // Hex string
    val photoUri: String?, // Local file path for the captured photo
    val inventoryCount: Int, // e.g., 30 pills left
    val isAsNeeded: Boolean, // PRN
    val maxDailyDoses: Int? // Safe limit for PRN
)
