package com.example.mediminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mediminder.data.local.entity.Log
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.local.dao.MedicationDao
import com.example.mediminder.data.local.dao.ScheduleDao
import com.example.mediminder.data.local.dao.LogDao

@Database(
    entities = [Medication::class, Schedule::class, Log::class],
    version = 1,
    exportSchema = false
)
abstract class MediMinderDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun logDao(): LogDao
}
