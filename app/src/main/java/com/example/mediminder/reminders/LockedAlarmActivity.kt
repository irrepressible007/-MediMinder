package com.example.mediminder.reminders

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.mediminder.R
import com.example.mediminder.ui.theme.MediMinderTheme
import java.util.Date

/**
 * The alarm page for a dose that rang after a restart, before the phone was unlocked. It can't
 * read the app's data yet, so it shows what was saved for this dose and offers to unlock; once
 * unlocked, the app's own alarm page takes over with Taken / Skip / Snooze.
 */
class LockedAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val entry = LockedAlarms.entryFrom(intent)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        val now = DateFormat.getTimeFormat(this).format(Date())
        setContent {
            MediMinderTheme(darkTheme = true) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF13205A)).safeDrawingPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.weight(0.4f))
                    Text(now, style = MaterialTheme.typography.displayMedium, color = Color.White)
                    Text(stringResource(R.string.locked_alarm_title), style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
                    if (entry != null) {
                        Text(entry.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
                        Text(entry.detail, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                    }
                    Text(stringResource(R.string.locked_unlock_hint), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { unlockAndAnswer(notificationId) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E9E52), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    ) { Text(stringResource(R.string.locked_unlock), style = MaterialTheme.typography.titleLarge) }
                    OutlinedButton(
                        onClick = { stop(notificationId) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.locked_stop), color = Color.White, style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }

    /** Unlock; the app then rings this dose again with its usual answers. */
    private fun unlockAndAnswer(notificationId: Int) {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = stop(notificationId)
            })
        } else stop(notificationId)
    }

    /** Silences this ringing; the dose stays unanswered, so the app asks again after unlock. */
    private fun stop(notificationId: Int) {
        if (notificationId != 0) NotificationManagerCompat.from(this).cancel(notificationId)
        finish()
    }

    companion object {
        private const val EXTRA_NOTIFICATION = "notificationId"

        internal fun intent(context: Context, entry: LockedAlarms.Entry, notificationId: Int): Intent =
            Intent(context, LockedAlarmActivity::class.java)
                .putExtras(LockedAlarms.intent(context, entry))
                .putExtra(EXTRA_NOTIFICATION, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
