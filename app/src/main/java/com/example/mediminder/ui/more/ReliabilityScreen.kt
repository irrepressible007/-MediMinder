package com.example.mediminder.ui.more

import com.example.mediminder.ui.components.AnimatedArt
import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.theme.Hue
import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.layout.size
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.R
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.reminders.CheckResult
import com.example.mediminder.reminders.Reliability
import com.example.mediminder.reminders.ReliabilityCheck

/** "Will my alarms actually ring?" — every setting that can silently break them, with a fix button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilityScreen(onBack: () -> Unit, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var checks by remember { mutableStateOf(Reliability.checks(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checks = Reliability.checks(context) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks = Reliability.checks(context) }

    fun fix(result: CheckResult) {
        if (result.check == ReliabilityCheck.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val intent = result.fix ?: return
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Reliability.appDetails(context))
        } catch (_: SecurityException) {
            context.startActivity(Reliability.appDetails(context))
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.reliability_title), onBack = onBack)
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedArt(
                Art.SHIELD,
                if (checks.all { it.ok != false }) Hue.GREEN else Hue.AMBER,
                Modifier.align(Alignment.CenterHorizontally),
                size = 148.dp,
            )
            Text(stringResource(R.string.reliability_intro), style = MaterialTheme.typography.bodyLarge)
            checks.forEach { result -> CheckCard(result, onFix = { fix(result) }) }
            Text(stringResource(R.string.reliability_manufacturer, Reliability.manufacturer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.reliability_test_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.reliability_test_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.action_test_reminder), onClick = {
                    settingsViewModel.engine.scheduleTest(AlertType.REMINDER)
                    Toast.makeText(context, context.getString(R.string.test_scheduled), Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.action_test_alarm), onClick = {
                    settingsViewModel.engine.scheduleTest(AlertType.ALARM)
                    Toast.makeText(context, context.getString(R.string.test_scheduled), Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CheckCard(result: CheckResult, onFix: () -> Unit) {
    val (title, body) = when (result.check) {
        ReliabilityCheck.NOTIFICATIONS -> R.string.check_notifications to R.string.check_notifications_body
        ReliabilityCheck.EXACT_ALARMS -> R.string.check_exact to R.string.check_exact_body
        ReliabilityCheck.FULL_SCREEN -> R.string.check_full_screen to R.string.check_full_screen_body
        ReliabilityCheck.BATTERY -> R.string.check_battery to R.string.check_battery_body
        ReliabilityCheck.ALARM_CHANNEL -> R.string.check_channel to R.string.check_channel_body
        ReliabilityCheck.AUTOSTART -> R.string.check_autostart to R.string.check_autostart_body
    }
    val ok = result.ok
    val status = LocalStatusColors.current
    MediCard(
        containerColor = when (ok) {
            true -> MaterialTheme.colorScheme.surface
            false -> MaterialTheme.colorScheme.errorContainer
            null -> status.dueContainer
        },
        borderColor = when (ok) {
            true -> MaterialTheme.colorScheme.outlineVariant
            false -> MaterialTheme.colorScheme.error
            null -> status.due
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (ok) { true -> Icons.Default.CheckCircle; false -> Icons.Default.Error; null -> Icons.Default.Help },
                contentDescription = null,
                tint = when (ok) { true -> status.taken; false -> MaterialTheme.colorScheme.error; null -> status.due },
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (ok != true && result.fix != null) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(stringResource(if (ok == null) R.string.action_open else R.string.action_fix), onClick = onFix)
        }
    }
}
