package com.example.mediminder.data.catalogue

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * One medicine product sold in Bangladesh (brand + strength + form), from the bundled
 * catalogue built by tools/build_catalogue.py. Read-only reference data.
 *
 * The layout must match CREATE_TABLE in tools/build_catalogue.py exactly.
 */
@Entity(
    tableName = "medicines",
    indices = [Index(value = ["brand_search"]), Index(value = ["generic_search"])]
)
data class CatalogueMedicine(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "brand_name") val brandName: String,
    @ColumnInfo(name = "generic_name") val genericName: String?,
    val strength: String?,
    @ColumnInfo(name = "dosage_form") val dosageForm: String?,
    val manufacturer: String?,
    /** allopathic, ayurvedic, unani, herbal, homeopathic */
    val category: String,
    /** Price of one unit (tablet/capsule…) in taka, when known. */
    @ColumnInfo(name = "unit_price") val unitPrice: Double?,
    /** Pack prices, e.g. "100 ml bottle: ৳ 40.12" or "30's pack: ৳ 270.00". */
    @ColumnInfo(name = "pack_info") val packInfo: String?,
    @ColumnInfo(name = "brand_search") val brandSearch: String,
    @ColumnInfo(name = "generic_search") val genericSearch: String,
)

@Dao
interface CatalogueDao {
    /** Brands whose search key starts with [key]; [keyEnd] is key + '{' (the character after 'z'). */
    @Query("SELECT * FROM medicines WHERE brand_search >= :key AND brand_search < :keyEnd ORDER BY length(brand_search), brand_search, strength LIMIT :limit")
    suspend fun brandPrefix(key: String, keyEnd: String, limit: Int): List<CatalogueMedicine>

    @Query("SELECT * FROM medicines WHERE generic_search >= :key AND generic_search < :keyEnd ORDER BY brand_search, strength LIMIT :limit")
    suspend fun genericPrefix(key: String, keyEnd: String, limit: Int): List<CatalogueMedicine>

    /** Slower full scan, used only when the prefix searches found too little. */
    @Query("SELECT * FROM medicines WHERE brand_search LIKE '%' || :key || '%' OR generic_search LIKE '%' || :key || '%' ORDER BY length(brand_search), brand_search LIMIT :limit")
    suspend fun contains(key: String, limit: Int): List<CatalogueMedicine>

    @Query("SELECT * FROM medicines WHERE brand_search IN (:keys) LIMIT :limit")
    suspend fun byBrandKeys(keys: List<String>, limit: Int): List<CatalogueMedicine>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun byId(id: Long): CatalogueMedicine?

    /** Other brands with the same generic, cheapest (known price) first. */
    @Query("SELECT * FROM medicines WHERE generic_search = :genericKey AND id != :excludeId ORDER BY unit_price IS NULL, unit_price, brand_search LIMIT :limit")
    suspend fun sameGeneric(genericKey: String, excludeId: Long, limit: Int): List<CatalogueMedicine>

    @Query("SELECT COUNT(*) FROM medicines")
    suspend fun count(): Int
}

@Database(entities = [CatalogueMedicine::class], version = CatalogueDatabase.VERSION, exportSchema = true)
abstract class CatalogueDatabase : RoomDatabase() {
    abstract fun catalogueDao(): CatalogueDao

    companion object {
        /** Bump together with CATALOGUE_VERSION in tools/build_catalogue.py when the data changes. */
        const val VERSION = 1
        const val ASSET = "database/catalogue.db"
        const val FILE_NAME = "catalogue.db"

        fun build(context: Context): CatalogueDatabase =
            Room.databaseBuilder(context, CatalogueDatabase::class.java, FILE_NAME)
                .createFromAsset(ASSET)
                // Reference data only: a newer catalogue simply replaces the old copy
                .fallbackToDestructiveMigration()
                .build()
    }
}

/** Text normalisation shared with tools/build_catalogue.py. */
object CatalogueText {
    /** Lower-case letters and digits only: "Napa Extra 500" -> "napaextra500". */
    fun searchKey(text: String?): String =
        text.orEmpty().lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    /** Comparable strength: "500 mg" == "500mg" == "500 MG". */
    fun strengthKey(text: String?): String =
        text.orEmpty().lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '%' || it == '/' || it == '+' }

    /** Active ingredients of a generic name: "Paracetamol + Caffeine" -> [paracetamol, caffeine]. */
    fun ingredients(generic: String?): Set<String> =
        generic.orEmpty()
            .split('+', ',', '&', '/')
            .map { part -> searchKey(part.substringBefore('(')) }
            .filter { it.length >= 4 }
            .toSet()
}
