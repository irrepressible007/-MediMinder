package com.example.mediminder.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.mediminder.R
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.util.AppLocale
import com.example.mediminder.util.ErrorLog
import com.example.mediminder.util.Formats
import java.time.LocalDate

/**
 * Sends a family member a normal SMS when medicine doses go unanswered. Each dose is reported
 * once, doses missed around the same time share one message, and at most [MAX_PER_DAY]
 * messages go in a day (an SMS costs money).
 */
class FamilyAlerter(private val context: Context, private val settings: SettingsRepository) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** A missed dose: its name and its time. */
    data class Missed(val key: String, val name: String, val minuteOfDay: Int)

    fun canSendSms(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun alreadySent(scheduleId: Long, day: Long): Boolean = prefs.getBoolean(key(scheduleId, day), false)

    fun sentToday(today: LocalDate = LocalDate.now()): Int =
        if (prefs.getLong(COUNT_DAY, -1) == today.toEpochDay()) prefs.getInt(COUNT, 0) else 0

    /**
     * Sends one message about [missed] doses (the ones not yet reported). Returns true if it went.
     */
    fun sendMissed(missed: List<Missed>, today: LocalDate = LocalDate.now()): Boolean {
        val f = settings.current.family
        val fresh = missed.filter { !prefs.getBoolean(it.key, false) }
        if (!f.on || f.phone.isBlank() || fresh.isEmpty()) return false
        if (sentToday(today) >= MAX_PER_DAY) {
            ErrorLog.record("Family alert", "Daily limit reached; not sent")
            return false
        }
        val ctx = AppLocale.localized(context)
        if (!send(f.phone, missedText(ctx, settings.current.userName, fresh))) return false
        prefs.edit().apply { fresh.forEach { putBoolean(it.key, true) } }.apply()
        count(today)
        prune(today)
        return true
    }

    /** "Send a test message" on the settings page. */
    fun sendTest(): Boolean {
        val f = settings.current.family
        if (f.phone.isBlank()) return false
        val ctx = AppLocale.localized(context)
        return send(f.phone, testText(ctx, settings.current.userName))
    }

    private fun send(phone: String, text: String): Boolean {
        if (!canSendSms()) {
            ErrorLog.record("Family alert", "No permission to send SMS")
            return false
        }
        return try {
            val sms = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else null)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            // Bangla needs Unicode SMS (70 characters a part); long messages go in parts
            val parts = runCatching { sms.divideMessage(text) }.getOrNull() ?: split(text)
            sms.sendMultipartTextMessage(phone.trim(), null, parts, null, null)
            true
        } catch (e: Exception) {
            ErrorLog.record("Family alert", "Could not send SMS", e)
            false
        }
    }

    private fun count(today: LocalDate) {
        val n = sentToday(today) + 1
        prefs.edit().putLong(COUNT_DAY, today.toEpochDay()).putInt(COUNT, n).apply()
    }

    /** Forget reports older than a week. */
    private fun prune(today: LocalDate) {
        val old = prefs.all.keys.filter { k -> k.startsWith("sent:") && (k.substringAfterLast(':').toLongOrNull() ?: 0) < today.toEpochDay() - 7 }
        if (old.isNotEmpty()) prefs.edit().apply { old.forEach(::remove) }.apply()
    }

    companion object {
        private const val FILE = "family_alerts"
        private const val COUNT = "count"
        private const val COUNT_DAY = "countDay"
        const val MAX_PER_DAY = 5

        fun key(scheduleId: Long, day: Long) = "sent:$scheduleId:$day"

        /**
         * SMS parts when the phone can't work them out: 153 plain characters a part, or 67 when
         * the text needs Unicode (Bangla).
         */
        fun split(text: String): ArrayList<String> {
            val size = if (text.all { it.code < 128 }) 153 else 67
            return ArrayList(text.chunked(size))
        }

        /** "MediMinder: Rahima hasn't marked these as taken: Napa 500 mg (8:15 AM). Please check in." */
        fun missedText(ctx: Context, userName: String, missed: List<Missed>): String {
            val who = userName.trim().ifBlank { ctx.getString(R.string.family_someone) }
            val list = missed.joinToString(", ") { "${it.name} (${Formats.time(ctx, it.minuteOfDay)})" }
            return ctx.getString(R.string.family_sms_missed, who, list)
        }

        fun testText(ctx: Context, userName: String): String =
            ctx.getString(R.string.family_sms_test, userName.trim().ifBlank { ctx.getString(R.string.family_someone) })
    }
}
