package com.example.mediminder.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The privacy policy and terms ship inside the app, in both languages. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LegalTest {
    @Test fun `privacy policy and terms are in the app in English and Bangla`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (doc in listOf("privacy", "terms")) for (lang in listOf("en", "bn")) {
            val text = context.assets.open("$doc.$lang.md").bufferedReader().use { it.readText() }
            assertTrue("$doc.$lang starts with a title", text.startsWith("# "))
            assertTrue("$doc.$lang has a contact", text.contains("github.com/irrepressible007/-MediMinder/issues"))
        }
    }
}
