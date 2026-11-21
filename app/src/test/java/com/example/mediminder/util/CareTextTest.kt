package com.example.mediminder.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.HabitType
import com.example.mediminder.domain.MeasureType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Each kind of care item is put into its own words. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CareTextTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `each kind says what to do`() {
        assertEquals("Take 1 tablet", CareText.line(context, CareKind.PILL, 1.0, "tablet", null))
        assertEquals("Apply to: Knees, Hands", CareText.line(context, CareKind.TOPICAL, 1.0, "application", "knees,hands"))
        assertEquals("Apply a thin layer", CareText.line(context, CareKind.TOPICAL, 1.0, "application", null))
        assertEquals("1 drop in the left eye", CareText.line(context, CareKind.DROPS, 1.0, "drop", "eye_left"))
        assertEquals("2 drops in each nostril", CareText.line(context, CareKind.DROPS, 2.0, "drop", "nose_both"))
        assertEquals("Inhale 2 puffs", CareText.line(context, CareKind.INHALED, 2.0, "puff", null))
        assertEquals("Inject 10 units · left thigh", CareText.line(context, CareKind.INJECTION, 10.0, "unit", "thigh_left"))
    }

    @Test fun `answers and titles fit the kind`() {
        assertEquals("Taken", CareText.answer(context, CareKind.PILL))
        assertEquals("Applied", CareText.answer(context, CareKind.TOPICAL))
        assertEquals("Write it down", CareText.answer(context, CareKind.MEASURE))
        assertEquals("Time to apply Betnovate", CareText.title(context, CareKind.TOPICAL, null, "Betnovate"))
        assertEquals("Time to check your blood pressure", CareText.title(context, CareKind.MEASURE, MeasureType.BLOOD_PRESSURE.name, "x"))
        assertEquals("Time for a glass of water", CareText.title(context, CareKind.HABIT, HabitType.WATER.name, "x"))
        assertEquals("Time for Breathing", CareText.title(context, CareKind.HABIT, HabitType.OTHER.name, "Breathing"))
    }

    @Test fun `checks and habits are named in the app's language, own habits keep their name`() {
        assertEquals("Blood pressure", CareText.name(context, CareKind.MEASURE, MeasureType.BLOOD_PRESSURE.name, "রক্তচাপ"))
        assertEquals("Breathing", CareText.name(context, CareKind.HABIT, HabitType.OTHER.name, "Breathing"))
        assertEquals("Napa", CareText.name(context, CareKind.PILL, null, "Napa"))
    }

    @Test fun `readings read like the meter`() {
        assertEquals("128/82 · pulse 72", CareText.value(context, Reading(type = "BLOOD_PRESSURE", value = 128.0, value2 = 82.0, pulse = 72)))
        assertEquals("7.2 mmol/L", CareText.value(context, Reading(type = "BLOOD_SUGAR", value = 7.2)))
        assertEquals("97%", CareText.value(context, Reading(type = "OXYGEN", value = 97.0)))
    }

    @Config(qualifiers = "bn")
    @Test fun `in Bangla a cream is applied, drops are put in, never eaten`() {
        assertEquals("লাগিয়েছি", CareText.answer(context, CareKind.TOPICAL))
        assertEquals("দিয়েছি", CareText.answer(context, CareKind.DROPS))
        assertEquals("নিয়েছি", CareText.answer(context, CareKind.INHALED))
        assertEquals("খেয়েছি", CareText.answer(context, CareKind.PILL))
        assertEquals("বাম চোখে ১ ফোঁটা দিন", CareText.line(context, CareKind.DROPS, 1.0, "drop", "eye_left"))
        assertEquals("১২৮/৮২", CareText.value(context, MeasureType.BLOOD_PRESSURE, 128.0, 82.0))
    }
}
