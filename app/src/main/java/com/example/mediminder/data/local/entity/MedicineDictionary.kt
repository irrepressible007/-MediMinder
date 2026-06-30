package com.example.mediminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicine_dictionary")
data class MedicineDictionary(
    @PrimaryKey val id: Int?,
    @ColumnInfo(name = "brand_id") val brandId: String?,
    @ColumnInfo(name = "brand_name") val brandName: String?,
    @ColumnInfo(name = "type") val type: String?,
    @ColumnInfo(name = "slug") val slug: String?,
    @ColumnInfo(name = "dosage_form") val dosageForm: String?,
    @ColumnInfo(name = "generic_name") val genericName: String?,
    @ColumnInfo(name = "strength") val strength: String?,
    @ColumnInfo(name = "manufacturer") val manufacturer: String?,
    @ColumnInfo(name = "package_container") val packageContainer: String?
)
