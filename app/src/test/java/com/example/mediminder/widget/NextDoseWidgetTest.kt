package com.example.mediminder.widget

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.R
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/** The home-screen widget: which dose it shows, and how. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NextDoseWidgetTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val day = LocalDate.of(2026, 11, 21)
    private val napa = TestData.item(TestData.medication(id = 1, name = "Napa"), TestData.schedule(id = 10, medicationId = 1, hour = 8), TestData.schedule(id = 11, medicationId = 1, hour = 21))
    private val drops = TestData.item(
        TestData.medication(id = 2, name = "Timolol").copy(kind = CareKind.DROPS, unit = "drop", site = "eye_left"),
        TestData.schedule(id = 20, medicationId = 2, hour = 9),
    )

    private fun taken(scheduleId: Long, medicationId: Long, hour: Int) = DoseLog(
        scheduleId = scheduleId, medicationId = medicationId, scheduledDay = day.toEpochDay(), scheduledMinute = hour * 60,
        status = DoseStatus.TAKEN, quantity = 1.0, loggedAt = day.atTime(hour, 5).atZone(TestData.zone).toInstant().toEpochMilli(),
    )

    private fun shown(next: NextDose): View = NextDoseWidget.render(context, next).apply(context, FrameLayout(context))

    @Test fun `the dose due now, with its answer button`() {
        val next = NextDose.of(listOf(napa, drops), listOf(taken(10, 1, 8)), day.atTime(9, 2))
        assertTrue(next.dueNow)
        assertEquals(20L, next.occurrence!!.schedule.id)
        assertEquals("eye_left", next.site)
        assertEquals(1, next.done)
        assertEquals(3, next.total)

        val view = shown(next)
        assertEquals("Timolol", view.findViewById<TextView>(R.id.widget_name).text.toString())
        assertTrue(view.findViewById<TextView>(R.id.widget_detail).text.contains("1 drop in the left eye"))
        val answer = view.findViewById<Button>(R.id.widget_answer)
        assertEquals(View.VISIBLE, answer.visibility)
        assertEquals("Done", answer.text.toString())
    }

    @Test fun `later in the day it shows the next one, without a button`() {
        val next = NextDose.of(listOf(napa, drops), listOf(taken(10, 1, 8), taken(20, 2, 9)), day.atTime(12, 0))
        assertFalse(next.dueNow)
        assertEquals(11L, next.occurrence!!.schedule.id)
        assertEquals(View.GONE, shown(next).findViewById<Button>(R.id.widget_answer).visibility)
    }

    @Test fun `everything taken says all done`() {
        val next = NextDose.of(listOf(napa), listOf(taken(10, 1, 8), taken(11, 1, 21)), day.atTime(22, 0))
        assertNull(next.occurrence)
        assertEquals(2, next.done)
        assertEquals("All done for today", shown(next).findViewById<TextView>(R.id.widget_name).text.toString())
    }
}
