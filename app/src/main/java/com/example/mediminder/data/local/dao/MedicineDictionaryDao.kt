package com.example.mediminder.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.mediminder.data.local.entity.MedicineDictionary
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDictionaryDao {
    @Query("SELECT * FROM medicine_dictionary WHERE brand_name LIKE :prefix || '%' ORDER BY brand_name ASC LIMIT 10")
    fun searchMedicine(prefix: String): Flow<List<MedicineDictionary>>
}
