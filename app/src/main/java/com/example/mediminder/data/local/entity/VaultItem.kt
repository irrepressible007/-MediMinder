package com.example.mediminder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dateAdded: Long,
    val localImagePath: String
)
