package com.example.mediminder.data.backup

import com.example.mediminder.domain.CareKind
import com.example.mediminder.data.local.entity.Reading
import androidx.annotation.StringRes
import com.example.mediminder.R
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.mediminder.BuildConfig
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.Doctor
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.RestockEvent
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.settings.AppSettings
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.reminders.ReminderEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Everything needed to move to a new phone. (Vault photos stay on the old phone.) */
@Serializable
data class Backup(
    val format: Int = FORMAT,
    val appVersion: String = "",
    val exportedAt: Long = 0,
    val medications: List<Medication> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    val doseLogs: List<DoseLog> = emptyList(),
    val restocks: List<RestockEvent> = emptyList(),
    val doctors: List<Doctor> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val readings: List<Reading> = emptyList(),
    val settings: AppSettings? = null,
) {
    companion object {
        const val FORMAT = 1
    }
}

data class BackupCounts(val medicines: Int, val doses: Int, val appointments: Int)

/** A problem the person can act on; [reason] is the message to show them. */
class BackupException(@StringRes val reason: Int) : Exception()

/** Saves / restores all user data as a JSON file the user picks (Drive, WhatsApp, SD card…). */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MediMinderDatabase,
    private val settings: SettingsRepository,
    private val engine: ReminderEngine,
) {
    private val json = Json {
        ignoreUnknownKeys = true   // a backup from a newer app version still restores
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun create(): Backup = Backup(
        appVersion = BuildConfig.VERSION_NAME,
        exportedAt = System.currentTimeMillis(),
        medications = db.medicationDao().getAll(),
        schedules = db.scheduleDao().getAll(),
        doseLogs = db.doseLogDao().getAll(),
        restocks = db.restockDao().getAll(),
        doctors = db.doctorDao().getAll(),
        appointments = db.appointmentDao().getAll(),
        readings = db.readingDao().getAll(),
        settings = settings.current,
    )

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    fun decode(text: String): Backup {
        val backup = try {
            json.decodeFromString(Backup.serializer(), text)
        } catch (e: Exception) {
            throw BackupException(R.string.backup_not_ours)
        }
        if (backup.format > Backup.FORMAT) throw BackupException(R.string.backup_too_new)
        return backup
    }

    suspend fun exportTo(uri: Uri): BackupCounts = withContext(Dispatchers.IO) {
        val backup = create()
        val bytes = encode(backup).toByteArray()
        // "wt" (truncate) is right for replacing a file, but some storage apps only accept "w"
        val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: runCatching { context.contentResolver.openOutputStream(uri, "w") }.getOrNull()
            ?: throw BackupException(R.string.backup_cannot_write)
        out.use { it.write(bytes) }
        settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
        BackupCounts(backup.medications.size, backup.doseLogs.size, backup.appointments.size)
    }

    suspend fun importFrom(uri: Uri): BackupCounts = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw BackupException(R.string.backup_cannot_read)
        restore(decode(text))
    }

    /** Replaces all current data with the backup, then re-arms every reminder. */
    suspend fun restore(backup: Backup): BackupCounts {
        db.withTransaction {
            // children first
            db.activeAlertDao().deleteAll()
            db.doseLogDao().deleteAll()
            db.restockDao().deleteAll()
            db.scheduleDao().deleteAll()
            db.appointmentDao().deleteAll()
            db.doctorDao().deleteAll()
            db.medicationDao().deleteAll()
            db.readingDao().deleteAll()

            // Backups from before 1.0.8 have no kind: it follows the unit, as the upgrade did
            db.medicationDao().insertAll(backup.medications.map { m ->
                val fromUnit = CareKind.forUnit(m.unit)
                if (m.kind == CareKind.PILL && fromUnit != CareKind.PILL) m.copy(kind = fromUnit) else m
            })
            val medicationIds = backup.medications.map { it.id }.toSet()
            db.scheduleDao().insertAll(backup.schedules.filter { it.medicationId in medicationIds })
            db.doseLogDao().insertAll(backup.doseLogs.filter { it.medicationId in medicationIds })
            db.restockDao().insertAll(backup.restocks.filter { it.medicationId in medicationIds })
            db.doctorDao().insertAll(backup.doctors)
            val doctorIds = backup.doctors.map { it.id }.toSet()
            db.appointmentDao().insertAll(backup.appointments.map { if (it.doctorId != null && it.doctorId !in doctorIds) it.copy(doctorId = null) else it })
            db.readingDao().insertAll(backup.readings)
        }
        backup.settings?.let { restored ->
            // Keep this phone's language and the fact that setup is done
            settings.update { current -> restored.copy(onboardingDone = true, language = current.language, lastBackupAt = current.lastBackupAt) }
        }
        engine.rescheduleAll()
        return BackupCounts(backup.medications.size, backup.doseLogs.size, backup.appointments.size)
    }
}
