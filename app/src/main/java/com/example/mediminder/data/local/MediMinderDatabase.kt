package com.example.mediminder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.mediminder.data.local.entity.Log
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.MedicineDictionary
import com.example.mediminder.data.local.entity.VaultItem
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.local.dao.MedicationDao
import com.example.mediminder.data.local.dao.ScheduleDao
import com.example.mediminder.data.local.dao.LogDao
import com.example.mediminder.data.local.dao.MedicineDictionaryDao
import com.example.mediminder.data.local.dao.VaultItemDao

@Database(
    entities = [Medication::class, Schedule::class, Log::class, MedicineDictionary::class, VaultItem::class],
    version = 2,          // bumped: added indices to Schedule and Log
    exportSchema = false
)
abstract class MediMinderDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun logDao(): LogDao
    abstract fun medicineDictionaryDao(): MedicineDictionaryDao
    abstract fun vaultItemDao(): VaultItemDao

    companion object {
        @Volatile private var INSTANCE: MediMinderDatabase? = null

        /**
         * Returns a singleton database instance.
         * Used by BootReceiver which runs outside of Hilt's DI graph.
         */
        fun getInstance(context: Context): MediMinderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MediMinderDatabase::class.java,
                    "mediminder.db"
                )
                .createFromAsset("database/mediminder_dataset.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
