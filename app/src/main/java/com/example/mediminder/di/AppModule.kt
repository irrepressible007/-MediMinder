package com.example.mediminder.di

import android.content.Context
import androidx.room.Room
import com.example.mediminder.data.local.MediMinderDatabase
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
        .build()
    }

    @Provides
    fun provideMedicineDictionaryDao(db: MediMinderDatabase) = db.medicineDictionaryDao()

    @Provides
    fun provideMedicationDao(db: MediMinderDatabase) = db.medicationDao()

    @Provides
    fun provideScheduleDao(db: MediMinderDatabase) = db.scheduleDao()

    @Provides
    fun provideLogDao(db: MediMinderDatabase) = db.logDao()

    @Provides
    fun provideVaultItemDao(db: MediMinderDatabase) = db.vaultItemDao()
}
