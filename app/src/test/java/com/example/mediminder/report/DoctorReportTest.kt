package com.example.mediminder.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.local.entity.Reading
import com.example.mediminder.data.settings.AppSettings
import com.example.mediminder.data.settings.MedicalProfile
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.MeasureType
import com.example.mediminder.domain.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** The doctor's PDF: what goes in it, and that it is written. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DoctorReportTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 11, 21)

    private fun sample(): DoctorReportData {
        val napa = TestData.medication(id = 1, name = "Napa", createdOn = today.minusDays(30)).copy(strength = "500 mg", dosePattern = "1+0+1", mealTiming = MealTiming.AFTER_MEAL)
        val walk = TestData.medication(id = 2, name = "Walk", createdOn = today.minusDays(30)).copy(kind = CareKind.HABIT, subtype = "WALK")
        val meds = listOf(
            TestData.item(napa, TestData.schedule(id = 10, medicationId = 1, hour = 8), TestData.schedule(id = 11, medicationId = 1, hour = 21)),
            TestData.item(walk, TestData.schedule(id = 20, medicationId = 2, hour = 17)),
        )
        val logs = (0 until 7).flatMap { d ->
            val day = today.minusDays(d.toLong()).toEpochDay()
            listOfNotNull(
                DoseLog(scheduleId = 10, medicationId = 1, scheduledDay = day, scheduledMinute = 480, status = DoseStatus.TAKEN),
                if (d % 3 == 0) null else DoseLog(scheduleId = 11, medicationId = 1, scheduledDay = day, scheduledMinute = 1260, status = DoseStatus.TAKEN),
                DoseLog(scheduleId = 20, medicationId = 2, scheduledDay = day, scheduledMinute = 1020, status = DoseStatus.TAKEN),
            )
        }
        val noon = today.atTime(12, 0).atZone(TestData.zone).toInstant().toEpochMilli()
        val readings = (0 until 6).map { i ->
            Reading(type = MeasureType.BLOOD_PRESSURE.name, value = 142.0 - i * 3, value2 = 92.0 - i * 2, pulse = 72, takenAt = noon - i * 86_400_000L)
        } + Reading(type = MeasureType.BLOOD_SUGAR.name, value = 6.4, timing = "FASTING", takenAt = noon)
        val settings = AppSettings(userName = "Rahima", profile = MedicalProfile(bloodGroup = "B+", allergies = "Penicillin", birthYear = 1956))
        return DoctorReport.build(context, settings, meds, logs, readings, days = 7, today = today, now = today.atTime(23, 59))
    }

    @Test fun `the report has the person, medicines, readings and habits`() {
        val d = sample()
        assertEquals("Rahima", d.person)
        assertEquals(70, d.age)
        assertEquals("Napa 500 mg", d.medicines.single().name)
        assertTrue(d.medicines.single().how, d.medicines.single().how.startsWith("1+0+1"))
        assertEquals("medicines only", 14, d.taken + d.missed + d.skipped)
        assertEquals(setOf(MeasureType.BLOOD_PRESSURE, MeasureType.BLOOD_SUGAR), d.readings.keys)
        assertEquals(1, d.habits.size)
    }

    @Test fun `every page draws, and the pages can be looked at`() {
        val out = System.getenv("REPORT_PNG")?.let(::File)
        val pages = mutableListOf<Bitmap>()
        DoctorReport.draw(context, sample(), object : DoctorReport.PageSink {
            override fun start(number: Int, width: Int, height: Int): Canvas {
                val bmp = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
                pages += bmp
                return Canvas(bmp).apply { drawColor(Color.WHITE); scale(2f, 2f) }
            }
            override fun finish() = Unit
        })
        assertTrue(pages.isNotEmpty())
        // something was drawn beyond the white page
        assertTrue(pages.first().let { b -> (0 until b.height step 20).any { y -> (0 until b.width step 20).any { x -> b.getPixel(x, y) != Color.WHITE } } })
        out?.let { dir -> dir.mkdirs(); pages.forEachIndexed { i, b -> File(dir, "report_${i + 1}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
    }
}
