package com.example.mediminder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** When a medicine is taken relative to food — the "খাবারের আগে / পরে" on a Bangladeshi prescription. */
@kotlinx.serialization.Serializable
enum class MealTiming { ANY, BEFORE_MEAL, AFTER_MEAL, WITH_MEAL, EMPTY_STOMACH, BEDTIME }

/** How a dose is announced: a normal notification, or a full-screen alarm that rings until answered. */
@kotlinx.serialization.Serializable
enum class AlertType { REMINDER, ALARM }

@Entity(tableName = "medications")
@kotlinx.serialization.Serializable
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val genericName: String? = null,
    /** e.g. "500 mg", "120 mg/5 ml" */
    val strength: String = "",
    /** Dosage form as written in the catalogue: "Tablet", "Syrup", "Capsule (Delayed Release)"… */
    val form: String = "Tablet",
    val manufacturer: String? = null,
    /** Row in the bundled medicine catalogue this was picked from; null for a typed-in medicine. */
    val catalogueId: Long? = null,
    val shape: String = "Round",
    /** "#RRGGBB" */
    val color: String = "#10B981",
    val photoUri: String? = null,
    val mealTiming: MealTiming = MealTiming.ANY,
    /** Prescription pattern such as "1+0+1"; null when the times were set by hand. */
    val dosePattern: String? = null,
    val notes: String? = null,

    /** "As needed" (PRN): no fixed times, logged only when taken. */
    val isAsNeeded: Boolean = false,
    /** Safety limit per day for as-needed medicines. */
    val maxDailyDoses: Int? = null,

    /** Stock tracking ("how many are left?"). Off when the user never entered a count. */
    val trackStock: Boolean = false,
    val stock: Double = 0.0,
    /** tablet, capsule, ml, drop, puff, injection, sachet, unit */
    val unit: String = "tablet",
    /** Warn when this many days of medicine (or fewer) are left. */
    val refillThresholdDays: Int = 5,
    val refillReminders: Boolean = true,
    /** Epoch day of the last "running low" alert, so it is not repeated every hour. */
    val lowStockAlertDay: Long? = null,

    /** Course dates as epoch days (LocalDate.toEpochDay); endDate is inclusive, null = ongoing. */
    val startDate: Long? = null,
    val endDate: Long? = null,

    /** Stopped medicines are kept (with their history) but no longer remind. */
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
