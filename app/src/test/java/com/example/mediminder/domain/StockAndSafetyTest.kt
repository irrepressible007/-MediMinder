package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.domain.TestData.item
import com.example.mediminder.domain.TestData.medication
import com.example.mediminder.domain.TestData.schedule
import com.example.mediminder.updater.isNewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StockTest {
    private val today = LocalDate.of(2026, 3, 2).toEpochDay()

    @Test fun `days left from a twice-daily schedule`() {
        val med = medication(stock = 10.0, trackStock = true)
        val times = listOf(schedule(id = 1, hour = 8), schedule(id = 2, hour = 21))
        assertEquals(2.0, Stock.dailyUse(med, times), 1e-9)
        assertEquals(5, Stock.daysLeft(med, times))
    }

    @Test fun `half tablets and weekly doses are counted correctly`() {
        val med = medication(stock = 3.0, trackStock = true)
        assertEquals(6, Stock.daysLeft(med, listOf(schedule(quantity = 0.5))))
        // one tablet only on Fridays = 1/7 a day → 3 tablets last 21 days
        assertEquals(21, Stock.daysLeft(med, listOf(schedule(days = DaysOfWeek.bit(DayOfWeek.FRIDAY)))))
    }

    @Test fun `low when at or under the threshold`() {
        val times = listOf(schedule())
        assertTrue(Stock.isLow(medication(stock = 5.0, trackStock = true, threshold = 5), times, today))
        assertFalse(Stock.isLow(medication(stock = 6.0, trackStock = true, threshold = 5), times, today))
        assertFalse(Stock.isLow(medication(stock = 0.0, trackStock = false), times, today))
    }

    @Test fun `not low when what is left finishes the course`() {
        // 3 days of a 1-a-day course left and exactly 3 tablets: no need to buy more
        val med = medication(stock = 3.0, trackStock = true, end = LocalDate.ofEpochDay(today + 2))
        assertFalse(Stock.isLow(med, listOf(schedule()), today))
        assertEquals(0, Stock.suggestedPurchase(med, listOf(schedule()), 30, today))
    }

    @Test fun `suggests enough for the refill period, rounded up`() {
        val med = medication(stock = 4.0, trackStock = true)
        assertEquals(26, Stock.suggestedPurchase(med, listOf(schedule()), 30, today))
        assertEquals(41, Stock.suggestedPurchase(med, listOf(schedule(id = 1), schedule(id = 2, quantity = 0.5)), 30, today))
    }

    @Test fun `as-needed medicines use the threshold as a count`() {
        val med = medication(stock = 2.0, trackStock = true, asNeeded = true, threshold = 3)
        assertTrue(Stock.isLow(med, emptyList(), today))
        assertNull(Stock.daysLeft(med, emptyList()))
    }
}

class AdherenceTest {
    private val day1 = LocalDate.of(2026, 3, 2)

    @Test fun `counts taken, missed and skipped and the streak`() {
        val med = medication(createdOn = day1)
        val morning = schedule(id = 1, hour = 8)
        val night = schedule(id = 2, hour = 21)
        val meds = listOf(item(med, morning, night))
        fun log(scheduleId: Long, day: LocalDate, status: DoseStatus) =
            DoseLog(medicationId = 1, scheduleId = scheduleId, scheduledDay = day.toEpochDay(), status = status)
        val logs = listOf(
            // day 1: both taken
            log(1, day1, DoseStatus.TAKEN), log(2, day1, DoseStatus.TAKEN),
            // day 2: night missed
            log(1, day1.plusDays(1), DoseStatus.TAKEN),
            // day 3: night skipped (does not break the streak)
            log(1, day1.plusDays(2), DoseStatus.TAKEN), log(2, day1.plusDays(2), DoseStatus.SKIPPED),
            // day 4: morning taken, night still upcoming
            log(1, day1.plusDays(3), DoseStatus.TAKEN),
        )
        val now = day1.plusDays(3).atTime(12, 0)
        val s = Adherence.summarise(meds, logs, day1, day1.plusDays(3), now)
        assertEquals(7, s.expected)   // the day-4 night dose is not due yet
        assertEquals(5, s.taken)
        assertEquals(1, s.missed)
        assertEquals(1, s.skipped)
        assertEquals(71, s.rate)
        assertEquals(2, s.streak)     // day 3 and day 4; day 2 had a miss
    }

    @Test fun `a medicine counts only from the day it was added`() {
        val med = medication(createdOn = day1.plusDays(2))
        val s = Adherence.summarise(listOf(item(med, schedule(hour = 8))), emptyList(), day1, day1.plusDays(2), day1.plusDays(2).atTime(23, 0))
        assertEquals(1, s.expected)
        assertEquals(1, s.missed)
    }
}

class SafetyCheckTest {
    @Test fun `penicillin allergy flags amoxicillin`() {
        val w = SafetyCheck.check("Moxacil", "Amoxicillin Trihydrate", "Penicillin, dust", emptyList())
        assertTrue(w.single() is SafetyWarning.AllergyClass)
    }

    @Test fun `direct allergy to the ingredient`() {
        val w = SafetyCheck.check("Napa", "Paracetamol", "paracetamol", emptyList())
        assertEquals(SafetyWarning.Allergy("paracetamol"), w.single())
    }

    @Test fun `no allergy words are ignored`() {
        assertTrue(SafetyCheck.check("Napa", "Paracetamol", "None", emptyList()).isEmpty())
        assertTrue(SafetyCheck.check("Napa", "Paracetamol", "নেই", emptyList()).isEmpty())
    }

    @Test fun `two brands of the same generic are flagged`() {
        val ace = medication(id = 2, name = "Ace", generic = "Paracetamol")
        val w = SafetyCheck.check("Napa Extra", "Paracetamol + Caffeine", "", listOf(ace))
        assertEquals(SafetyWarning.DuplicateIngredient("Paracetamol", "Ace"), w.single())
    }

    @Test fun `editing a medicine does not warn about itself, and stopped ones are ignored`() {
        val napa = medication(id = 1, name = "Napa", generic = "Paracetamol")
        assertTrue(SafetyCheck.check("Napa", "Paracetamol", "", listOf(napa), editingId = 1).isEmpty())
        assertTrue(SafetyCheck.check("Ace", "Paracetamol", "", listOf(napa.copy(archived = true))).isEmpty())
    }
}

class UpdateVersionTest {
    @Test fun `only a higher version counts as an update`() {
        assertTrue(isNewer("v2.0.1", "v2.0.0"))
        assertTrue(isNewer("v2.0.10", "v2.0.9"))
        assertTrue(isNewer("v3.0", "v2.9.9"))
        assertFalse(isNewer("v1.0.10", "v2.0.0"))
        assertFalse(isNewer("v2.0.0", "v2.0.0"))
    }
}
