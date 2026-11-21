package com.example.mediminder.ui.components

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.mediminder.R
import com.example.mediminder.reminders.CheckResult
import com.example.mediminder.reminders.Reliability
import com.example.mediminder.reminders.ReliabilityCheck
import com.example.mediminder.reminders.whyItMatters
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.util.Formats

/*
 * Android lets people switch off what reminders need (notifications, exact alarms, full-screen
 * alarms). MediMinder asks the standard way: it says why, asks at the moment it matters, opens the
 * exact settings page in one tap, and accepts "not now" — it reminds again later, never forces.
 */

/**
 * Returns an action that fixes one setting: the system's own permission dialog for
 * notifications (Android 13+), otherwise the exact settings page. When Android no longer shows
 * its dialog (it was declined twice), the notification settings page opens instead.
 */
@Composable
fun rememberSetupFixer(onResult: () -> Unit = {}): (CheckResult) -> Unit {
    val context = LocalContext.current
    var askedAt by remember { mutableLongStateOf(0L) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // An instant "no" means Android did not show its dialog: offer the settings page
        if (!granted && SystemClock.elapsedRealtime() - askedAt < 500) Reliability.open(context, Reliability.appNotificationSettings(context))
        onResult()
    }
    return remember(notifications) {
        { result ->
            if (result.check == ReliabilityCheck.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askedAt = SystemClock.elapsedRealtime()
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Reliability.open(context, result.fix)
            }
        }
    }
}

/** Today's gentle card while a phone setting keeps reminders from ringing on time. */
@Composable
fun SetupCard(
    problems: List<CheckResult>,
    onFix: (CheckResult) -> Unit,
    onLater: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = problems.firstOrNull() ?: return
    val context = LocalContext.current
    val status = LocalStatusColors.current
    MediCard(
        modifier = modifier,
        onClick = onDetails,
        containerColor = status.dueContainer,
        borderColor = status.due.copy(alpha = 0.35f),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icons.Default.NotificationsActive, Hue.AMBER, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (problems.size == 1) stringResource(R.string.setup_card_title_one)
                    else stringResource(R.string.setup_card_title_many, Formats.number(context, problems.size)),
                    style = MaterialTheme.typography.titleMedium,
                    color = status.onDueContainer,
                )
                Text(stringResource(first.check.whyItMatters()), style = MaterialTheme.typography.bodyMedium, color = status.onDueContainer)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuietButton(stringResource(R.string.setup_later), onClick = onLater, color = status.onDueContainer)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onFix(first) },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = status.due, contentColor = MaterialTheme.colorScheme.surface),
            ) {
                Text(stringResource(R.string.action_allow), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Asked in context: choosing "Alarm" while a permission alarms need is off. */
@Composable
fun AlarmPermissionNote(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(Reliability.criticalProblems(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { missing = Reliability.criticalProblems(context) }
    val fix = rememberSetupFixer { missing = Reliability.criticalProblems(context) }
    val first = missing.firstOrNull() ?: return
    NoticeCard(
        text = stringResource(R.string.wiz_alarm_needs),
        icon = Icons.Default.Alarm,
        tone = Tone.WARNING,
        modifier = modifier,
        actionLabel = stringResource(R.string.action_allow),
        onClick = { fix(first) },
    )
}
