package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.DoseSlot
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.settings.MealTimes

/**
 * A Bangladeshi prescription dose pattern: "1+0+1" = morning + noon + night,
 * "1+1+1+1" adds an evening dose, "½+0+½" = half a tablet twice a day.
 */
data class DosePattern(val doses: List<Pair<DoseSlot, Double>>) {

    /** Normalised text, e.g. "1+0+1" or "½+0+½". */
    val text: String
        get() {
            val bySlot = doses.toMap()
            val slots = if (DoseSlot.EVENING in bySlot) FOUR_SLOTS else THREE_SLOTS
            return slots.joinToString("+") { formatQuantity(bySlot[it] ?: 0.0) }
        }

    val dosesPerDay: Int get() = doses.count { it.second > 0 }

    companion object {
        private val THREE_SLOTS = listOf(DoseSlot.MORNING, DoseSlot.NOON, DoseSlot.NIGHT)
        private val FOUR_SLOTS = listOf(DoseSlot.MORNING, DoseSlot.NOON, DoseSlot.EVENING, DoseSlot.NIGHT)

        /** The patterns doctors write most often, offered as one-tap chips. */
        val PRESETS = listOf("1+0+0", "0+0+1", "1+0+1", "1+1+1", "0+1+0", "1+1+1+1", "½+0+½", "2+0+2")

        private val BANGLA_DIGITS = mapOf('০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4', '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9')

        /** Parses "1+0+1", "১+০+১", "1-0-1", "½+0+½", "0.5+0+0.5"; null if it is not a pattern. */
        fun parse(input: String): DosePattern? {
            val normalised = input.trim().map { BANGLA_DIGITS[it] ?: it }.joinToString("")
            val parts = normalised.split('+', '-', '–').map { it.trim() }
            if (parts.size !in 3..4 || parts.any { it.isEmpty() }) return null
            val quantities = parts.map { parseQuantity(it) ?: return null }
            if (quantities.all { it == 0.0 } || quantities.any { it < 0 || it > 20 }) return null
            val slots = if (parts.size == 4) FOUR_SLOTS else THREE_SLOTS
            return DosePattern(slots.zip(quantities).filter { it.second > 0 })
        }

        private fun parseQuantity(text: String): Double? = when (text) {
            "½", "1/2" -> 0.5
            "¼", "1/4" -> 0.25
            "¾", "3/4" -> 0.75
            "1½", "1 1/2" -> 1.5
            else -> text.toDoubleOrNull()
        }

        fun formatQuantity(quantity: Double): String = when (quantity) {
            0.5 -> "½"
            0.25 -> "¼"
            0.75 -> "¾"
            1.5 -> "1½"
            else -> if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()
        }
    }
}

/** A dose time worked out from the pattern and the user's meal times. */
data class PlannedDose(val slot: DoseSlot, val minuteOfDay: Int, val quantity: Double)

object MealSchedule {
    /** The meal a slot is anchored to. */
    fun mealMinute(slot: DoseSlot, meals: MealTimes): Int = when (slot) {
        DoseSlot.MORNING -> meals.breakfast
        DoseSlot.NOON -> meals.lunch
        DoseSlot.EVENING -> meals.evening
        DoseSlot.NIGHT -> meals.dinner
    }

    /** Dose time for a slot: e.g. NIGHT + BEFORE_MEAL = 30 minutes before dinner. */
    fun minuteFor(slot: DoseSlot, timing: MealTiming, meals: MealTimes, beforeMinutes: Int, afterMinutes: Int): Int {
        val meal = mealMinute(slot, meals)
        val minute = when (timing) {
            MealTiming.BEFORE_MEAL -> meal - beforeMinutes
            MealTiming.AFTER_MEAL -> meal + afterMinutes
            MealTiming.WITH_MEAL, MealTiming.ANY -> meal
            MealTiming.EMPTY_STOMACH -> meal - 60
            MealTiming.BEDTIME -> if (slot == DoseSlot.NIGHT) meals.bedtime else meal + afterMinutes
        }
        return Math.floorMod(minute, 24 * 60)
    }

    fun plan(pattern: DosePattern, timing: MealTiming, meals: MealTimes, beforeMinutes: Int, afterMinutes: Int): List<PlannedDose> =
        pattern.doses.map { (slot, quantity) ->
            PlannedDose(slot, minuteFor(slot, timing, meals, beforeMinutes, afterMinutes), quantity)
        }.sortedBy { it.minuteOfDay }
}
