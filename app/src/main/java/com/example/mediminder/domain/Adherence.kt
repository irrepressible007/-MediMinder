package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** Doses that were due on one day: [expected] = taken + skipped + missed. */
data class DayAdherence(val date: LocalDate, val expected: Int, val taken: Int, val missed: Int)

data class MedicationAdherence(val medicationId: Long, val name: String, val expected: Int, val taken: Int) {
    val rate: Int? get() = if (expected == 0) null else (taken * 100.0 / expected).roundToInt()
}

data class AdherenceSummary(
    val expected: Int,
    val taken: Int,
    val skipped: Int,
    val missed: Int,
    /** Consecutive days with no missed dose, ending today. */
    val streak: Int,
    val days: List<DayAdherence>,
    val perMedication: List<MedicationAdherence>,
) {
    /** Percentage of due doses taken, or null when nothing was due. */
    val rate: Int? get() = if (expected == 0) null else (taken * 100.0 / expected).roundToInt()
}

object Adherence {
    /**
     * Adherence over [fromDate]..[toDate] using the current schedules. A dose counts once it is
     * answered or past its missed-window; doses still pending today are left out.
     * A medicine counts only from the day it was added (or its start date).
     */
    fun summarise(
        medications: List<MedicationWithSchedules>,
        logs: List<DoseLog>,
        fromDate: LocalDate,
        toDate: LocalDate,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AdherenceSummary {
        val logByOccurrence = logs.filter { it.scheduleId != null }.associateBy { it.scheduleId to it.scheduledDay }
        val perMed = LinkedHashMap<Long, MedicationAdherence>()
        val days = mutableListOf<DayAdherence>()
        var expected = 0
        var taken = 0
        var skipped = 0
        var missed = 0

        var date = fromDate
        while (!date.isAfter(toDate)) {
            var dayExpected = 0
            var dayTaken = 0
            var dayMissed = 0
            val counted = DosePlanner.occurrencesOn(medications, date).filter { occ ->
                val addedOn = occ.medication.createdAt.toLocalDateTime(zone).toLocalDate()
                !date.isBefore(addedOn)
            }
            for (occ in counted) {
                val log = logByOccurrence[occ.schedule.id to occ.day]
                val state = DosePlanner.stateOf(occ, log, now)
                if (state == DoseState.UPCOMING || state == DoseState.DUE || state == DoseState.SNOOZED) continue
                dayExpected++
                val entry = perMed[occ.medication.id] ?: MedicationAdherence(occ.medication.id, occ.medication.name, 0, 0)
                when (state) {
                    DoseState.TAKEN -> { dayTaken++; perMed[occ.medication.id] = entry.copy(expected = entry.expected + 1, taken = entry.taken + 1) }
                    DoseState.SKIPPED -> { skipped++; perMed[occ.medication.id] = entry.copy(expected = entry.expected + 1) }
                    else -> { dayMissed++; perMed[occ.medication.id] = entry.copy(expected = entry.expected + 1) }
                }
            }
            expected += dayExpected
            taken += dayTaken
            missed += dayMissed
            days += DayAdherence(date, dayExpected, dayTaken, dayMissed)
            date = date.plusDays(1)
        }

        // Streak: walk back from the last day; a day with a missed dose ends it, empty days don't count
        var streak = 0
        for (day in days.asReversed()) {
            if (day.expected == 0) continue
            if (day.missed > 0) break
            streak++
        }

        return AdherenceSummary(expected, taken, skipped, missed, streak, days, perMed.values.toList())
    }

    /** Points for the little motivation counter: 10 per dose taken. */
    fun points(takenDoses: Int): Int = takenDoses * 10
}
