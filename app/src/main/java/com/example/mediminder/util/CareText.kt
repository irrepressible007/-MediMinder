package com.example.mediminder.util

import android.content.Context
import com.example.mediminder.R
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.CareSites
import com.example.mediminder.domain.HabitType
import com.example.mediminder.domain.MeasureType
import com.example.mediminder.domain.ReadingLevel
import java.util.Locale

/**
 * How each kind of care item is put into words. A tablet is "taken", a cream "applied", drops
 * "put in" the left eye; a health check is "written down" and a habit "done". In Bangla the
 * verbs differ even more (খাওয়া, লাগানো, দেওয়া, নেওয়া), so every reminder, button and
 * label goes through here.
 */
object CareText {
    fun isOral(kind: CareKind) = kind == CareKind.PILL || kind == CareKind.LIQUID

    fun measureOf(subtype: String?): MeasureType? = MeasureType.entries.firstOrNull { it.name == subtype }
    fun habitOf(subtype: String?): HabitType? = HabitType.entries.firstOrNull { it.name == subtype }

    /** The name shown everywhere: a health check or a habit is named in the app's language. */
    fun name(ctx: Context, kind: CareKind, subtype: String?, name: String): String = when (kind) {
        CareKind.MEASURE -> measureOf(subtype)?.let { measureName(ctx, it) } ?: name
        CareKind.HABIT -> habitOf(subtype)?.takeIf { it != HabitType.OTHER }?.let { habitName(ctx, it) } ?: name
        else -> name
    }

    /** "Time for Napa 500 mg", "Time to apply Betnovate", "Time to check your blood pressure". */
    fun title(ctx: Context, kind: CareKind, subtype: String?, displayName: String): String = when (kind) {
        CareKind.PILL, CareKind.LIQUID -> ctx.getString(R.string.notif_dose_title, displayName)
        CareKind.TOPICAL -> ctx.getString(R.string.care_title_apply, displayName)
        CareKind.DROPS, CareKind.SUPPOSITORY -> ctx.getString(R.string.care_title_put, displayName)
        CareKind.INHALED, CareKind.INJECTION -> ctx.getString(R.string.care_title_use, displayName)
        CareKind.MEASURE -> when (measureOf(subtype)) {
            MeasureType.BLOOD_PRESSURE -> ctx.getString(R.string.measure_title_bp)
            MeasureType.BLOOD_SUGAR -> ctx.getString(R.string.measure_title_sugar)
            MeasureType.WEIGHT -> ctx.getString(R.string.measure_title_weight)
            MeasureType.TEMPERATURE -> ctx.getString(R.string.measure_title_temp)
            MeasureType.OXYGEN -> ctx.getString(R.string.measure_title_oxygen)
            null -> ctx.getString(R.string.notif_dose_title, displayName)
        }
        CareKind.HABIT -> when (habitOf(subtype)) {
            HabitType.WATER -> ctx.getString(R.string.habit_title_water)
            HabitType.WALK -> ctx.getString(R.string.habit_title_walk)
            HabitType.EXERCISE -> ctx.getString(R.string.habit_title_exercise)
            HabitType.FOOT_CHECK -> ctx.getString(R.string.habit_title_foot)
            HabitType.SLEEP -> ctx.getString(R.string.habit_title_sleep)
            else -> ctx.getString(R.string.habit_title_other, displayName)
        }
    }

    /** What to do: "Take 1 tablet", "Apply to: Knees", "1 drop in the left eye", "Inject 10 units · left thigh". */
    fun line(ctx: Context, kind: CareKind, quantity: Double, unit: String, site: String?): String {
        val amount = Formats.quantity(ctx, quantity, unit)
        return when (kind) {
            CareKind.PILL, CareKind.LIQUID -> ctx.getString(R.string.notif_dose_take, amount)
            CareKind.TOPICAL -> {
                val parts = CareSites.bodyParts(site)
                if (parts.isEmpty()) ctx.getString(R.string.care_line_apply_thin)
                else ctx.getString(R.string.care_line_apply, parts.joinToString(", ") { bodyName(ctx, it) })
            }
            CareKind.DROPS -> ctx.getString(R.string.care_line_drops, amount, dropSiteName(ctx, site))
            CareKind.INHALED -> ctx.getString(R.string.care_line_inhale, amount)
            CareKind.INJECTION -> injectionSiteName(ctx, site)?.let { ctx.getString(R.string.care_line_inject_site, amount, it) }
                ?: ctx.getString(R.string.care_line_inject, amount)
            CareKind.SUPPOSITORY -> ctx.getString(R.string.care_line_insert, amount)
            CareKind.MEASURE -> ctx.getString(R.string.care_line_measure)
            CareKind.HABIT -> ctx.getString(R.string.care_line_habit)
        }
    }

    /** The answer button: "Taken", "Applied", "Done", "Write it down". */
    fun answer(ctx: Context, kind: CareKind): String = ctx.getString(answerRes(kind))

    fun answerRes(kind: CareKind): Int = when (kind) {
        CareKind.PILL, CareKind.LIQUID -> R.string.action_taken
        CareKind.INHALED, CareKind.INJECTION -> R.string.care_answer_used
        CareKind.TOPICAL -> R.string.care_answer_applied
        CareKind.DROPS, CareKind.SUPPOSITORY -> R.string.care_answer_put
        CareKind.HABIT -> R.string.care_answer_habit
        CareKind.MEASURE -> R.string.care_answer_record
    }

    fun bodyName(ctx: Context, part: String): String = ctx.getString(
        when (part) {
            "face" -> R.string.body_face
            "scalp" -> R.string.body_scalp
            "neck" -> R.string.body_neck
            "chest" -> R.string.body_chest
            "back" -> R.string.body_back
            "belly" -> R.string.body_belly
            "arms" -> R.string.body_arms
            "hands" -> R.string.body_hands
            "legs" -> R.string.body_legs
            "knees" -> R.string.body_knees
            "feet" -> R.string.body_feet
            else -> R.string.body_skin
        }
    )

    fun dropSiteName(ctx: Context, site: String?): String {
        val organ = CareSites.dropOrganOf(site)
        val side = CareSites.dropSideOf(site)
        return ctx.getString(
            when (organ to side) {
                "ear" to "left" -> R.string.site_ear_left
                "ear" to "right" -> R.string.site_ear_right
                "ear" to "both" -> R.string.site_ear_both
                "nose" to "left" -> R.string.site_nose_left
                "nose" to "right" -> R.string.site_nose_right
                "nose" to "both" -> R.string.site_nose_both
                "eye" to "left" -> R.string.site_eye_left
                "eye" to "right" -> R.string.site_eye_right
                else -> R.string.site_eye_both
            }
        )
    }

    fun organName(ctx: Context, organ: String) = ctx.getString(
        when (organ) { "ear" -> R.string.organ_ear; "nose" -> R.string.organ_nose; else -> R.string.organ_eye }
    )

    fun sideName(ctx: Context, side: String) = ctx.getString(
        when (side) { "left" -> R.string.side_left; "right" -> R.string.side_right; else -> R.string.side_both }
    )

    fun injectionSiteName(ctx: Context, site: String?): String? = when (site) {
        "belly_left" -> ctx.getString(R.string.inj_belly_left)
        "belly_right" -> ctx.getString(R.string.inj_belly_right)
        "thigh_left" -> ctx.getString(R.string.inj_thigh_left)
        "thigh_right" -> ctx.getString(R.string.inj_thigh_right)
        "arm_left" -> ctx.getString(R.string.inj_arm_left)
        "arm_right" -> ctx.getString(R.string.inj_arm_right)
        else -> null
    }

    /** Where it goes, in a few words, for the medicine's page ("Knees, Hands", "each eye"). */
    fun siteSummary(ctx: Context, kind: CareKind, site: String?): String? = when (kind) {
        CareKind.TOPICAL -> CareSites.bodyParts(site).takeIf { it.isNotEmpty() }?.joinToString(", ") { bodyName(ctx, it) }
        CareKind.DROPS -> dropSiteName(ctx, site)
        CareKind.INJECTION -> if (site == CareSites.ROTATE) ctx.getString(R.string.wiz_rotate_title) else null
        else -> null
    }

    fun measureName(ctx: Context, type: MeasureType): String = ctx.getString(
        when (type) {
            MeasureType.BLOOD_PRESSURE -> R.string.measure_bp
            MeasureType.BLOOD_SUGAR -> R.string.measure_sugar
            MeasureType.WEIGHT -> R.string.measure_weight
            MeasureType.TEMPERATURE -> R.string.measure_temp
            MeasureType.OXYGEN -> R.string.measure_oxygen
        }
    )

    fun measureSub(ctx: Context, type: MeasureType): String = ctx.getString(
        when (type) {
            MeasureType.BLOOD_PRESSURE -> R.string.measure_bp_sub
            MeasureType.BLOOD_SUGAR -> R.string.measure_sugar_sub
            MeasureType.WEIGHT -> R.string.measure_weight_sub
            MeasureType.TEMPERATURE -> R.string.measure_temp_sub
            MeasureType.OXYGEN -> R.string.measure_oxygen_sub
        }
    )

    fun habitName(ctx: Context, type: HabitType): String = ctx.getString(
        when (type) {
            HabitType.WATER -> R.string.habit_water
            HabitType.WALK -> R.string.habit_walk
            HabitType.EXERCISE -> R.string.habit_exercise
            HabitType.FOOT_CHECK -> R.string.habit_foot
            HabitType.SLEEP -> R.string.habit_sleep
            HabitType.OTHER -> R.string.habit_other
        }
    )

    fun habitSub(ctx: Context, type: HabitType): String = ctx.getString(
        when (type) {
            HabitType.WATER -> R.string.habit_water_sub
            HabitType.WALK -> R.string.habit_walk_sub
            HabitType.EXERCISE -> R.string.habit_exercise_sub
            HabitType.FOOT_CHECK -> R.string.habit_foot_sub
            HabitType.SLEEP -> R.string.habit_sleep_sub
            HabitType.OTHER -> R.string.habit_other_sub
        }
    )

    fun levelName(ctx: Context, level: ReadingLevel): String? = when (level) {
        ReadingLevel.VERY_LOW -> ctx.getString(R.string.level_very_low)
        ReadingLevel.LOW -> ctx.getString(R.string.level_low)
        ReadingLevel.A_BIT_LOW -> ctx.getString(R.string.level_a_bit_low)
        ReadingLevel.NORMAL -> ctx.getString(R.string.level_normal)
        ReadingLevel.A_BIT_HIGH -> ctx.getString(R.string.level_a_bit_high)
        ReadingLevel.HIGH -> ctx.getString(R.string.level_high)
        ReadingLevel.VERY_HIGH -> ctx.getString(R.string.level_very_high)
        ReadingLevel.NONE -> null
    }

    /** A number as a meter shows it: no trailing ".0", one decimal place at most. */
    fun decimal(ctx: Context, v: Double): String {
        val text = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
        return Formats.digits(ctx, text)
    }

    /** "128/82", "7.2 mmol/L", "64.5 kg", "101.2 °F", "97%". */
    fun value(ctx: Context, type: MeasureType, value: Double, value2: Double?): String = when (type) {
        MeasureType.BLOOD_PRESSURE -> decimal(ctx, value) + "/" + decimal(ctx, value2 ?: 0.0)
        MeasureType.BLOOD_SUGAR -> decimal(ctx, value) + " " + ctx.getString(R.string.unit_mmol)
        MeasureType.WEIGHT -> decimal(ctx, value) + " " + ctx.getString(R.string.unit_kg)
        MeasureType.TEMPERATURE -> decimal(ctx, value) + " " + ctx.getString(R.string.unit_f)
        MeasureType.OXYGEN -> decimal(ctx, value) + "%"
    }

    fun value(ctx: Context, reading: Reading): String {
        val type = measureOf(reading.type) ?: return decimal(ctx, reading.value)
        val main = value(ctx, type, reading.value, reading.value2)
        return reading.pulse?.let { main + " · " + ctx.getString(R.string.reading_pulse, Formats.number(ctx, it)) } ?: main
    }
}
