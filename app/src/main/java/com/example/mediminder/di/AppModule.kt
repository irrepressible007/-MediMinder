package com.example.mediminder.di

import android.content.Context
import androidx.room.Room
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.dao.MedicineDictionaryDao
import com.example.mediminder.data.local.dao.MedicationDao
import com.example.mediminder.data.local.dao.ScheduleDao
import com.example.mediminder.data.local.dao.LogDao
import com.example.mediminder.data.local.dao.VaultItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MediMinderDatabase {
        return Room.databaseBuilder(
            context,
            MediMinderDatabase::class.java,
            "mediminder.db"
        )
        .createFromAsset("database/mediminder_dataset.db")
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideMedicineDictionaryDao(db: MediMinderDatabase): MedicineDictionaryDao = db.medicineDictionaryDao()

    @Provides
    fun provideMedicationDao(db: MediMinderDatabase): MedicationDao = db.medicationDao()

    @Provides
    fun provideScheduleDao(db: MediMinderDatabase): ScheduleDao = db.scheduleDao()

    @Provides
    fun provideLogDao(db: MediMinderDatabase): LogDao = db.logDao()

    @Provides
    fun provideVaultItemDao(db: MediMinderDatabase): VaultItemDao = db.vaultItemDao()
}
