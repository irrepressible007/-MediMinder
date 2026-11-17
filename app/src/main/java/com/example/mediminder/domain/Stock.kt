package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Stock maths: how long the medicine lasts and how much to buy. */
object Stock {
    /** Average units used per day on the current schedule. */
    fun dailyUse(medication: Medication, schedules: List<Schedule>): Double {
        if (medication.isAsNeeded) return 0.0
        return schedules.filter { it.enabled }.sumOf { it.quantity * DaysOfWeek.count(it.daysMask) / 7.0 }
    }

    /** Whole days the current stock lasts, or null when it cannot be known (as-needed / no schedule). */
    fun daysLeft(medication: Medication, schedules: List<Schedule>): Int? {
        if (!medication.trackStock) return null
        val perDay = dailyUse(medication, schedules)
        if (perDay <= 0.0) return null
        return floor(medication.stock / perDay + 1e-9).toInt()
    }

    /** Days of the course still to go (including today), or null if ongoing. */
    fun courseDaysRemaining(medication: Medication, today: Long): Int? {
        val end = medication.endDate ?: return null
        val start = max(today, medication.startDate ?: today)
        return max(0, (end - start + 1).toInt())
    }

    /**
     * Running low? For scheduled medicines: stock lasts [Medication.refillThresholdDays] days or
     * fewer — unless it already covers the rest of a fixed course. For as-needed medicines the
     * threshold is a number of units.
     */
    fun isLow(medication: Medication, schedules: List<Schedule>, today: Long): Boolean {
        if (!medication.trackStock || medication.archived) return false
        if (medication.endDate != null && today > medication.endDate) return false
        val perDay = dailyUse(medication, schedules)
        if (perDay <= 0.0) return medication.stock <= medication.refillThresholdDays
        courseDaysRemaining(medication, today)?.let { remaining ->
            if (medication.stock + 1e-9 >= perDay * remaining) return false
        }
        val left = daysLeft(medication, schedules) ?: return false
        return left <= medication.refillThresholdDays
    }

    /** Units to buy so the medicine lasts [days] more days (or to finish the course). Whole units. */
    fun suggestedPurchase(medication: Medication, schedules: List<Schedule>, days: Int, today: Long): Int {
        val perDay = dailyUse(medication, schedules)
        if (perDay <= 0.0) return if (medication.isAsNeeded) (medication.maxDailyDoses ?: 2) * 5 else 0
        val coverDays = courseDaysRemaining(medication, today)?.let { minOf(it, days) } ?: days
        val needed = perDay * coverDays - medication.stock
        return max(0, ceil(needed - 1e-9).toInt())
    }
}
