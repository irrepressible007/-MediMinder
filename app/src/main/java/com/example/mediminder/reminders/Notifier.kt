package com.example.mediminder.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mediminder.MainActivity
import com.example.mediminder.R
import com.example.mediminder.data.photos.MedicinePhotos
import android.graphics.Bitmap
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.util.AppLocale
import com.example.mediminder.util.Formats
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the notifications need to describe one dose. */
data class DoseInfo(
    val scheduleId: Long,
    val day: Long,
    val minuteOfDay: Int,
    val medicationId: Long,
    val name: String,
    val strength: String,
    val quantity: Double,
    val unit: String,
    val mealTiming: MealTiming,
    val alertType: AlertType,
    /** Photo of the box or strip (MedicinePhotos reference), or null. */
    val photo: String? = null,
)

/** Builds and posts every notification the app shows. */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val manager = NotificationManagerCompat.from(appContext)

    /** Text comes from a context in the user's chosen language, not the phone's. */
    private val context: Context get() = AppLocale.localized(appContext)

    // ── Channels ────────────────────────────────────────────────────────────

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ctx = context
        val system = appContext.getSystemService(NotificationManager::class.java)

        system.createNotificationChannel(
            NotificationChannel(CHANNEL_DOSES, ctx.getString(R.string.channel_doses), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_doses_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, ctx.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_alarms_desc)
                setSound(alarmSound(), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
                enableLights(true)
                lightColor = Color.GREEN
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_REFILLS, ctx.getString(R.string.channel_refills), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = ctx.getString(R.string.channel_refills_desc)
            }
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_APPOINTMENTS, ctx.getString(R.string.channel_appointments), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_appointments_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_SETUP, ctx.getString(R.string.channel_setup), NotificationManager.IMPORTANCE_LOW).apply {
                description = ctx.getString(R.string.channel_setup_desc)
            }
        )
        // Channel from version 1.x, replaced by the ones above
        system.deleteNotificationChannel("mediminder_alarms")
    }

    private fun alarmSound(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    // ── Doses ───────────────────────────────────────────────────────────────

    /** A normal reminder with Taken / Snooze / Skip buttons, one per dose. */
    fun showDoseReminder(dose: DoseInfo, snoozeMinutes: Int, repeatCount: Int) {
        val ctx = context
        val title = ctx.getString(R.string.notif_dose_title, doseName(dose))
        val text = doseLine(ctx, dose)
        val builder = NotificationCompat.Builder(ctx, CHANNEL_DOSES)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(ContextCompat.getColor(appContext, R.color.brand))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(false)
            .setAutoCancel(false)
            .setGroup(GROUP_DOSES)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(openApp(DEEP_LINK_TODAY))
            .addAction(0, ctx.getString(R.string.action_taken), answer(DoseActionReceiver.ACTION_TAKE, dose))
            .addAction(0, ctx.getString(R.string.action_snooze, Formats.number(ctx, snoozeMinutes)), answer(DoseActionReceiver.ACTION_SNOOZE, dose))
            .addAction(0, ctx.getString(R.string.action_skip), answer(DoseActionReceiver.ACTION_SKIP, dose))
        if (repeatCount > 0) builder.setSubText(ctx.getString(R.string.notif_dose_repeat))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }
        withPhoto(builder, dose.photo, summary = text)
        post(doseNotificationId(dose.scheduleId, dose.day), builder.build())
    }

    /**
     * The ringing alarm: one notification for every alarm-type dose that is waiting, with a
     * full-screen alarm page on the lock screen. FLAG_INSISTENT repeats the alarm sound until
     * the user answers or [ringMinutes] pass.
     */
    fun showAlarm(doses: List<DoseInfo>, snoozeMinutes: Int, ringMinutes: Int) {
        if (doses.isEmpty()) {
            manager.cancel(ID_ALARM)
            return
        }
        val ctx = context
        val title = if (doses.size == 1) ctx.getString(R.string.notif_dose_title, doseName(doses.first()))
        else ctx.getString(R.string.notif_alarm_many, Formats.number(ctx, doses.size))
        val lines = doses.map { "${doseName(it)} — ${doseLine(ctx, it)}" }
        val style = NotificationCompat.InboxStyle().also { s -> lines.forEach(s::addLine) }

        val fullScreen = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(ContextCompat.getColor(appContext, R.color.brand))
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setTimeoutAfter(ringMinutes * 60_000L)
            .addAction(0, ctx.getString(if (doses.size == 1) R.string.action_taken else R.string.action_take_all), answerAll(DoseActionReceiver.ACTION_TAKE_ALL))
            .addAction(0, ctx.getString(R.string.action_snooze, Formats.number(ctx, snoozeMinutes)), answerAll(DoseActionReceiver.ACTION_SNOOZE_ALL))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(alarmSound(), AudioManager.STREAM_ALARM)
            builder.setVibrate(longArrayOf(0, 800, 400, 800, 400, 800))
        }
        val photo = doses.firstNotNullOfOrNull { it.photo }
        if (doses.size == 1) withPhoto(builder, photo, summary = lines.first())
        else MedicinePhotos.thumbnail(appContext, photo)?.let(builder::setLargeIcon)
        val notification = builder.build().apply { flags = flags or Notification.FLAG_INSISTENT }
        post(ID_ALARM, notification)
    }

    /**
     * The medicine's photo on the side of the notification (large icon), and the whole photo
     * when it is expanded. Without a photo the notification is left as it is.
     */
    private fun withPhoto(builder: NotificationCompat.Builder, photo: String?, summary: String) {
        val thumb = MedicinePhotos.thumbnail(appContext, photo) ?: return
        builder.setLargeIcon(thumb)
        MedicinePhotos.picture(appContext, photo)?.let { picture ->
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .bigLargeIcon(null as Bitmap?)   // the side picture hides once the big one shows
                    .setSummaryText(summary)
            )
        }
    }

    fun cancelDose(scheduleId: Long, day: Long) = manager.cancel(doseNotificationId(scheduleId, day))

    fun cancelAlarm() = manager.cancel(ID_ALARM)

    // ── Stock ───────────────────────────────────────────────────────────────

    fun showLowStock(medicationId: Long, name: String, stockText: String, daysLeft: Int?, photo: String? = null) {
        val ctx = context
        val text = if (daysLeft != null) ctx.getString(R.string.notif_low_stock_days, stockText, Formats.number(ctx, daysLeft))
        else ctx.getString(R.string.notif_low_stock_units, stockText)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_REFILLS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(ContextCompat.getColor(appContext, R.color.brand))
            .setContentTitle(ctx.getString(R.string.notif_low_stock_title, name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp("$DEEP_LINK_MEDICINE$medicationId"))
            .addAction(0, ctx.getString(R.string.action_restock), openApp("$DEEP_LINK_RESTOCK$medicationId"))
            // the pack's photo helps at the pharmacy
            .also { b -> MedicinePhotos.thumbnail(appContext, photo)?.let(b::setLargeIcon) }
            .build()
        post("stock:$medicationId".hashCode(), notification)
    }

    fun showRefillSummary(names: List<String>) {
        if (names.isEmpty()) return
        val ctx = context
        val title = ctx.getString(R.string.notif_refill_summary_title, Formats.number(ctx, names.size))
        val notification = NotificationCompat.Builder(ctx, CHANNEL_REFILLS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(ContextCompat.getColor(appContext, R.color.brand))
            .setContentTitle(title)
            .setContentText(names.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString(", ") + "\n" + ctx.getString(R.string.notif_refill_summary_text)))
            .setAutoCancel(true)
            .setContentIntent(openApp(DEEP_LINK_REFILLS))
            .build()
        post(ID_REFILL_SUMMARY, notification)
    }

    // ── Setup ───────────────────────────────────────────────────────────────

    /** A quiet, occasional nudge: a phone setting still keeps reminders from ringing on time. */
    fun showSetupNudge(@androidx.annotation.StringRes why: Int, count: Int) {
        val ctx = context
        val title = if (count == 1) ctx.getString(R.string.setup_nudge_title)
        else ctx.getString(R.string.setup_card_title_many, Formats.number(ctx, count))
        val notification = NotificationCompat.Builder(ctx, CHANNEL_SETUP)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(ContextCompat.getColor(appContext, R.color.brand))
            .setContentTitle(title)
            .setContentText(ctx.getString(why))
            .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(why)))
            .setAutoCancel(true)
            .setContentIntent(openApp(DEEP_LINK_RELIABILITY))
            .addAction(0, ctx.getString(R.string.setup_nudge_action), openApp(DEEP_LINK_RELIABILITY))
            .build()
        post(ID_SETUP, notification)
    }

    // ── Appointments ────────────────────────────────────────────────────────

    fun showAppointment(appointment: Appointment, evening: Boolean) {
        val ctx = context
        val dateTime = appointment.dateTime.toLocalDateTime()
        val whenText = Formats.shortDate(ctx, dateTime.toLocalDate()) + ", " + Formats.time(ctx, dateTime.hour * 60 + dateTime.minute)
        val details = buildList {
            add(whenText)
            appointment.hospital?.takeIf { it.isNotBlank() }?.let(::add)
            appointment.serialNumber?.takeIf { it.isNotBlank() }?.let { add(ctx.getString(R.string.appt_serial_value, Formats.digits(ctx, it))) }
        }.joinToString(" · ")
        val title = ctx.getString(if (evening) R.string.notif_appt_tomorrow else R.string.notif_appt_title, appointment.doctorName)
        val alarm = appointment.reminderAlertType == AlertType.ALARM && !evening
        val builder = NotificationCompat.Builder(ctx, if (alarm) CHANNEL_ALARMS else CHANNEL_APPOINTMENTS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(ContextCompat.getColor(appContext, R.color.brand))
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(DEEP_LINK_APPOINTMENTS))
        appointment.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            val dial = PendingIntent.getActivity(
                appContext, appointment.id.toInt(),
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, ctx.getString(R.string.action_call_chamber), dial)
        }
        if (alarm) {
            val fullScreen = PendingIntent.getActivity(
                appContext, appointment.id.toInt(),
                AlarmActivity.appointmentIntent(appContext, appointment.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreen, true).setTimeoutAfter(5 * 60_000L)
        }
        val notification = builder.build().apply { if (alarm) flags = flags or Notification.FLAG_INSISTENT }
        post("appt:${appointment.id}".hashCode(), notification)
    }

    fun cancelAppointment(appointmentId: Long) = manager.cancel("appt:$appointmentId".hashCode())

    // ── Test ────────────────────────────────────────────────────────────────

    fun showTest(alertType: AlertType) {
        val ctx = context
        val alarm = alertType == AlertType.ALARM
        val builder = NotificationCompat.Builder(ctx, if (alarm) CHANNEL_ALARMS else CHANNEL_DOSES)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setColor(ContextCompat.getColor(appContext, R.color.brand))
            .setContentTitle(ctx.getString(R.string.notif_test_title))
            .setContentText(ctx.getString(R.string.notif_test_text))
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .setContentIntent(openApp(DEEP_LINK_TODAY))
        if (alarm) {
            val fullScreen = PendingIntent.getActivity(
                appContext, 7,
                AlarmActivity.testIntent(appContext),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreen, true).setContentIntent(fullScreen)
        }
        post(ID_TEST, builder.build().apply { if (alarm) flags = flags or Notification.FLAG_INSISTENT })
    }

    fun cancelTest() = manager.cancel(ID_TEST)

    // ── Helpers ─────────────────────────────────────────────────────────────

    fun notificationsAllowed(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    @SuppressLint("MissingPermission") // checked in notificationsAllowed()
    private fun post(id: Int, notification: Notification) {
        if (!notificationsAllowed()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call
        }
    }

    private fun doseName(dose: DoseInfo) = listOf(dose.name, dose.strength).filter { it.isNotBlank() }.joinToString(" ")

    private fun doseLine(ctx: Context, dose: DoseInfo): String = buildList {
        add(ctx.getString(R.string.notif_dose_take, Formats.quantity(ctx, dose.quantity, dose.unit)))
        add(Formats.time(ctx, dose.minuteOfDay))
        if (dose.mealTiming != MealTiming.ANY) add(Formats.mealTiming(ctx, dose.mealTiming))
    }.joinToString(" · ")

    private fun answer(action: String, dose: DoseInfo): PendingIntent =
        PendingIntent.getBroadcast(
            appContext, 0,
            Intent(appContext, DoseActionReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("mediminder://answer/${dose.scheduleId}/${dose.day}"))
                .putExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, dose.scheduleId)
                .putExtra(ReminderScheduler.EXTRA_DAY, dose.day),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun answerAll(action: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext, 0,
            Intent(appContext, DoseActionReceiver::class.java).setAction(action).setData(Uri.parse("mediminder://answer/all")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openApp(deepLink: String): PendingIntent =
        PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(deepLink))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val CHANNEL_DOSES = "dose_reminders"
        const val CHANNEL_ALARMS = "dose_alarms"
        const val CHANNEL_REFILLS = "refills"
        const val CHANNEL_APPOINTMENTS = "appointments"
        const val CHANNEL_SETUP = "setup_tips"

        private const val GROUP_DOSES = "doses"
        private const val ID_ALARM = 1001
        private const val ID_TEST = 1002
        private const val ID_REFILL_SUMMARY = 1003
        private const val ID_SETUP = 1004

        const val DEEP_LINK_TODAY = "mediminder://open/today"
        const val DEEP_LINK_REFILLS = "mediminder://open/refills"
        const val DEEP_LINK_APPOINTMENTS = "mediminder://open/appointments"
        const val DEEP_LINK_MEDICINE = "mediminder://open/medicine/"
        const val DEEP_LINK_RESTOCK = "mediminder://open/restock/"
        const val DEEP_LINK_RELIABILITY = "mediminder://open/reliability"

        fun doseNotificationId(scheduleId: Long, day: Long) = "dose:$scheduleId:$day".hashCode()
    }
}
