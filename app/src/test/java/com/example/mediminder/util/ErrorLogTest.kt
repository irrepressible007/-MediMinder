package com.example.mediminder.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ErrorLogTest {
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before fun setUp() {
        ErrorLog.init(ApplicationProvider.getApplicationContext<Context>())
        ErrorLog.clear()
    }

    @After fun tearDown() {
        ErrorLog.clear()
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    @Test fun `problems are recorded with their cause and counted`() {
        assertEquals(0, ErrorLog.count())
        ErrorLog.record("Update", "Update check failed", IOException("HTTP 403"))
        ErrorLog.record("Photos", "Could not decode a photo")
        assertEquals(2, ErrorLog.count())
        val text = ErrorLog.read()
        assertTrue(text.contains("[Update] Update check failed"))
        assertTrue("the cause is kept", text.contains("java.io.IOException: HTTP 403"))
        assertNotNull(ErrorLog.lastAt())
        assertTrue("the report names the app and phone", ErrorLog.report().startsWith("MediMinder "))
    }

    @Test fun `the log stays small, keeping the newest problems`() {
        repeat(3000) { ErrorLog.record("Test", "problem number $it — a reasonably long line to fill the log quickly") }
        val text = ErrorLog.read()
        assertTrue("capped at about 96 KB", text.length < 130_000)
        assertTrue("the newest is kept", text.contains("problem number 2999"))
        assertTrue("the oldest is gone", !text.contains("problem number 0 "))
    }

    @Test fun `clearing empties it`() {
        ErrorLog.record("Test", "something")
        ErrorLog.clear()
        assertEquals(0, ErrorLog.count())
    }
}
