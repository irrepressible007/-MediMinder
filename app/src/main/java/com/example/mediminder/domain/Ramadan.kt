package com.example.mediminder.domain

import com.example.mediminder.data.settings.MealTimes
import com.example.mediminder.data.settings.RamadanSettings
import java.time.LocalDate

/**
 * Ramadan in Bangladesh: when it is, and how the day's meals move.
 *
 * During the fast people eat at Sehri (before dawn), at Iftar (sunset) and often again late
 * at night after Taraweeh. Medicines taken "with breakfast" move to Sehri, "with dinner" to
 * Iftar, and a midday dose to late night: the usual advice for fasting with medicines.
 */
object Ramadan {
    /**
     * The first day of Ramadan as usually announced in Bangladesh. These are estimates from
     * the lunar calendar; the moon sighting can move them by a day, so the app always says
     * "around" and lets people set the exact dates.
     */
    private val STARTS: List<LocalDate> = listOf(
        "2027-02-09", "2028-01-29", "2029-01-17", "2030-01-07", "2030-12-27",
        "2031-12-16", "2032-12-05", "2033-11-24", "2034-11-14", "2035-11-03",
    ).map(LocalDate::parse)

    /** Ramadan lasts 29 or 30 days. */
    const val DAYS = 30

    /** How early the app suggests getting ready. */
    const val SUGGEST_DAYS_BEFORE = 10

    /** This year's Ramadan if it is on now, otherwise the next one. */
    fun estimatedStart(today: LocalDate): LocalDate? = STARTS.firstOrNull { !today.isAfter(estimatedEnd(it)) }

    /** The last day of fasting (Eid is the day after). */
    fun estimatedEnd(start: LocalDate): LocalDate = start.plusDays(DAYS - 1L)

    /** True from [SUGGEST_DAYS_BEFORE] days before Ramadan until it ends. */
    fun isNear(today: LocalDate): Boolean {
        val start = estimatedStart(today) ?: return false
        return !today.isBefore(start.minusDays(SUGGEST_DAYS_BEFORE.toLong()))
    }

    /** Meal times during Ramadan: breakfast → Sehri, lunch → after Taraweeh, dinner → Iftar. */
    fun meals(r: RamadanSettings): MealTimes = MealTimes(
        breakfast = r.sehri,
        lunch = r.lateNight,
        evening = r.iftar + (Math.floorMod(r.lateNight - r.iftar, 24 * 60)) / 2,
        dinner = r.iftar,
        bedtime = (r.lateNight + 30) % (24 * 60),
    )

    /**
     * A time that falls while fasting: from about 45 minutes after Sehri (dawn) until Iftar.
     * Doses set by hand at such times can't be taken; the app points them out.
     */
    fun duringFast(minuteOfDay: Int, r: RamadanSettings): Boolean {
        val dawn = r.sehri + 45
        return minuteOfDay in dawn until r.iftar
    }
}
