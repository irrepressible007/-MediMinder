package com.example.mediminder.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mediminder.data.local.dao.ActiveAlertDao
import com.example.mediminder.data.local.dao.AppointmentDao
import com.example.mediminder.data.local.dao.DoctorDao
import com.example.mediminder.data.local.dao.DoseLogDao
import com.example.mediminder.data.local.dao.MedicationDao
import com.example.mediminder.data.local.dao.RestockDao
import com.example.mediminder.data.local.dao.ScheduleDao
import com.example.mediminder.data.local.dao.VaultItemDao
import com.example.mediminder.data.local.entity.ActiveAlert
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.Doctor
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.RestockEvent
import com.example.mediminder.data.local.entity.Schedule
import com.example.mediminder.data.local.entity.VaultItem
import java.util.TimeZone

/**
 * The user's own data: medicines, dose times, history, stock, appointments, vault.
 *
 * Never use destructive migrations here — every schema change needs a [Migration] so
 * people keep their medicines when the app updates. (The medicine catalogue lives in a
 * separate, replaceable database: see CatalogueDatabase.)
 */
@Database(
    entities = [
        Medication::class, Schedule::class, DoseLog::class, RestockEvent::class,
        Doctor::class, Appointment::class, ActiveAlert::class, VaultItem::class,
    ],
    version = 4,
    exportSchema = true
)
abstract class MediMinderDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun restockDao(): RestockDao
    abstract fun doctorDao(): DoctorDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun activeAlertDao(): ActiveAlertDao
    abstract fun vaultItemDao(): VaultItemDao

    companion object {
        const val FILE_NAME = "mediminder.db"

        fun build(context: Context): MediMinderDatabase =
            Room.databaseBuilder(context, MediMinderDatabase::class.java, FILE_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}

/**
 * 1.x → 2.0. Versions 1.0.1–1.0.9 used schema 1, 1.0.10 used schema 2 (the same tables plus
 * two indexes), so both upgrade the same way. Existing medicines, dose times, stock and
 * taken-history are kept.
 */
/** Every upgrade path, oldest first. */
val ALL_MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_3, MIGRATION_2_3, MIGRATION_3_4)

/** 3 → 4: "delete but keep the history" marks a medicine instead of removing it. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medications ADD COLUMN deletedAt INTEGER")
    }
}

val MIGRATION_1_3 = object : Migration(1, 3) {
    override fun migrate(db: SupportSQLiteDatabase) = migrateFrom1x(db)
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) = migrateFrom1x(db)
}

/**
 * Converts the 1.x tables, or — if they are not what 1.x left behind (a very early build) —
 * starts with an empty 2.0 database. Losing a few test records beats a medicine app that
 * crashes on every launch.
 */
private fun migrateFrom1x(db: SupportSQLiteDatabase) {
    try {
        convert1xTables(db)
    } catch (e: Exception) {
        android.util.Log.e("MediMinderDatabase", "Could not convert 1.x data, starting fresh", e)
        resetToSchema3(db)
    }
}

private fun resetToSchema3(db: SupportSQLiteDatabase) {
    listOf(
        "medications_new", "schedules_tmp", "logs_tmp", "active_alerts", "appointments", "doctors",
        "restocks", "dose_logs", "logs", "schedules", "medications", "medicine_dictionary",
    ).forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
    db.execSQL(Schema3.MEDICATIONS)
    db.execSQL(Schema3.SCHEDULES)
    db.execSQL(Schema3.SCHEDULES_INDEX)
    db.execSQL(Schema3.DOSE_LOGS)
    Schema3.DOSE_LOGS_INDEXES.forEach(db::execSQL)
    db.execSQL(Schema3.RESTOCKS)
    db.execSQL(Schema3.RESTOCKS_INDEX)
    db.execSQL(Schema3.DOCTORS)
    db.execSQL(Schema3.APPOINTMENTS)
    Schema3.APPOINTMENTS_INDEXES.forEach(db::execSQL)
    db.execSQL(Schema3.ACTIVE_ALERTS)
    db.execSQL(Schema3.ACTIVE_ALERTS_INDEX)
    db.execSQL(Schema3.VAULT_ITEMS)
}

/**
 * The order is deliberate: children are copied into FK-free scratch tables before the old
 * parent table is dropped, so the copy is safe whether or not foreign keys are enforced
 * during the migration, and no rename ever has to rewrite another table's FK reference
 * (old SQLite versions on Android 7–10 cannot do that).
 */
private fun convert1xTables(db: SupportSQLiteDatabase) {
    val now = System.currentTimeMillis()
    val offsetMillis = TimeZone.getDefault().getOffset(now).toLong()

    // ── medications ──
    db.execSQL(Schema3.MEDICATIONS.replace("`medications`", "`medications_new`"))
    db.execSQL(
        """
        INSERT INTO medications_new (id, name, genericName, strength, form, manufacturer, catalogueId, shape, color,
            photoUri, mealTiming, dosePattern, notes, isAsNeeded, maxDailyDoses, trackStock, stock, unit,
            refillThresholdDays, refillReminders, lowStockAlertDay, startDate, endDate, archived, createdAt, updatedAt)
        SELECT id, name, NULL, dosage,
            CASE shape WHEN 'Capsule' THEN 'Capsule' WHEN 'Square' THEN 'Syrup' ELSE 'Tablet' END,
            NULL, NULL, shape, color, photoUri, 'ANY', NULL, NULL, isAsNeeded, maxDailyDoses,
            CASE WHEN inventoryCount > 0 THEN 1 ELSE 0 END, inventoryCount,
            CASE shape WHEN 'Capsule' THEN 'capsule' WHEN 'Square' THEN 'ml' ELSE 'tablet' END,
            5, 1, NULL, NULL, NULL, 0, $now, $now
        FROM medications
        """.trimIndent()
    )

    // ── scratch copies of the children (no foreign keys) ──
    db.execSQL("CREATE TABLE schedules_tmp (id INTEGER, medicationId INTEGER, minuteOfDay INTEGER, enabled INTEGER)")
    db.execSQL(
        "INSERT INTO schedules_tmp SELECT id, medicationId, (timeOfDayMillis / 60000) % 1440, isActive FROM schedules"
    )
    db.execSQL("CREATE TABLE logs_tmp (medicationId INTEGER, day INTEGER, loggedAt INTEGER, note TEXT)")
    // Only "taken" survives: a dose with no log already reads as missed in 2.0
    db.execSQL(
        "INSERT INTO logs_tmp SELECT medicationId, (timestamp + $offsetMillis) / 86400000, timestamp, symptoms " +
            "FROM logs WHERE status = 'TAKEN'"
    )

    // ── swap the parent ──
    db.execSQL("DROP TABLE IF EXISTS schedules")
    db.execSQL("DROP TABLE IF EXISTS logs")
    db.execSQL("DROP TABLE medications")
    db.execSQL("ALTER TABLE medications_new RENAME TO medications")

    // ── recreate the children against the final parent ──
    db.execSQL(Schema3.SCHEDULES)
    db.execSQL(Schema3.SCHEDULES_INDEX)
    db.execSQL(
        "INSERT INTO schedules (id, medicationId, minuteOfDay, daysMask, quantity, alertType, enabled, slot) " +
            "SELECT id, medicationId, minuteOfDay, 127, 1.0, 'REMINDER', enabled, NULL FROM schedules_tmp " +
            "WHERE medicationId IN (SELECT id FROM medications)"
    )
    db.execSQL(Schema3.DOSE_LOGS)
    Schema3.DOSE_LOGS_INDEXES.forEach(db::execSQL)
    db.execSQL(
        "INSERT INTO dose_logs (medicationId, scheduleId, scheduledDay, scheduledMinute, status, quantity, loggedAt, snoozedUntil, note) " +
            "SELECT medicationId, NULL, day, NULL, 'TAKEN', 1.0, loggedAt, NULL, note FROM logs_tmp " +
            "WHERE medicationId IN (SELECT id FROM medications)"
    )
    db.execSQL("DROP TABLE schedules_tmp")
    db.execSQL("DROP TABLE logs_tmp")

    // ── new in 2.0 ──
    db.execSQL(Schema3.RESTOCKS)
    db.execSQL(Schema3.RESTOCKS_INDEX)
    db.execSQL(Schema3.DOCTORS)
    db.execSQL(Schema3.APPOINTMENTS)
    Schema3.APPOINTMENTS_INDEXES.forEach(db::execSQL)
    db.execSQL(Schema3.ACTIVE_ALERTS)
    db.execSQL(Schema3.ACTIVE_ALERTS_INDEX)

    // The medicine list now lives in its own database (catalogue.db)
    db.execSQL("DROP TABLE IF EXISTS medicine_dictionary")
}

/** Version 3 table definitions, exactly as Room generates them (see app/schemas/…/3.json). */
internal object Schema3 {
    const val MEDICATIONS =
        "CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
            "`genericName` TEXT, `strength` TEXT NOT NULL, `form` TEXT NOT NULL, `manufacturer` TEXT, `catalogueId` INTEGER, " +
            "`shape` TEXT NOT NULL, `color` TEXT NOT NULL, `photoUri` TEXT, `mealTiming` TEXT NOT NULL, `dosePattern` TEXT, " +
            "`notes` TEXT, `isAsNeeded` INTEGER NOT NULL, `maxDailyDoses` INTEGER, `trackStock` INTEGER NOT NULL, " +
            "`stock` REAL NOT NULL, `unit` TEXT NOT NULL, `refillThresholdDays` INTEGER NOT NULL, `refillReminders` INTEGER NOT NULL, " +
            "`lowStockAlertDay` INTEGER, `startDate` INTEGER, `endDate` INTEGER, `archived` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"

    const val SCHEDULES =
        "CREATE TABLE IF NOT EXISTS `schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, " +
            "`minuteOfDay` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `quantity` REAL NOT NULL, `alertType` TEXT NOT NULL, " +
            "`enabled` INTEGER NOT NULL, `slot` TEXT, FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"
    const val SCHEDULES_INDEX =
        "CREATE INDEX IF NOT EXISTS `index_schedules_medicationId` ON `schedules` (`medicationId`)"

    const val DOSE_LOGS =
        "CREATE TABLE IF NOT EXISTS `dose_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, " +
            "`scheduleId` INTEGER, `scheduledDay` INTEGER NOT NULL, `scheduledMinute` INTEGER, `status` TEXT NOT NULL, " +
            "`quantity` REAL NOT NULL, `loggedAt` INTEGER NOT NULL, `snoozedUntil` INTEGER, `note` TEXT, " +
            "FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    val DOSE_LOGS_INDEXES = listOf(
        "CREATE INDEX IF NOT EXISTS `index_dose_logs_medicationId` ON `dose_logs` (`medicationId`)",
        "CREATE INDEX IF NOT EXISTS `index_dose_logs_scheduledDay` ON `dose_logs` (`scheduledDay`)",
        "CREATE INDEX IF NOT EXISTS `index_dose_logs_scheduleId_scheduledDay` ON `dose_logs` (`scheduleId`, `scheduledDay`)",
    )

    const val RESTOCKS =
        "CREATE TABLE IF NOT EXISTS `restocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, " +
            "`quantity` REAL NOT NULL, `pricePaid` REAL, `pharmacy` TEXT, `note` TEXT, `date` INTEGER NOT NULL, " +
            "FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    const val RESTOCKS_INDEX =
        "CREATE INDEX IF NOT EXISTS `index_restocks_medicationId` ON `restocks` (`medicationId`)"

    const val DOCTORS =
        "CREATE TABLE IF NOT EXISTS `doctors` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
            "`specialty` TEXT, `hospital` TEXT, `address` TEXT, `phone` TEXT, `visitingHours` TEXT, `notes` TEXT, " +
            "`createdAt` INTEGER NOT NULL)"

    const val APPOINTMENTS =
        "CREATE TABLE IF NOT EXISTS `appointments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `doctorId` INTEGER, " +
            "`doctorName` TEXT NOT NULL, `specialty` TEXT, `hospital` TEXT, `address` TEXT, `phone` TEXT, " +
            "`dateTime` INTEGER NOT NULL, `serialNumber` TEXT, `fee` REAL, `purpose` TEXT, `notes` TEXT, `status` TEXT NOT NULL, " +
            "`reminderMinutesBefore` INTEGER NOT NULL, `remindDayBefore` INTEGER NOT NULL, `reminderAlertType` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, FOREIGN KEY(`doctorId`) REFERENCES `doctors`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
    val APPOINTMENTS_INDEXES = listOf(
        "CREATE INDEX IF NOT EXISTS `index_appointments_doctorId` ON `appointments` (`doctorId`)",
        "CREATE INDEX IF NOT EXISTS `index_appointments_dateTime` ON `appointments` (`dateTime`)",
    )

    const val ACTIVE_ALERTS =
        "CREATE TABLE IF NOT EXISTS `active_alerts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scheduleId` INTEGER NOT NULL, " +
            "`medicationId` INTEGER NOT NULL, `scheduledDay` INTEGER NOT NULL, `scheduledMinute` INTEGER NOT NULL, " +
            "`alertType` TEXT NOT NULL, `firedAt` INTEGER NOT NULL, `repeatCount` INTEGER NOT NULL)"
    /** Unchanged since schema 1. */
    const val VAULT_ITEMS =
        "CREATE TABLE IF NOT EXISTS `vault_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
            "`dateAdded` INTEGER NOT NULL, `localImagePath` TEXT NOT NULL)"

    const val ACTIVE_ALERTS_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_active_alerts_scheduleId_scheduledDay` ON `active_alerts` (`scheduleId`, `scheduledDay`)"
}
