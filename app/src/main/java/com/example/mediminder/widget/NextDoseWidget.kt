package com.example.mediminder.widget

import com.example.mediminder.util.LinkKey
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.mediminder.MainActivity
import com.example.mediminder.R
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import com.example.mediminder.domain.CareKind
import com.example.mediminder.domain.CareSites
import com.example.mediminder.domain.DoseState
import com.example.mediminder.domain.DosePlanner
import com.example.mediminder.domain.Occurrence
import com.example.mediminder.reminders.DoseActionReceiver
import com.example.mediminder.reminders.Notifier
import com.example.mediminder.reminders.ReminderEntryPoint
import com.example.mediminder.reminders.ReminderScheduler
import com.example.mediminder.util.AppLocale
import com.example.mediminder.util.CareText
import com.example.mediminder.util.ErrorLog
import com.example.mediminder.util.Formats
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** What the widget shows: the dose due now (or next), and how today is going. */
data class NextDose(
    /** The dose to show, or null when nothing more is due today. */
    val occurrence: Occurrence?,
    /** True when it is due now (the answer button shows). */
    val dueNow: Boolean,
    /** Where it goes this time (cream, drops, injection site). */
    val site: String?,
    val done: Int,
    val total: Int,
) {
    companion object {
        fun of(meds: List<MedicationWithSchedules>, logs: List<DoseLog>, now: LocalDateTime): NextDose {
            val today = now.toLocalDate()
            val logBy = logs.filter { it.scheduleId != null && it.scheduledDay == today.toEpochDay() }.associateBy { it.scheduleId!! }
            val states = DosePlanner.occurrencesOn(meds, today).map { it to DosePlanner.stateOf(it, logBy[it.schedule.id], now) }
            val due = states.filter { it.second == DoseState.DUE }.minByOrNull { it.first.minuteOfDay }
            val next = due ?: states.filter { it.second == DoseState.UPCOMING || it.second == DoseState.SNOOZED }.minByOrNull { it.first.minuteOfDay }
            val site = next?.first?.let { occ ->
                CareSites.siteFor(occ.medication, meds.firstOrNull { it.medication.id == occ.medication.id }?.schedules.orEmpty(), occ.schedule, occ.day)
            }
            return NextDose(next?.first, due != null, site, states.count { it.second == DoseState.TAKEN }, states.size)
        }
    }
}

/**
 * Home-screen widget: the next dose, with Taken (or Applied, Done, Write it down) when it is
 * due. It refreshes whenever a dose rings or is answered, and every 30 minutes.
 */
class NextDoseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                manager.updateAppWidget(ids, render(context, load(context)))
            } catch (e: Exception) {
                ErrorLog.record("Widget", "Could not update the widget", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Asks every MediMinder widget on the home screen to show the latest. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NextDoseWidget::class.java))
            if (ids == null || ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, NextDoseWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        private suspend fun load(context: Context): NextDose {
            val db = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java).database()
            val today = LocalDateTime.now().toLocalDate().toEpochDay()
            return NextDose.of(db.medicationDao().getActiveWithSchedules(), db.doseLogDao().getRange(today, today), LocalDateTime.now())
        }

        fun render(context: Context, next: NextDose): RemoteViews {
            val ctx = AppLocale.localized(context)
            val views = RemoteViews(context.packageName, R.layout.widget_next_dose)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, Notifier.DEEP_LINK_TODAY))
            views.setTextViewText(
                R.id.widget_progress,
                if (next.total == 0) "" else ctx.getString(R.string.day_summary_done, Formats.number(ctx, next.done), Formats.number(ctx, next.total)),
            )
            val occ = next.occurrence
            if (occ == null) {
                views.setTextViewText(R.id.widget_label, ctx.getString(R.string.widget_next_label))
                views.setTextViewText(
                    R.id.widget_name,
                    ctx.getString(if (next.total > 0 && next.done == next.total) R.string.widget_all_done else R.string.widget_nothing),
                )
                views.setViewVisibility(R.id.widget_detail, View.GONE)
                views.setViewVisibility(R.id.widget_answer, View.GONE)
                return views
            }
            val m = occ.medication
            views.setTextViewText(R.id.widget_label, ctx.getString(if (next.dueNow) R.string.widget_now else R.string.widget_next_label))
            views.setTextViewText(R.id.widget_name, listOf(CareText.name(ctx, m.kind, m.subtype, m.name), m.strength).filter { it.isNotBlank() }.joinToString(" "))
            views.setViewVisibility(R.id.widget_detail, View.VISIBLE)
            views.setTextViewText(R.id.widget_detail, Formats.time(ctx, occ.minuteOfDay) + " · " + CareText.line(ctx, m.kind, occ.schedule.quantity, m.unit, next.site))
            if (!next.dueNow) {
                views.setViewVisibility(R.id.widget_answer, View.GONE)
                return views
            }
            views.setViewVisibility(R.id.widget_answer, View.VISIBLE)
            views.setTextViewText(R.id.widget_answer, CareText.answer(ctx, m.kind))
            views.setOnClickPendingIntent(
                R.id.widget_answer,
                // a health check is answered by writing the reading down, in the app
                if (m.kind == CareKind.MEASURE) openApp(context, "${Notifier.DEEP_LINK_RECORD}${occ.schedule.id}/${occ.day}")
                else PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, DoseActionReceiver::class.java)
                        .setAction(DoseActionReceiver.ACTION_TAKE)
                        .setData(Uri.parse("mediminder://widget-answer/${occ.schedule.id}/${occ.day}"))
                        .putExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, occ.schedule.id)
                        .putExtra(ReminderScheduler.EXTRA_DAY, occ.day),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return views
        }

        private fun openApp(context: Context, link: String): PendingIntent =
            PendingIntent.getActivity(
                context, 0,
                LinkKey.sign(
                    context,
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse(link))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
