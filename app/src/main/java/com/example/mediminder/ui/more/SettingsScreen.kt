package com.example.mediminder.ui.more

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.example.mediminder.BuildConfig
import com.example.mediminder.R
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.backup.BackupException
import com.example.mediminder.data.backup.BackupManager
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.settings.AppSettings
import com.example.mediminder.data.settings.MealTimes
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.reminders.ReminderEngine
import com.example.mediminder.ui.components.ChoiceChips
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.Stepper
import com.example.mediminder.ui.components.TimePill
import com.example.mediminder.util.AppLocale
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val medicines: MedicineRepository,
    val backup: BackupManager,
    val engine: ReminderEngine,
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.settings

    fun update(transform: (AppSettings) -> AppSettings) = settings.update(transform)

    fun setLanguage(language: String) {
        settings.update { it.copy(language = language) }
        AppLocale.apply(language) // recreates the activity in the new language
    }

    suspend fun applyMealTimes(): Int = medicines.applyMealTimes()

    /** Re-arm after a settings change that affects alarms (refill check time). */
    suspend fun rearm() = engine.rescheduleAll()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenReliability: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by viewModel.state.collectAsState()
    var mealsChanged by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val counts = viewModel.backup.exportTo(uri)
                Toast.makeText(context, context.getString(R.string.backup_done, counts.medicines, counts.doses), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: context.getString(R.string.backup_failed), Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(stringResource(R.string.restore_title)) },
            text = { Text(stringResource(R.string.restore_body)) },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    scope.launch {
                        try {
                            val counts = viewModel.backup.importFrom(uri)
                            Toast.makeText(context, context.getString(R.string.restore_done, counts.medicines, counts.appointments), Toast.LENGTH_LONG).show()
                        } catch (e: BackupException) {
                            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.restore_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.action_restore), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    fun updateMeals(transform: (MealTimes) -> MealTimes) {
        viewModel.update { it.copy(mealTimes = transform(it.mealTimes)) }
        mealsChanged = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── You ──
            SectionTitle(stringResource(R.string.settings_language))
            ChoiceChips(listOf("", "bn", "en"), s.language, { lang ->
                when (lang) { "bn" -> "বাংলা"; "en" -> "English"; else -> stringResource(R.string.settings_language_system) }
            }, viewModel::setLanguage)
            OutlinedTextField(
                value = s.userName,
                onValueChange = { v -> viewModel.update { it.copy(userName = v.take(40)) } },
                label = { Text(stringResource(R.string.settings_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Meal times ──
            SectionTitle(stringResource(R.string.settings_meal_times))
            Text(stringResource(R.string.settings_meal_times_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MealRow(stringResource(R.string.meal_breakfast), s.mealTimes.breakfast) { m -> updateMeals { it.copy(breakfast = m) } }
            MealRow(stringResource(R.string.meal_lunch), s.mealTimes.lunch) { m -> updateMeals { it.copy(lunch = m) } }
            MealRow(stringResource(R.string.meal_evening), s.mealTimes.evening) { m -> updateMeals { it.copy(evening = m) } }
            MealRow(stringResource(R.string.meal_dinner), s.mealTimes.dinner) { m -> updateMeals { it.copy(dinner = m) } }
            MealRow(stringResource(R.string.meal_bedtime), s.mealTimes.bedtime) { m -> updateMeals { it.copy(bedtime = m) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_before_meal), Modifier.weight(1f))
                Stepper(stringResource(R.string.minutes_short, Formats.number(context, s.beforeMealMinutes)),
                    onMinus = { viewModel.update { it.copy(beforeMealMinutes = (it.beforeMealMinutes - 5).coerceAtLeast(5)) }; mealsChanged = true },
                    onPlus = { viewModel.update { it.copy(beforeMealMinutes = (it.beforeMealMinutes + 5).coerceAtMost(90)) }; mealsChanged = true })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_after_meal), Modifier.weight(1f))
                Stepper(stringResource(R.string.minutes_short, Formats.number(context, s.afterMealMinutes)),
                    onMinus = { viewModel.update { it.copy(afterMealMinutes = (it.afterMealMinutes - 5).coerceAtLeast(0)) }; mealsChanged = true },
                    onPlus = { viewModel.update { it.copy(afterMealMinutes = (it.afterMealMinutes + 5).coerceAtMost(90)) }; mealsChanged = true })
            }
            if (mealsChanged) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val n = viewModel.applyMealTimes()
                        Toast.makeText(context, context.getString(R.string.settings_meal_applied, n), Toast.LENGTH_SHORT).show()
                        mealsChanged = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_apply_meal_times)) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Reminders & alarms ──
            SectionTitle(stringResource(R.string.settings_reminders))
            Text(stringResource(R.string.settings_default_alert), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(AlertType.REMINDER, AlertType.ALARM).forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = s.defaultAlertType == type,
                        onClick = { viewModel.update { it.copy(defaultAlertType = type) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        icon = { Icon(if (type == AlertType.ALARM) Icons.Default.Alarm else Icons.Default.Notifications, null) },
                    ) { Text(stringResource(if (type == AlertType.ALARM) R.string.alert_alarm else R.string.alert_reminder)) }
                }
            }
            Text(stringResource(R.string.settings_snooze), style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(5, 10, 15, 30), s.snoozeMinutes, { stringResource(R.string.minutes_short, Formats.number(context, it)) }, { m -> viewModel.update { it.copy(snoozeMinutes = m) } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_repeat))
                    Text(stringResource(R.string.settings_repeat_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(s.repeatReminders, { v -> viewModel.update { it.copy(repeatReminders = v) } })
            }
            if (s.repeatReminders) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_repeat_every), Modifier.weight(1f))
                    Stepper(stringResource(R.string.minutes_short, Formats.number(context, s.repeatIntervalMinutes)),
                        onMinus = { viewModel.update { it.copy(repeatIntervalMinutes = (it.repeatIntervalMinutes - 5).coerceAtLeast(5)) } },
                        onPlus = { viewModel.update { it.copy(repeatIntervalMinutes = (it.repeatIntervalMinutes + 5).coerceAtMost(60)) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_repeat_times), Modifier.weight(1f))
                    Stepper(Formats.number(context, s.maxRepeats),
                        onMinus = { viewModel.update { it.copy(maxRepeats = (it.maxRepeats - 1).coerceAtLeast(1)) } },
                        onPlus = { viewModel.update { it.copy(maxRepeats = (it.maxRepeats + 1).coerceAtMost(10)) } })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_ring_minutes), Modifier.weight(1f))
                Stepper(stringResource(R.string.minutes_short, Formats.number(context, s.alarmRingMinutes)),
                    onMinus = { viewModel.update { it.copy(alarmRingMinutes = (it.alarmRingMinutes - 1).coerceAtLeast(1)) } },
                    onPlus = { viewModel.update { it.copy(alarmRingMinutes = (it.alarmRingMinutes + 1).coerceAtMost(15)) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    viewModel.engine.scheduleTest(AlertType.REMINDER)
                    Toast.makeText(context, context.getString(R.string.test_scheduled), Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_test_reminder)) }
                OutlinedButton(onClick = {
                    viewModel.engine.scheduleTest(AlertType.ALARM)
                    Toast.makeText(context, context.getString(R.string.test_scheduled), Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_test_alarm)) }
            }
            TextButton(onClick = onOpenReliability) { Text(stringResource(R.string.reliability_title)) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Refills ──
            SectionTitle(stringResource(R.string.settings_refills))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_refill_check_time), Modifier.weight(1f))
                TimePill(s.refillCheckMinute, onChange = { m ->
                    viewModel.update { it.copy(refillCheckMinute = m) }
                    scope.launch { viewModel.rearm() }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_refill_days), Modifier.weight(1f))
                Stepper(context.resources.getQuantityString(R.plurals.days_count, s.refillBuyDays, s.refillBuyDays),
                    onMinus = { viewModel.update { it.copy(refillBuyDays = (it.refillBuyDays - 5).coerceAtLeast(5)) } },
                    onPlus = { viewModel.update { it.copy(refillBuyDays = (it.refillBuyDays + 5).coerceAtMost(90)) } })
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ── Backup ──
            SectionTitle(stringResource(R.string.settings_backup))
            Text(
                if (s.lastBackupAt > 0) stringResource(R.string.backup_last, Formats.date(context, java.time.Instant.ofEpochMilli(s.lastBackupAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()))
                else stringResource(R.string.backup_never),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("mediminder-backup-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_backup)) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_restore)) }
            }
            Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.settings_about, Formats.digits(context, BuildConfig.VERSION_NAME)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.settings_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun MealRow(label: String, minute: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TimePill(minute, onChange = onChange)
    }
}
