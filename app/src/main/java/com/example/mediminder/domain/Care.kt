package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.Schedule
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * What a care item is. Medicines come in forms that are used differently (a tablet is
 * swallowed, a cream applied, drops put in an eye…); health checks and habits are reminded
 * exactly like medicines but answered differently (a reading is recorded, a habit is done).
 */
@Serializable
enum class CareKind {
    PILL, LIQUID, TOPICAL, DROPS, INHALED, INJECTION, SUPPOSITORY, MEASURE, HABIT;

    val isMedicine: Boolean get() = this != MEASURE && this != HABIT

    companion object {
        /** From a medicine's unit: the unit is what the person confirmed when adding it. */
        fun forUnit(unit: String): CareKind = when (unit) {
            "ml", "spoon" -> LIQUID
            "drop" -> DROPS
            "puff" -> INHALED
            "injection", "unit" -> INJECTION
            "suppository" -> SUPPOSITORY
            "application" -> TOPICAL
            else -> PILL
        }

        /** Things that only last a while once opened (the label says how long). */
        fun lastsOnceOpened(kind: CareKind) = kind == TOPICAL || kind == DROPS || kind == INHALED || kind == LIQUID
    }
}

/** Health checks people are commonly asked to keep track of. */
enum class MeasureType { BLOOD_PRESSURE, BLOOD_SUGAR, WEIGHT, TEMPERATURE, OXYGEN }

/** Everyday habits a doctor often recommends. */
enum class HabitType { WATER, WALK, EXERCISE, FOOT_CHECK, SLEEP, OTHER }

/** Blood sugar is read differently before and after food. */
enum class SugarTiming { FASTING, AFTER_MEAL, RANDOM }

object CareSites {
    /** Where a cream or ointment goes. */
    val BODY = listOf("face", "scalp", "neck", "chest", "back", "belly", "arms", "hands", "legs", "knees", "feet", "skin")

    /** Drops: one side or both. */
    val SIDES = listOf("left", "right", "both")

    /** For injections: change the place every dose, in this order. */
    const val ROTATE = "rotate"
    val INJECTION = listOf("belly_left", "belly_right", "thigh_left", "thigh_right", "arm_left", "arm_right")

    /**
     * The place for one injection: doses go round the six sites in turn, across days.
     * [indexInDay] is this dose's position among the day's doses, [perDay] how many there are.
     */
    fun injectionSite(day: Long, indexInDay: Int, perDay: Int): String =
        INJECTION[Math.floorMod(day * perDay.coerceAtLeast(1) + indexInDay, INJECTION.size.toLong()).toInt()]

    /**
     * Where this dose goes: the medicine's own place, or for injections that move around,
     * the place in the rotation for this dose (counting that day's doses in time order).
     */
    fun siteFor(medication: Medication, schedules: List<Schedule>, schedule: Schedule, day: Long): String? {
        val site = medication.site ?: return null
        if (medication.kind != CareKind.INJECTION || site != ROTATE) return site
        val dow = LocalDate.ofEpochDay(day).dayOfWeek
        val todays = schedules.filter { it.enabled && DaysOfWeek.includes(it.daysMask, dow) }.sortedBy { it.minuteOfDay }
        val index = todays.indexOfFirst { it.id == schedule.id }.coerceAtLeast(0)
        return injectionSite(day, index, todays.size)
    }

    /** Drops: "eye_left", "ear_both", "nose_right"… */
    fun dropSite(organ: String, side: String) = "${organ}_$side"
    fun dropOrganOf(site: String?) = site?.substringBefore('_')?.takeIf { it in ORGANS } ?: "eye"
    fun dropSideOf(site: String?) = site?.substringAfter('_', "both")?.takeIf { it in SIDES } ?: "both"
    val ORGANS = listOf("eye", "ear", "nose")

    /** A cream can go on several places, kept as "knees,hands". */
    fun bodyParts(site: String?): List<String> = site.orEmpty().split(',').filter { it in BODY }
    fun bodySite(parts: Collection<String>): String? = BODY.filter { it in parts }.joinToString(",").ifEmpty { null }

    /** Drops go in an eye, an ear or the nose — read from the catalogue's dosage form. */
    fun dropOrgan(form: String): String {
        val f = form.lowercase()
        return when {
            ("ear" in f || "otic" in f) && "eye" !in f -> "ear"
            "nasal" in f || "nose" in f -> "nose"
            else -> "eye"
        }
    }
}

/** Things that only last a while once opened. */
object OpenedItems {
    /** The day it should be replaced (epoch day), or null when not tracked. */
    fun replaceOn(m: Medication): Long? {
        val opened = m.openedAt ?: return null
        val days = m.useWithinDays ?: return null
        return if (CareKind.lastsOnceOpened(m.kind)) opened + days else null
    }

    fun needsReplacing(m: Medication, today: Long): Boolean = replaceOn(m)?.let { today >= it } == true

    /** The daily check reminds on the day, then once a week until a new one is opened. */
    fun remindToday(m: Medication, today: Long): Boolean {
        val due = replaceOn(m) ?: return false
        return today >= due && (today - due) % 7 == 0L
    }

    /** Common "use within" choices printed on labels: eye drops are usually 4 weeks. */
    val CHOICES = listOf(28, 30, 60, 90, 180)
}

/** How a reading compares with the usual adult range. */
enum class ReadingLevel { VERY_LOW, LOW, A_BIT_LOW, NORMAL, A_BIT_HIGH, HIGH, VERY_HIGH, NONE }

/**
 * General adult ranges, used only to label readings in plain words. A doctor may set other
 * targets for a person; the app says so wherever these labels appear.
 * Blood sugar is in mmol/L (as used in Bangladesh), temperature in °F, oxygen in %.
 */
object Readings {
    fun level(type: MeasureType, value: Double, value2: Double? = null, timing: SugarTiming? = null): ReadingLevel = when (type) {
        MeasureType.BLOOD_PRESSURE -> {
            val dia = value2 ?: 0.0
            when {
                value >= 180 || dia >= 120 -> ReadingLevel.VERY_HIGH
                value >= 140 || dia >= 90 -> ReadingLevel.HIGH
                value < 90 || (value2 != null && dia < 60) -> ReadingLevel.LOW
                value >= 130 || dia >= 85 -> ReadingLevel.A_BIT_HIGH
                else -> ReadingLevel.NORMAL
            }
        }
        MeasureType.BLOOD_SUGAR -> when {
            value < 3.0 -> ReadingLevel.VERY_LOW
            value < 3.9 -> ReadingLevel.LOW
            value >= 16.7 -> ReadingLevel.VERY_HIGH
            timing == SugarTiming.FASTING -> when {
                value < 5.6 -> ReadingLevel.NORMAL
                value < 7.0 -> ReadingLevel.A_BIT_HIGH
                else -> ReadingLevel.HIGH
            }
            else -> when {
                value < 7.8 -> ReadingLevel.NORMAL
                value < 11.1 -> ReadingLevel.A_BIT_HIGH
                else -> ReadingLevel.HIGH
            }
        }
        MeasureType.TEMPERATURE -> when {
            value < 95.0 -> ReadingLevel.LOW
            value < 99.5 -> ReadingLevel.NORMAL
            value < 100.4 -> ReadingLevel.A_BIT_HIGH
            value < 103.0 -> ReadingLevel.HIGH
            else -> ReadingLevel.VERY_HIGH
        }
        MeasureType.OXYGEN -> when {
            value >= 95 -> ReadingLevel.NORMAL
            value >= 92 -> ReadingLevel.A_BIT_LOW
            value >= 88 -> ReadingLevel.LOW
            else -> ReadingLevel.VERY_LOW
        }
        MeasureType.WEIGHT -> ReadingLevel.NONE
    }

    /** Far enough from usual that the person should contact a doctor now. */
    fun urgent(level: ReadingLevel) = level == ReadingLevel.VERY_LOW || level == ReadingLevel.VERY_HIGH

    /** Numbers no thermometer, meter or scale would show: most likely a typing slip. */
    fun plausible(type: MeasureType, value: Double, value2: Double? = null, pulse: Int? = null): Boolean {
        if (pulse != null && pulse !in 25..250) return false
        return when (type) {
            MeasureType.BLOOD_PRESSURE -> value in 50.0..300.0 && value2 != null && value2 in 25.0..200.0 && value > value2
            MeasureType.BLOOD_SUGAR -> value in 1.0..40.0
            MeasureType.WEIGHT -> value in 2.0..350.0
            MeasureType.TEMPERATURE -> value in 86.0..113.0
            MeasureType.OXYGEN -> value in 50.0..100.0
        }
    }

    /** Meters in Bangladesh show mmol/L; some show mg/dL. */
    fun mgdlToMmol(mgdl: Double) = Math.round(mgdl / 18.0 * 10.0) / 10.0
    fun celsiusToF(c: Double) = Math.round((c * 9.0 / 5.0 + 32.0) * 10.0) / 10.0
}
