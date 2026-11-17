package com.example.mediminder.ui.more

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.mediminder.R
import com.example.mediminder.reminders.Reliability
import com.example.mediminder.reminders.ReliabilityCheck
import kotlinx.coroutines.launch

/** First run: language → name & meal times → permissions that make alarms reliable. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by viewModel.state.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var checks by remember { mutableStateOf(Reliability.checks(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checks = Reliability.checks(context) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks = Reliability.checks(context) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (step) {
                0 -> {
                    Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    }
                    Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = s.language == "bn", onClick = { viewModel.setLanguage("bn") }, label = { Text("বাংলা") })
                        FilterChip(selected = s.language == "en", onClick = { viewModel.setLanguage("en") }, label = { Text("English") })
                    }
                    OutlinedTextField(
                        value = s.userName,
                        onValueChange = { v -> viewModel.update { it.copy(userName = v.take(40)) } },
                        label = { Text(stringResource(R.string.settings_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                1 -> {
                    Text(stringResource(R.string.onboarding_meals_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.onboarding_meals_body), style = MaterialTheme.typography.bodyMedium)
                    MealRow(stringResource(R.string.meal_breakfast), s.mealTimes.breakfast) { m -> viewModel.update { it.copy(mealTimes = it.mealTimes.copy(breakfast = m)) } }
                    MealRow(stringResource(R.string.meal_lunch), s.mealTimes.lunch) { m -> viewModel.update { it.copy(mealTimes = it.mealTimes.copy(lunch = m)) } }
                    MealRow(stringResource(R.string.meal_evening), s.mealTimes.evening) { m -> viewModel.update { it.copy(mealTimes = it.mealTimes.copy(evening = m)) } }
                    MealRow(stringResource(R.string.meal_dinner), s.mealTimes.dinner) { m -> viewModel.update { it.copy(mealTimes = it.mealTimes.copy(dinner = m)) } }
                    MealRow(stringResource(R.string.meal_bedtime), s.mealTimes.bedtime) { m -> viewModel.update { it.copy(mealTimes = it.mealTimes.copy(bedtime = m)) } }
                }
                else -> {
                    Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.onboarding_permissions_body), style = MaterialTheme.typography.bodyMedium)
                    checks.filter { it.check != ReliabilityCheck.AUTOSTART && it.check != ReliabilityCheck.ALARM_CHANNEL }.forEach { result ->
                        val label = stringResource(
                            when (result.check) {
                                ReliabilityCheck.NOTIFICATIONS -> R.string.check_notifications
                                ReliabilityCheck.EXACT_ALARMS -> R.string.check_exact
                                ReliabilityCheck.FULL_SCREEN -> R.string.check_full_screen
                                else -> R.string.check_battery
                            }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text((if (result.ok == true) "✓ " else "• ") + label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            if (result.ok != true) {
                                TextButton(onClick = {
                                    if (result.check == ReliabilityCheck.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        result.fix?.let { runCatching { context.startActivity(it) } }
                                    }
                                }) { Text(stringResource(R.string.action_allow)) }
                            }
                        }
                    }
                    Text(stringResource(R.string.onboarding_permissions_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.action_back)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (step < 2) step++ else {
                    viewModel.update { it.copy(onboardingDone = true) }
                    scope.launch { viewModel.rearm() }
                    onFinished()
                }
            }) { Text(stringResource(if (step < 2) R.string.action_next else R.string.action_get_started)) }
        }
    }
}
