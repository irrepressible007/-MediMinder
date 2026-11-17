package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.DoseSlot
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.settings.MealTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DosePatternTest {
    private val meals = MealTimes(breakfast = 8 * 60, lunch = 13 * 60 + 30, evening = 17 * 60 + 30, dinner = 21 * 60, bedtime = 22 * 60 + 30)

    @Test fun `parses the common prescription patterns`() {
        assertEquals(listOf(DoseSlot.MORNING to 1.0, DoseSlot.NIGHT to 1.0), DosePattern.parse("1+0+1")!!.doses)
        assertEquals(3, DosePattern.parse("1+1+1")!!.dosesPerDay)
        assertEquals(listOf(DoseSlot.NIGHT to 1.0), DosePattern.parse("0+0+1")!!.doses)
        assertEquals(4, DosePattern.parse("1+1+1+1")!!.dosesPerDay)
        assertEquals(DoseSlot.EVENING, DosePattern.parse("0+0+1+0")!!.doses.single().first)
    }

    @Test fun `accepts Bangla digits, halves and dashes`() {
        assertEquals("1+0+1", DosePattern.parse("১+০+১")!!.text)
        assertEquals(listOf(DoseSlot.MORNING to 0.5, DoseSlot.NIGHT to 0.5), DosePattern.parse("½+0+½")!!.doses)
        assertEquals(listOf(DoseSlot.MORNING to 0.5, DoseSlot.NIGHT to 0.5), DosePattern.parse("0.5 + 0 + 0.5")!!.doses)
        assertEquals("1+0+1", DosePattern.parse("1-0-1")!!.text)
        assertEquals("2+0+2", DosePattern.parse("2+0+2")!!.text)
    }

    @Test fun `rejects things that are not patterns`() {
        assertNull(DosePattern.parse("0+0+0"))
        assertNull(DosePattern.parse("1+0"))
        assertNull(DosePattern.parse("1+a+1"))
        assertNull(DosePattern.parse(""))
        assertNull(DosePattern.parse("1+0+1+0+1"))
    }

    @Test fun `after meal is a little after each meal`() {
        val plan = MealSchedule.plan(DosePattern.parse("1+1+1")!!, MealTiming.AFTER_MEAL, meals, beforeMinutes = 30, afterMinutes = 15)
        assertEquals(listOf(8 * 60 + 15, 13 * 60 + 45, 21 * 60 + 15), plan.map { it.minuteOfDay })
    }

    @Test fun `before meal is half an hour before`() {
        val plan = MealSchedule.plan(DosePattern.parse("1+0+1")!!, MealTiming.BEFORE_MEAL, meals, beforeMinutes = 30, afterMinutes = 15)
        assertEquals(listOf(7 * 60 + 30, 20 * 60 + 30), plan.map { it.minuteOfDay })
    }

    @Test fun `bedtime puts the night dose at bedtime`() {
        val plan = MealSchedule.plan(DosePattern.parse("0+0+1")!!, MealTiming.BEDTIME, meals, 30, 15)
        assertEquals(22 * 60 + 30, plan.single().minuteOfDay)
    }

    @Test fun `times wrap past midnight instead of going negative`() {
        val early = meals.copy(breakfast = 0 * 60 + 15)
        assertEquals(23 * 60 + 45, MealSchedule.minuteFor(DoseSlot.MORNING, MealTiming.BEFORE_MEAL, early, 30, 15))
    }
}
