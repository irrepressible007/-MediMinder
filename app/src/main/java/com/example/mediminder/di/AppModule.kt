package com.example.mediminder.di

import android.content.Context
import com.example.mediminder.data.catalogue.CatalogueDao
import com.example.mediminder.data.catalogue.CatalogueDatabase
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.dao.ActiveAlertDao
import com.example.mediminder.data.local.dao.AppointmentDao
import com.example.mediminder.data.local.dao.DoseLogDao
import com.example.mediminder.data.local.dao.MedicationDao
import com.example.mediminder.data.local.dao.ScheduleDao
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
    fun provideDatabase(@ApplicationContext context: Context): MediMinderDatabase = MediMinderDatabase.build(context)

    @Provides
    @Singleton
    fun provideCatalogueDatabase(@ApplicationContext context: Context): CatalogueDatabase = CatalogueDatabase.build(context)

    @Provides
    fun provideCatalogueDao(db: CatalogueDatabase): CatalogueDao = db.catalogueDao()

    @Provides fun provideMedicationDao(db: MediMinderDatabase): MedicationDao = db.medicationDao()
    @Provides fun provideScheduleDao(db: MediMinderDatabase): ScheduleDao = db.scheduleDao()
    @Provides fun provideDoseLogDao(db: MediMinderDatabase): DoseLogDao = db.doseLogDao()
    @Provides fun provideActiveAlertDao(db: MediMinderDatabase): ActiveAlertDao = db.activeAlertDao()
    @Provides fun provideAppointmentDao(db: MediMinderDatabase): AppointmentDao = db.appointmentDao()
    @Provides fun provideVaultItemDao(db: MediMinderDatabase): VaultItemDao = db.vaultItemDao()
}
