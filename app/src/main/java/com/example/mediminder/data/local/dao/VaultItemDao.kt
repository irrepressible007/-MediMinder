package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mediminder.data.local.entity.VaultItem
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items ORDER BY dateAdded DESC")
    fun getAllVaultItems(): Flow<List<VaultItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vaultItem: VaultItem): Long
    
    @androidx.room.Delete
    suspend fun delete(vaultItem: VaultItem)
}
