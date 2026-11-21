package com.example.mediminder.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.mediminder.R
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.DosePattern
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Text formatting shared by the screens and the notifications. */
object Formats {
    /** Units a dose can be measured in; the key is what is stored in Medication.unit. */
    val UNITS = listOf("tablet", "capsule", "ml", "spoon", "drop", "puff", "injection", "unit", "sachet", "suppository", "application")

    /** Counted one at a time: no "½ drop" or "¼ puff". (Insulin is counted in whole units.) */
    fun isWholeUnit(unit: String) = unit in setOf("drop", "puff", "injection", "unit", "suppository", "application")

    private val BANGLA_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    fun isBangla(context: Context): Boolean = locale(context).language == "bn"

    /** Converts ASCII digits to Bangla digits when the app is in Bangla. */
    fun digits(context: Context, text: String): String =
        if (!isBangla(context)) text else buildString(text.length) {
            text.forEach { c -> append(if (c in '0'..'9') BANGLA_DIGITS[c - '0'] else c) }
        }

    fun number(context: Context, value: Double): String = digits(context, DosePattern.formatQuantity(value))

    fun number(context: Context, value: Int): String = digits(context, value.toString())

    /** "8:30 AM" in English; "সকাল ৮:৩০" in Bangla. */
    fun time(context: Context, minuteOfDay: Int): String {
        val hour = (minuteOfDay / 60) % 24
        val minute = minuteOfDay % 60
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val clock = "%d:%02d".format(Locale.US, h12, minute)
        return if (isBangla(context)) {
            val part = context.getString(
                when (hour) {
                    in 4..11 -> R.string.daypart_morning
                    in 12..14 -> R.string.daypart_noon
                    in 15..17 -> R.string.daypart_afternoon
                    in 18..19 -> R.string.daypart_evening
                    else -> R.string.daypart_night
                }
            )
            "$part ${digits(context, clock)}"
        } else {
            "$clock ${if (hour < 12) "AM" else "PM"}"
        }
    }

    fun date(context: Context, date: LocalDate, style: FormatStyle = FormatStyle.MEDIUM): String =
        digits(context, DateTimeFormatter.ofLocalizedDate(style).withLocale(locale(context)).format(date))

    /** "Mon, 3 Mar" style short date. */
    fun shortDate(context: Context, date: LocalDate): String =
        digits(context, DateTimeFormatter.ofPattern("EEE, d MMM", locale(context)).format(date))

    fun unitName(context: Context, unit: String, quantity: Double): String {
        val plural = quantity > 1.0
        val res = when (unit) {
            "capsule" -> if (plural) R.string.unit_capsule_plural else R.string.unit_capsule
            "ml" -> R.string.unit_ml
            "spoon" -> if (plural) R.string.unit_spoon_plural else R.string.unit_spoon
            "drop" -> if (plural) R.string.unit_drop_plural else R.string.unit_drop
            "puff" -> if (plural) R.string.unit_puff_plural else R.string.unit_puff
            "injection" -> if (plural) R.string.unit_injection_plural else R.string.unit_injection
            "unit" -> if (plural) R.string.unit_unit_plural else R.string.unit_unit
            "sachet" -> if (plural) R.string.unit_sachet_plural else R.string.unit_sachet
            "suppository" -> if (plural) R.string.unit_suppository_plural else R.string.unit_suppository
            "application" -> if (plural) R.string.unit_application_plural else R.string.unit_application
            else -> if (plural) R.string.unit_tablet_plural else R.string.unit_tablet
        }
        return context.getString(res)
    }

    /** "1 tablet", "½ tablet", "5 ml" */
    fun quantity(context: Context, quantity: Double, unit: String): String =
        "${number(context, quantity)} ${unitName(context, unit, quantity)}"

    fun mealTiming(context: Context, timing: MealTiming): String = context.getString(
        when (timing) {
            MealTiming.ANY -> R.string.meal_any
            MealTiming.BEFORE_MEAL -> R.string.meal_before
            MealTiming.AFTER_MEAL -> R.string.meal_after
            MealTiming.WITH_MEAL -> R.string.meal_with
            MealTiming.EMPTY_STOMACH -> R.string.meal_empty_stomach
            MealTiming.BEDTIME -> R.string.meal_bedtime
        }
    )

    /** "৳ 12.50" */
    fun taka(context: Context, amount: Double): String =
        "৳ " + digits(context, if (amount % 1.0 == 0.0) "%,.0f".format(Locale.US, amount) else "%,.2f".format(Locale.US, amount))

    /** Picks the unit that matches a catalogue dosage form. */
    fun unitForForm(form: String?): String {
        val f = form.orEmpty().lowercase()
        return when {
            "capsule" in f -> "capsule"
            "syrup" in f || "suspension" in f || "solution" in f || "liquid" in f || "elixir" in f -> "ml"
            "drop" in f -> "drop"
            "inhal" in f || "aerosol" in f || "spray" in f || "rotacap" in f -> "puff"
            "injection" in f || "infusion" in f || "vial" in f -> "injection"
            "sachet" in f || "powder" in f || "granule" in f -> "sachet"
            "suppositor" in f -> "suppository"
            "cream" in f || "ointment" in f || "gel" in f || "lotion" in f -> "application"
            else -> "tablet"
        }
    }
}

/** Per-app language (বাংলা / English), independent of the phone's language. */
object AppLocale {
    /** Applies a language to the whole app; "" follows the phone. */
    fun apply(language: String) {
        AppCompatDelegate.setApplicationLocales(
            if (language.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language)
        )
    }

    /**
     * A context in the user's chosen language, for text built outside an activity
     * (notifications posted from alarm receivers).
     */
    fun localized(context: Context): Context {
        val language = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').ifEmpty {
            SettingsRepository.storedLanguage(context)
        }
        if (language.isEmpty()) return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(config)
    }
}
