package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mediminder.data.local.entity.VaultItem
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items ORDER BY dateAdded DESC")
    fun getAllVaultItems(): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items")
    suspend fun getAll(): List<VaultItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vaultItem: VaultItem): Long

    @Update
    suspend fun update(vaultItem: VaultItem): Int

    @Delete
    suspend fun delete(vaultItem: VaultItem)
}
