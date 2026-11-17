package com.example.mediminder.ui.more

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.mediminder.R
import com.example.mediminder.reminders.Reliability
import com.example.mediminder.reminders.ReliabilityCheck
import com.example.mediminder.ui.components.ChoiceTile
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

/** First run: language & text size → meal times → permissions that make alarms reliable. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by viewModel.state.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var checks by remember { mutableStateOf(Reliability.checks(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checks = Reliability.checks(context) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks = Reliability.checks(context) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
        LinearProgressIndicator(
            progress = { (step + 1) / 3f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(20.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    Box(
                        Modifier.size(104.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp)) }
                    Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    SectionTitle(stringResource(R.string.settings_language))
                    ChoiceTile(title = "বাংলা", selected = s.language == "bn", onClick = { viewModel.setLanguage("bn") })
                    ChoiceTile(title = "English", selected = s.language == "en", onClick = { viewModel.setLanguage("en") })
                    SectionTitle(stringResource(R.string.settings_text_size))
                    TextSizeChooser(s.textScale) { scale -> viewModel.update { it.copy(textScale = scale) } }
                    OutlinedTextField(
                        value = s.userName,
                        onValueChange = { v -> viewModel.update { it.copy(userName = v.take(40)) } },
                        label = { Text(stringResource(R.string.settings_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                1 -> {
                    Text(stringResource(R.string.onboarding_meals_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.onboarding_meals_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MediCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        MealRow(stringResource(R.string.meal_breakfast), s.mealTimes.breakfast) { m -> viewModel.update { it.copy(mealTimes = it.mealTimes.copy(breakfast = m)) } }
                        MealRow(stringResource(R.string.meal_lunch), s.mealTimes.lunch) { m -> viewModel.update { it.copy(mealTimes = it.mealTimes.copy(lunch = m)) } }
                        MealRow(stringResource(R.string.meal_evening), s.mealTimes.evening) { m -> viewModel.update { it.copy(mealTimes = it.mealTimes.copy(evening = m)) } }
                        MealRow(stringResource(R.string.meal_dinner), s.mealTimes.dinner) { m -> viewModel.update { it.copy(mealTimes = it.mealTimes.copy(dinner = m)) } }
                        MealRow(stringResource(R.string.meal_bedtime), s.mealTimes.bedtime) { m -> viewModel.update { it.copy(mealTimes = it.mealTimes.copy(bedtime = m)) } }
                    }
                }
                else -> {
                    val status = LocalStatusColors.current
                    Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.onboarding_permissions_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MediCard {
                        val shown = checks.filter { it.check != ReliabilityCheck.AUTOSTART && it.check != ReliabilityCheck.ALARM_CHANNEL }
                        shown.forEachIndexed { i, result ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            val label = stringResource(
                                when (result.check) {
                                    ReliabilityCheck.NOTIFICATIONS -> R.string.check_notifications
                                    ReliabilityCheck.EXACT_ALARMS -> R.string.check_exact
                                    ReliabilityCheck.FULL_SCREEN -> R.string.check_full_screen
                                    else -> R.string.check_battery
                                }
                            )
                            val ok = result.ok == true
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (ok) status.taken else status.due,
                                    modifier = Modifier.size(30.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            }
                            if (!ok) {
                                Spacer(Modifier.height(8.dp))
                                SecondaryButton(stringResource(R.string.action_allow), onClick = {
                                    if (result.check == ReliabilityCheck.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        result.fix?.let { runCatching { context.startActivity(it) } }
                                    }
                                })
                            }
                        }
                    }
                    Text(stringResource(R.string.onboarding_permissions_later), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) SecondaryButton(stringResource(R.string.action_back), onClick = { step-- }, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = stringResource(if (step < 2) R.string.action_next else R.string.action_get_started),
                onClick = {
                    if (step < 2) step++ else {
                        viewModel.update { it.copy(onboardingDone = true) }
                        scope.launch { viewModel.rearm() }
                        onFinished()
                    }
                },
                modifier = Modifier.weight(1.6f),
            )
        }
    }
}
