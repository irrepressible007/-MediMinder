package com.example.mediminder.reminders

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import com.example.mediminder.ui.theme.AlarmBackgroundBottom
import com.example.mediminder.ui.theme.AlarmBackgroundTop
import com.example.mediminder.ui.theme.AlarmTakenButton
import com.example.mediminder.R
import com.example.mediminder.ui.components.ringing
import com.example.mediminder.ui.components.PulseRings
import androidx.compose.material.icons.filled.NotificationsActive
import com.example.mediminder.ui.photos.MedicinePhoto
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.MealTiming
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.ui.theme.MediMinderTheme
import com.example.mediminder.util.Formats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * Full-screen alarm shown over the lock screen. Lists every dose that is ringing right now
 * (from the active_alerts table, so it survives process death). The alarm sound itself
 * belongs to the notification and stops as soon as every dose is answered.
 */
@AndroidEntryPoint
class AlarmActivity : AppCompatActivity() {
    @Inject lateinit var db: MediMinderDatabase
    @Inject lateinit var doseActions: DoseActions
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifier: Notifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val testMode = intent.getBooleanExtra(EXTRA_TEST, false)
        val appointmentId = intent.getLongExtra(EXTRA_APPOINTMENT_ID, -1L)

        // AppCompat's setContentView skips the NavigationEvent owner that BackHandler needs
        initializeViewTreeOwners()
        setContent {
            // Bigger than the app: this is read at arm's length, often without glasses
            MediMinderTheme(darkTheme = true, textScale = settings.current.textScale) {
                when {
                    testMode -> TestAlarm(onDone = { notifier.cancelTest(); finish() })
                    appointmentId > 0 -> AppointmentAlarm(appointmentId)
                    else -> DoseAlarm()
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun unlockThen(action: () -> Unit) {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = action()
            })
        } else action()
    }

    private data class RingingDose(
        val scheduleId: Long,
        val day: Long,
        val name: String,
        val detail: String,
        val photo: String?,
    )

    @Composable
    private fun DoseAlarm() {
        val context = LocalContext.current
        val snooze = settings.current.snoozeMinutes
        val doses by remember {
            combine(db.activeAlertDao().observeAll(), db.medicationDao().observeAllWithSchedules()) { alerts, medications ->
                val schedules = medications.flatMap { m -> m.schedules.map { it to m.medication } }.associateBy { it.first.id }
                alerts.mapNotNull { alert ->
                    val (schedule, medication) = schedules[alert.scheduleId] ?: return@mapNotNull null
                    RingingDose(
                        scheduleId = alert.scheduleId,
                        day = alert.scheduledDay,
                        name = listOf(medication.name, medication.strength).filter { it.isNotBlank() }.joinToString(" "),
                        detail = buildList {
                            add(Formats.quantity(context, schedule.quantity, medication.unit))
                            add(Formats.time(context, schedule.minuteOfDay))
                            if (medication.mealTiming != MealTiming.ANY) add(Formats.mealTiming(context, medication.mealTiming))
                        }.joinToString(" · "),
                        photo = medication.photoUri,
                    )
                }
            }
        }.collectAsState(initial = null)

        // Nothing left ringing: the alarm is done
        LaunchedEffect(doses) { if (doses?.isEmpty() == true) finish() }
        BackHandler { lifecycleScope.launch { doseActions.snoozeAllRinging(); finish() } }

        AlarmFrame(title = stringResource(R.string.alarm_title)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(doses.orEmpty(), key = { "${it.scheduleId}:${it.day}" }) { dose ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            // Seeing the box or strip is the surest check that it's the right medicine
                            MedicinePhoto(dose.photo, stringResource(R.string.cd_medicine_photo, dose.name), Modifier.fillMaxWidth().height(200.dp))
                            if (dose.photo != null) Spacer(Modifier.height(14.dp))
                            Text(dose.name, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                            Text(dose.detail, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { lifecycleScope.launch { doseActions.take(dose.scheduleId, dose.day) } },
                                colors = ButtonDefaults.buttonColors(containerColor = AlarmTakenButton, contentColor = Color.White),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().height(68.dp),
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.dose_sheet_take), style = MaterialTheme.typography.headlineSmall)
                            }
                            TextButton(
                                onClick = { lifecycleScope.launch { doseActions.skip(dose.scheduleId, dose.day) } },
                                modifier = Modifier.align(Alignment.CenterHorizontally).height(52.dp),
                            ) { Text(stringResource(R.string.dose_sheet_skip), color = Color.White, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if ((doses?.size ?: 0) > 1) {
                Button(
                    onClick = { lifecycleScope.launch { doseActions.takeAllRinging() } },
                    colors = ButtonDefaults.buttonColors(containerColor = AlarmTakenButton, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(stringResource(R.string.action_take_all), style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { lifecycleScope.launch { doseActions.snoozeAllRinging(); finish() } },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(2.dp, Color.White),
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.dose_sheet_snooze, Formats.number(context, snooze)), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    @Composable
    private fun AppointmentAlarm(appointmentId: Long) {
        val context = LocalContext.current
        val appointment by produceState<Appointment?>(null) { value = db.appointmentDao().getById(appointmentId) }
        BackHandler { notifier.cancelAppointment(appointmentId); finish() }
        AlarmFrame(title = stringResource(R.string.alarm_appointment_title)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                appointment?.let { a ->
                    val at = a.dateTime.toLocalDateTime()
                    Text(a.doctorName, color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    a.specialty?.let { Text(it, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge) }
                    Text(
                        Formats.shortDate(context, at.toLocalDate()) + ", " + Formats.time(context, minuteOf(at.toLocalTime())),
                        color = Color.White, style = MaterialTheme.typography.titleLarge,
                    )
                    a.hospital?.let { Text(it, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge) }
                    a.serialNumber?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.appt_serial_value, Formats.digits(context, it)), color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            appointment?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                OutlinedButton(
                    onClick = { unlockThen { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(2.dp, Color.White),
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_call_chamber), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { notifier.cancelAppointment(appointmentId); finish() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AlarmBackgroundBottom),
            ) { Text(stringResource(R.string.action_dismiss), style = MaterialTheme.typography.titleLarge) }
        }
    }

    @Composable
    private fun TestAlarm(onDone: () -> Unit) {
        AlarmFrame(title = stringResource(R.string.notif_test_title)) {
            Text(
                stringResource(R.string.alarm_test_body),
                color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AlarmBackgroundBottom),
            ) { Text(stringResource(R.string.action_dismiss), style = MaterialTheme.typography.titleLarge) }
        }
    }

    @Composable
    private fun AlarmFrame(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
        val context = LocalContext.current
        var now by remember { mutableStateOf(LocalTime.now()) }
        LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(15_000) } }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(AlarmBackgroundTop, AlarmBackgroundBottom)))
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                PulseRings(color = Color.White, size = 150.dp) {
                    Box(
                        Modifier.size(88.dp).background(Color.White.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp).ringing()) }
                }
                Spacer(Modifier.height(16.dp))
                Text(Formats.time(context, minuteOf(now)), color = Color.White, style = MaterialTheme.typography.displayMedium)
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                content()
            }
        }
    }

    companion object {
        private const val EXTRA_TEST = "test"
        private const val EXTRA_APPOINTMENT_ID = "appointmentId"

        fun testIntent(context: Context): Intent =
            Intent(context, AlarmActivity::class.java).putExtra(EXTRA_TEST, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun appointmentIntent(context: Context, appointmentId: Long): Intent =
            Intent(context, AlarmActivity::class.java).putExtra(EXTRA_APPOINTMENT_ID, appointmentId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
