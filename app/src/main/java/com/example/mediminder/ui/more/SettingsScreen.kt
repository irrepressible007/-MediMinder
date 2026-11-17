package com.example.mediminder.ui.more

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
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
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.ChoiceChips
import com.example.mediminder.ui.components.ChoiceTile
import com.example.mediminder.ui.components.ConfirmDialog
import com.example.mediminder.ui.components.ExpandableSection
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.QuietButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.Stepper
import com.example.mediminder.ui.components.SwitchRow
import com.example.mediminder.ui.components.ValueRow
import com.example.mediminder.ui.components.showTimePicker
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

val TEXT_SCALES = listOf(1f, 1.15f, 1.3f)

@Composable
fun textScaleLabel(scale: Float): String = stringResource(
    when {
        scale >= 1.3f -> R.string.text_size_huge
        scale >= 1.15f -> R.string.text_size_large
        else -> R.string.text_size_normal
    }
)

/** Three big choices; the whole app re-renders at the new size as soon as one is tapped. */
@Composable
fun TextSizeChooser(current: Float, onPick: (Float) -> Unit) {
    TEXT_SCALES.forEach { scale ->
        ChoiceTile(
            title = textScaleLabel(scale),
            selected = kotlin.math.abs(current - scale) < 0.01f,
            onClick = { onPick(scale) },
            badge = "Aa",
        )
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenReliability: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val s by viewModel.state.collectAsState()
    var mealsChanged by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val counts = viewModel.backup.exportTo(uri)
                Toast.makeText(context, resources.getString(R.string.backup_done, Formats.number(context, counts.medicines), Formats.number(context, counts.doses)), Toast.LENGTH_LONG).show()
            } catch (e: BackupException) {
                Toast.makeText(context, resources.getString(e.reason), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, resources.getString(R.string.backup_failed), Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    restoreUri?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.restore_title),
            text = stringResource(R.string.restore_body),
            confirmLabel = stringResource(R.string.action_restore),
            destructive = true,
            onConfirm = {
                scope.launch {
                    try {
                        val counts = viewModel.backup.importFrom(uri)
                        Toast.makeText(context, resources.getString(R.string.restore_done, Formats.number(context, counts.medicines), Formats.number(context, counts.appointments)), Toast.LENGTH_LONG).show()
                    } catch (e: BackupException) {
                        Toast.makeText(context, resources.getString(e.reason), Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, resources.getString(R.string.restore_failed), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { restoreUri = null },
        )
    }

    fun updateMeals(transform: (MealTimes) -> MealTimes) {
        viewModel.update { it.copy(mealTimes = transform(it.mealTimes)) }
        mealsChanged = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Reading ──
            SectionTitle(stringResource(R.string.settings_text_size))
            TextSizeChooser(s.textScale) { scale -> viewModel.update { it.copy(textScale = scale) } }
            Text(stringResource(R.string.text_size_preview), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

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

            // ── Meal times ──
            SectionTitle(stringResource(R.string.settings_meal_times))
            Text(stringResource(R.string.settings_meal_times_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MediCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                MealRow(stringResource(R.string.meal_breakfast), s.mealTimes.breakfast) { m -> updateMeals { it.copy(breakfast = m) } }
                MealRow(stringResource(R.string.meal_lunch), s.mealTimes.lunch) { m -> updateMeals { it.copy(lunch = m) } }
                MealRow(stringResource(R.string.meal_evening), s.mealTimes.evening) { m -> updateMeals { it.copy(evening = m) } }
                MealRow(stringResource(R.string.meal_dinner), s.mealTimes.dinner) { m -> updateMeals { it.copy(dinner = m) } }
                MealRow(stringResource(R.string.meal_bedtime), s.mealTimes.bedtime) { m -> updateMeals { it.copy(bedtime = m) } }
            }
            if (mealsChanged) {
                PrimaryButton(stringResource(R.string.settings_apply_meal_times), onClick = {
                    scope.launch {
                        val n = viewModel.applyMealTimes()
                        Toast.makeText(context, resources.getString(R.string.settings_meal_applied, Formats.number(context, n)), Toast.LENGTH_SHORT).show()
                        mealsChanged = false
                    }
                })
            }
            ExpandableSection(stringResource(R.string.settings_meal_offsets)) {
                LabeledStepper(stringResource(R.string.settings_before_meal), stringResource(R.string.minutes_short, Formats.number(context, s.beforeMealMinutes)),
                    onMinus = { viewModel.update { it.copy(beforeMealMinutes = (it.beforeMealMinutes - 5).coerceAtLeast(5)) }; mealsChanged = true },
                    onPlus = { viewModel.update { it.copy(beforeMealMinutes = (it.beforeMealMinutes + 5).coerceAtMost(90)) }; mealsChanged = true })
                LabeledStepper(stringResource(R.string.settings_after_meal), stringResource(R.string.minutes_short, Formats.number(context, s.afterMealMinutes)),
                    onMinus = { viewModel.update { it.copy(afterMealMinutes = (it.afterMealMinutes - 5).coerceAtLeast(0)) }; mealsChanged = true },
                    onPlus = { viewModel.update { it.copy(afterMealMinutes = (it.afterMealMinutes + 5).coerceAtMost(90)) }; mealsChanged = true })
            }

            // ── Reminders & alarms ──
            SectionTitle(stringResource(R.string.settings_reminders))
            Text(stringResource(R.string.settings_default_alert), style = MaterialTheme.typography.bodyLarge)
            listOf(AlertType.REMINDER, AlertType.ALARM).forEach { type ->
                ChoiceTile(
                    title = stringResource(if (type == AlertType.ALARM) R.string.wiz_alert_alarm else R.string.wiz_alert_reminder),
                    subtitle = stringResource(if (type == AlertType.ALARM) R.string.wiz_alert_alarm_sub else R.string.wiz_alert_reminder_sub),
                    icon = if (type == AlertType.ALARM) Icons.Default.Alarm else Icons.Default.Notifications,
                    selected = s.defaultAlertType == type,
                    onClick = { viewModel.update { it.copy(defaultAlertType = type) } },
                )
            }
            Text(stringResource(R.string.settings_snooze), style = MaterialTheme.typography.bodyLarge)
            ChoiceChips(listOf(5, 10, 15, 30), s.snoozeMinutes, { stringResource(R.string.minutes_short, Formats.number(context, it)) }, { m -> viewModel.update { it.copy(snoozeMinutes = m) } })
            Text(stringResource(R.string.settings_test), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.action_test_reminder), onClick = {
                    viewModel.engine.scheduleTest(AlertType.REMINDER)
                    Toast.makeText(context, resources.getString(R.string.test_scheduled), Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.action_test_alarm), onClick = {
                    viewModel.engine.scheduleTest(AlertType.ALARM)
                    Toast.makeText(context, resources.getString(R.string.test_scheduled), Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f))
            }
            QuietButton(stringResource(R.string.reliability_title), onClick = onOpenReliability, icon = Icons.Default.VerifiedUser)
            ExpandableSection(stringResource(R.string.settings_advanced)) {
                SwitchRow(stringResource(R.string.settings_repeat), s.repeatReminders, { v -> viewModel.update { it.copy(repeatReminders = v) } }, subtitle = stringResource(R.string.settings_repeat_hint))
                if (s.repeatReminders) {
                    LabeledStepper(stringResource(R.string.settings_repeat_every), stringResource(R.string.minutes_short, Formats.number(context, s.repeatIntervalMinutes)),
                        onMinus = { viewModel.update { it.copy(repeatIntervalMinutes = (it.repeatIntervalMinutes - 5).coerceAtLeast(5)) } },
                        onPlus = { viewModel.update { it.copy(repeatIntervalMinutes = (it.repeatIntervalMinutes + 5).coerceAtMost(60)) } })
                    LabeledStepper(stringResource(R.string.settings_repeat_times), Formats.number(context, s.maxRepeats),
                        onMinus = { viewModel.update { it.copy(maxRepeats = (it.maxRepeats - 1).coerceAtLeast(1)) } },
                        onPlus = { viewModel.update { it.copy(maxRepeats = (it.maxRepeats + 1).coerceAtMost(10)) } })
                }
                LabeledStepper(stringResource(R.string.settings_ring_minutes), stringResource(R.string.minutes_short, Formats.number(context, s.alarmRingMinutes)),
                    onMinus = { viewModel.update { it.copy(alarmRingMinutes = (it.alarmRingMinutes - 1).coerceAtLeast(1)) } },
                    onPlus = { viewModel.update { it.copy(alarmRingMinutes = (it.alarmRingMinutes + 1).coerceAtMost(15)) } })
                ValueRow(stringResource(R.string.settings_refill_check_time), Formats.time(context, s.refillCheckMinute), onClick = {
                    showTimePicker(context, s.refillCheckMinute) { m ->
                        viewModel.update { it.copy(refillCheckMinute = m) }
                        scope.launch { viewModel.rearm() }
                    }
                })
                LabeledStepper(stringResource(R.string.settings_refill_days), Formats.digits(context, context.resources.getQuantityString(R.plurals.days_count, s.refillBuyDays, s.refillBuyDays)),
                    onMinus = { viewModel.update { it.copy(refillBuyDays = (it.refillBuyDays - 5).coerceAtLeast(5)) } },
                    onPlus = { viewModel.update { it.copy(refillBuyDays = (it.refillBuyDays + 5).coerceAtMost(90)) } })
            }

            // ── Backup ──
            SectionTitle(stringResource(R.string.settings_backup))
            Text(
                if (s.lastBackupAt > 0) stringResource(R.string.backup_last, Formats.date(context, java.time.Instant.ofEpochMilli(s.lastBackupAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()))
                else stringResource(R.string.backup_never),
                style = MaterialTheme.typography.bodyLarge,
            )
            SecondaryButton(stringResource(R.string.action_backup), onClick = { exportLauncher.launch("mediminder-backup-${LocalDate.now()}.json") }, icon = Icons.Default.CloudUpload)
            SecondaryButton(stringResource(R.string.action_restore), onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, icon = Icons.Default.Restore)
            Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.settings_about, Formats.digits(context, BuildConfig.VERSION_NAME)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.settings_disclaimer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LabeledStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Stepper(value, onMinus = onMinus, onPlus = onPlus)
    }
}

/** "Breakfast ............ 8:00 AM ›" — tap to change. */
@Composable
fun MealRow(label: String, minute: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    ValueRow(label, Formats.time(context, minute), onClick = { showTimePicker(context, minute, onChange) })
}
