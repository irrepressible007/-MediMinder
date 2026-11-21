package com.example.mediminder.domain

import com.example.mediminder.data.local.entity.DaysOfWeek
import com.example.mediminder.ui.health.CareEditorViewModel
import com.example.mediminder.ui.health.RecordViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Creams, drops and injections; health-check ranges; habits' first times. */
class CareTest {

    @Test fun `the kind follows the unit`() {
        assertEquals(CareKind.DROPS, CareKind.forUnit("drop"))
        assertEquals(CareKind.TOPICAL, CareKind.forUnit("application"))
        assertEquals(CareKind.INHALED, CareKind.forUnit("puff"))
        assertEquals(CareKind.INJECTION, CareKind.forUnit("unit"))
        assertEquals(CareKind.LIQUID, CareKind.forUnit("ml"))
        assertEquals(CareKind.PILL, CareKind.forUnit("capsule"))
        assertFalse(CareKind.HABIT.isMedicine)
        assertTrue(CareKind.TOPICAL.isMedicine)
    }

    @Test fun `injections go round all six places without repeating`() {
        val insulin = TestData.medication().copy(kind = CareKind.INJECTION, site = CareSites.ROTATE)
        val morning = TestData.schedule(id = 1, hour = 8)
        val night = TestData.schedule(id = 2, hour = 20)
        val schedules = listOf(night, morning)   // in any order: the day's doses count in time order
        val start = LocalDate.of(2026, 11, 21).toEpochDay()
        val sites = (0 until 3).flatMap { d -> listOf(morning, night).map { CareSites.siteFor(insulin, schedules, it, start + d) } }
        assertEquals(CareSites.INJECTION.toSet(), sites.toSet())
        assertEquals(6, sites.size)
        // and the next day starts the round again
        assertEquals(sites[0], CareSites.siteFor(insulin, schedules, morning, start + 3))
    }

    @Test fun `a place that doesn't move is kept as chosen`() {
        val cream = TestData.medication().copy(kind = CareKind.TOPICAL, site = "knees,hands")
        assertEquals("knees,hands", CareSites.siteFor(cream, emptyList(), TestData.schedule(), 0))
        assertNull(CareSites.siteFor(TestData.medication(), emptyList(), TestData.schedule(), 0))
        // an injection that isn't rotated says nothing about where
        val shot = TestData.medication().copy(kind = CareKind.INJECTION, site = null)
        assertNull(CareSites.siteFor(shot, emptyList(), TestData.schedule(), 0))
    }

    @Test fun `days off are not counted in the rotation`() {
        val insulin = TestData.medication().copy(kind = CareKind.INJECTION, site = CareSites.ROTATE)
        val weekdays = TestData.schedule(id = 1, hour = 8, days = DaysOfWeek.EVERY_DAY and DaysOfWeek.bit(DayOfWeek.FRIDAY).inv())
        val evening = TestData.schedule(id = 2, hour = 20)
        val friday = LocalDate.of(2026, 11, 20)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        // on Friday the evening dose is the day's first
        assertEquals(CareSites.injectionSite(friday.toEpochDay(), 0, 1), CareSites.siteFor(insulin, listOf(weekdays, evening), evening, friday.toEpochDay()))
    }

    @Test fun `drops and cream places are read back`() {
        assertEquals("ear", CareSites.dropOrgan("Ear Drop"))
        assertEquals("nose", CareSites.dropOrgan("Nasal Spray"))
        assertEquals("eye", CareSites.dropOrgan("Eye/Ear Drop"))
        assertEquals("eye", CareSites.dropOrganOf("eye_left"))
        assertEquals("left", CareSites.dropSideOf("eye_left"))
        assertEquals("both", CareSites.dropSideOf(null))
        assertEquals("in head-to-toe order", "hands,knees", CareSites.bodySite(setOf("knees", "hands")))
        assertNull(CareSites.bodySite(emptySet()))
        assertEquals(listOf("knees"), CareSites.bodyParts("knees,elbow"))
    }

    @Test fun `opened too long ago means time for a new one, reminded weekly`() {
        val opened = LocalDate.of(2026, 11, 1).toEpochDay()
        val drops = TestData.medication().copy(kind = CareKind.DROPS, openedAt = opened, useWithinDays = 28)
        assertFalse(OpenedItems.needsReplacing(drops, opened + 27))
        assertTrue(OpenedItems.needsReplacing(drops, opened + 28))
        assertTrue(OpenedItems.remindToday(drops, opened + 28))
        assertFalse(OpenedItems.remindToday(drops, opened + 29))
        assertTrue(OpenedItems.remindToday(drops, opened + 35))
        // tablets don't go off when the strip is opened
        val tablets = drops.copy(kind = CareKind.PILL)
        assertNull(OpenedItems.replaceOn(tablets))
        assertNull(OpenedItems.replaceOn(drops.copy(useWithinDays = null)))
    }

    @Test fun `blood pressure in plain words`() {
        fun bp(top: Double, bottom: Double) = Readings.level(MeasureType.BLOOD_PRESSURE, top, bottom)
        assertEquals(ReadingLevel.NORMAL, bp(118.0, 76.0))
        assertEquals(ReadingLevel.NORMAL, bp(120.0, 80.0))
        assertEquals(ReadingLevel.A_BIT_HIGH, bp(134.0, 82.0))
        assertEquals(ReadingLevel.HIGH, bp(150.0, 95.0))
        assertEquals(ReadingLevel.VERY_HIGH, bp(185.0, 100.0))
        assertEquals(ReadingLevel.LOW, bp(85.0, 55.0))
        assertTrue(Readings.urgent(bp(185.0, 100.0)))
    }

    @Test fun `blood sugar depends on when it was checked`() {
        fun sugar(v: Double, t: SugarTiming) = Readings.level(MeasureType.BLOOD_SUGAR, v, timing = t)
        assertEquals(ReadingLevel.NORMAL, sugar(5.0, SugarTiming.FASTING))
        assertEquals(ReadingLevel.A_BIT_HIGH, sugar(6.5, SugarTiming.FASTING))
        assertEquals(ReadingLevel.HIGH, sugar(8.0, SugarTiming.FASTING))
        assertEquals(ReadingLevel.NORMAL, sugar(7.0, SugarTiming.AFTER_MEAL))
        assertEquals(ReadingLevel.A_BIT_HIGH, sugar(9.0, SugarTiming.RANDOM))
        assertEquals(ReadingLevel.VERY_LOW, sugar(2.8, SugarTiming.RANDOM))
        assertEquals(ReadingLevel.VERY_HIGH, sugar(18.0, SugarTiming.AFTER_MEAL))
    }

    @Test fun `temperature, oxygen and weight`() {
        assertEquals(ReadingLevel.NORMAL, Readings.level(MeasureType.TEMPERATURE, 98.6))
        assertEquals(ReadingLevel.HIGH, Readings.level(MeasureType.TEMPERATURE, 101.0))
        assertEquals(ReadingLevel.VERY_HIGH, Readings.level(MeasureType.TEMPERATURE, 104.0))
        assertEquals(ReadingLevel.NORMAL, Readings.level(MeasureType.OXYGEN, 97.0))
        assertEquals(ReadingLevel.A_BIT_LOW, Readings.level(MeasureType.OXYGEN, 93.0))
        assertEquals(ReadingLevel.VERY_LOW, Readings.level(MeasureType.OXYGEN, 85.0))
        assertEquals(ReadingLevel.NONE, Readings.level(MeasureType.WEIGHT, 64.0))
    }

    @Test fun `typing slips are caught, and other meter units converted`() {
        assertFalse("top and bottom swapped", Readings.plausible(MeasureType.BLOOD_PRESSURE, 80.0, 120.0))
        assertFalse("bottom number missing", Readings.plausible(MeasureType.BLOOD_PRESSURE, 120.0, null))
        assertTrue(Readings.plausible(MeasureType.BLOOD_PRESSURE, 120.0, 80.0, pulse = 72))
        assertFalse("mg/dL typed as mmol/L", Readings.plausible(MeasureType.BLOOD_SUGAR, 126.0))
        assertFalse(Readings.plausible(MeasureType.OXYGEN, 101.0))
        assertEquals(7.0, Readings.mgdlToMmol(126.0), 0.0)
        assertEquals(100.4, Readings.celsiusToF(38.0), 0.0)
    }

    @Test fun `numbers typed on a Bangla keyboard are understood`() {
        assertEquals(128.0, RecordViewModel.number("১২৮")!!, 0.0)
        assertEquals(7.2, RecordViewModel.number("৭,২")!!, 0.0)
        assertEquals(64.5, RecordViewModel.number(" 64.5 ")!!, 0.0)
        assertNull(RecordViewModel.number("abc"))
        assertNull(RecordViewModel.number(""))
    }

    @Test fun `habits and checks start with sensible times`() {
        assertEquals(7, CareEditorViewModel.presetTimes(HabitType.WATER.name).size)
        assertEquals(listOf(8 * 60, 20 * 60), CareEditorViewModel.presetTimes(MeasureType.BLOOD_PRESSURE.name))
        assertEquals(DaysOfWeek.bit(DayOfWeek.FRIDAY), CareEditorViewModel.presetDays(MeasureType.WEIGHT.name))
        assertEquals(DaysOfWeek.EVERY_DAY, CareEditorViewModel.presetDays(HabitType.WALK.name))
    }
}
