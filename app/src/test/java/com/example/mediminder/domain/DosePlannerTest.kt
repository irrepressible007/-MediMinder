package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.domain.TestData.medication
import com.example.mediminder.domain.TestData.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DosePlannerTest {
    // Monday 2 March 2026
    private val monday = LocalDate.of(2026, 3, 2)

    @Test fun `daily dose later today is next`() {
        val next = DosePlanner.nextOccurrence(medication(), schedule(hour = 20), monday.atTime(9, 0))
        assertEquals(monday.atTime(20, 0), next)
    }

    @Test fun `dose already past today moves to tomorrow`() {
        val next = DosePlanner.nextOccurrence(medication(), schedule(hour = 8), monday.atTime(9, 0))
        assertEquals(monday.plusDays(1).atTime(8, 0), next)
    }

    @Test fun `exactly at the dose time counts as passed`() {
        val next = DosePlanner.nextOccurrence(medication(), schedule(hour = 8), monday.atTime(8, 0))
        assertEquals(monday.plusDays(1).atTime(8, 0), next)
    }

    @Test fun `a dose taken early today is not alarmed again`() {
        val next = DosePlanner.nextOccurrence(medication(), schedule(hour = 20), monday.atTime(9, 0)) { it == monday.toEpochDay() }
        assertEquals(monday.plusDays(1).atTime(20, 0), next)
    }

    @Test fun `weekday mask skips to the next allowed day`() {
        // Friday only (Bangladesh weekend example)
        val fridays = DaysOfWeek.bit(DayOfWeek.FRIDAY)
        val next = DosePlanner.nextOccurrence(medication(), schedule(hour = 9, days = fridays), monday.atTime(10, 0))
        assertEquals(LocalDate.of(2026, 3, 6).atTime(9, 0), next)
    }

    @Test fun `course that has not started waits for its start date`() {
        val next = DosePlanner.nextOccurrence(medication(start = monday.plusDays(3)), schedule(hour = 8), monday.atTime(7, 0))
        assertEquals(monday.plusDays(3).atTime(8, 0), next)
    }

    @Test fun `finished course never rings again`() {
        val next = DosePlanner.nextOccurrence(medication(end = monday), schedule(hour = 8), monday.atTime(9, 0))
        assertNull(next)
    }

    @Test fun `last day of course still rings`() {
        val next = DosePlanner.nextOccurrence(medication(end = monday), schedule(hour = 21), monday.atTime(9, 0))
        assertEquals(monday.atTime(21, 0), next)
    }

    @Test fun `as needed, archived, disabled and empty-mask doses never ring`() {
        val at = monday.atTime(7, 0)
        assertNull(DosePlanner.nextOccurrence(medication(asNeeded = true), schedule(), at))
        assertNull(DosePlanner.nextOccurrence(medication(archived = true), schedule(), at))
        assertNull(DosePlanner.nextOccurrence(medication(), schedule(enabled = false), at))
        assertNull(DosePlanner.nextOccurrence(medication(), schedule(days = 0), at))
    }

    @Test fun `occurrences are sorted by time`() {
        val med = medication()
        val list = DosePlanner.occurrencesOn(listOf(TestData.item(med, schedule(id = 1, hour = 21), schedule(id = 2, hour = 8))), monday)
        assertEquals(listOf(8 * 60, 21 * 60), list.map { it.minuteOfDay })
    }

    @Test fun `state goes upcoming, due, missed — and logs win`() {
        val occ = Occurrence(medication(), schedule(hour = 8), monday)
        assertEquals(DoseState.UPCOMING, DosePlanner.stateOf(occ, null, monday.atTime(7, 59)))
        assertEquals(DoseState.DUE, DosePlanner.stateOf(occ, null, monday.atTime(8, 0)))
        assertEquals(DoseState.DUE, DosePlanner.stateOf(occ, null, monday.atTime(9, 59)))
        assertEquals(DoseState.MISSED, DosePlanner.stateOf(occ, null, monday.atTime(10, 0)))
        val taken = DoseLog(medicationId = 1, scheduleId = 10, scheduledDay = monday.toEpochDay(), status = DoseStatus.TAKEN)
        assertEquals(DoseState.TAKEN, DosePlanner.stateOf(occ, taken, monday.atTime(23, 0)))
        val snoozed = taken.copy(status = DoseStatus.SNOOZED)
        assertEquals(DoseState.SNOOZED, DosePlanner.stateOf(occ, snoozed, monday.atTime(8, 15)))
        // A snooze that was never followed up eventually counts as missed
        assertEquals(DoseState.MISSED, DosePlanner.stateOf(occ, snoozed, monday.atTime(13, 0)))
    }

    @Test fun `day parts match how Bangladeshi prescriptions group doses`() {
        assertEquals(DayPart.MORNING, dayPartOf(8 * 60))
        assertEquals(DayPart.AFTERNOON, dayPartOf(14 * 60))
        assertEquals(DayPart.EVENING, dayPartOf(18 * 60))
        assertEquals(DayPart.NIGHT, dayPartOf(22 * 60))
        assertEquals(DayPart.NIGHT, dayPartOf(1 * 60))
    }

    @Test fun `doses whose time had passed before the medicine was added are not shown as missed`() {
        val day = java.time.LocalDate.of(2026, 11, 21)
        val added = day.atTime(22, 50).atZone(TestData.zone).toInstant().toEpochMilli()
        val napa = TestData.medication().copy(createdAt = added)
        val item = TestData.item(napa, TestData.schedule(id = 10, hour = 8, minute = 15), TestData.schedule(id = 11, hour = 21, minute = 15))
        // 8:15 had long passed; 9:15 PM is still within its two hours, so it can be taken
        assertEquals(listOf(11L), DosePlanner.occurrencesOn(listOf(item), day).map { it.schedule.id })
        assertTrue("nothing on the days before", DosePlanner.occurrencesOn(listOf(item), day.minusDays(1)).isEmpty())
        assertEquals(2, DosePlanner.occurrencesOn(listOf(item), day.plusDays(1)).size)
    }
}
