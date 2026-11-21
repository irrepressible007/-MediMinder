package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import com.example.mediminder.data.local.entity.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** One scheduled dose on one day. */
data class Occurrence(
    val medication: Medication,
    val schedule: Schedule,
    val date: LocalDate,
) {
    val day: Long get() = date.toEpochDay()
    val minuteOfDay: Int get() = schedule.minuteOfDay
    val dateTime: LocalDateTime get() = date.atTime(timeOf(schedule.minuteOfDay))
}

/** What the Today screen shows for a dose. */
enum class DoseState { UPCOMING, DUE, TAKEN, SKIPPED, SNOOZED, MISSED }

/** Rules for when doses happen. Pure functions of their inputs — no Android, no clock. */
object DosePlanner {
    /** A dose not answered this long after its time counts as missed. */
    const val MISSED_AFTER_MINUTES = 120L

    fun isCourseActiveOn(medication: Medication, date: LocalDate): Boolean {
        if (medication.archived) return false
        val day = date.toEpochDay()
        medication.startDate?.let { if (day < it) return false }
        medication.endDate?.let { if (day > it) return false }
        return true
    }

    fun occursOn(medication: Medication, schedule: Schedule, date: LocalDate): Boolean =
        schedule.enabled &&
            !medication.isAsNeeded &&
            isCourseActiveOn(medication, date) &&
            DaysOfWeek.includes(schedule.daysMask, date.dayOfWeek)

    fun occurrencesOn(medications: List<MedicationWithSchedules>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Occurrence> =
        medications.flatMap { item ->
            val added = item.medication.createdAt.toLocalDateTime(zone)
            item.schedules
                .filter { occursOn(item.medication, it, date) }
                .map { Occurrence(item.medication, it, date) }
                // a dose whose time had fully passed before the medicine was added was never missed
                .filter { it.dateTime.plusMinutes(MISSED_AFTER_MINUTES).isAfter(added) }
        }.sortedWith(compareBy<Occurrence> { it.minuteOfDay }.thenBy { it.medication.name.lowercase() })

    /**
     * The next time this dose should alert, strictly after [after].
     * [isAnswered] (epoch day → Boolean) lets a dose already taken early be skipped.
     * Returns null when the dose will never occur again (course over, disabled, archived).
     */
    fun nextOccurrence(
        medication: Medication,
        schedule: Schedule,
        after: LocalDateTime,
        isAnswered: (Long) -> Boolean = { false },
    ): LocalDateTime? {
        if (!schedule.enabled || medication.isAsNeeded || medication.archived) return null
        if (schedule.daysMask and DaysOfWeek.EVERY_DAY == 0) return null

        var date = after.toLocalDate()
        medication.startDate?.let { start ->
            val startDate = LocalDate.ofEpochDay(start)
            if (startDate.isAfter(date)) date = startDate
        }
        val time = timeOf(schedule.minuteOfDay)
        // 8 days covers every weekday pattern; one more day for "answered today"
        repeat(9) {
            if (medication.endDate != null && date.toEpochDay() > medication.endDate) return null
            if (occursOn(medication, schedule, date)) {
                val candidate = date.atTime(time)
                if (candidate.isAfter(after) && !isAnswered(date.toEpochDay())) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** State of a dose given what was logged and the current time. */
    fun stateOf(occurrence: Occurrence, log: DoseLog?, now: LocalDateTime): DoseState {
        when (log?.status) {
            DoseStatus.TAKEN -> return DoseState.TAKEN
            DoseStatus.SKIPPED -> return DoseState.SKIPPED
            DoseStatus.SNOOZED -> if (now.isBefore(occurrence.dateTime.plusMinutes(MISSED_AFTER_MINUTES * 2))) return DoseState.SNOOZED
            null -> Unit
        }
        val time = occurrence.dateTime
        return when {
            now.isBefore(time) -> DoseState.UPCOMING
            now.isBefore(time.plusMinutes(MISSED_AFTER_MINUTES)) -> DoseState.DUE
            else -> DoseState.MISSED
        }
    }
}

/** Part of the day, for grouping the dose list. */
enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

fun dayPartOf(minuteOfDay: Int): DayPart = when (minuteOfDay) {
    in 4 * 60 until 12 * 60 -> DayPart.MORNING
    in 12 * 60 until 17 * 60 -> DayPart.AFTERNOON
    in 17 * 60 until 21 * 60 -> DayPart.EVENING
    else -> DayPart.NIGHT
}

fun timeOf(minuteOfDay: Int): LocalTime = LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60)

fun minuteOf(time: LocalTime): Int = time.hour * 60 + time.minute

fun LocalDateTime.toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atZone(zone).toInstant().toEpochMilli()

fun Long.toLocalDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)
