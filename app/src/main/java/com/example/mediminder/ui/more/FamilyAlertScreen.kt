package com.example.mediminder.ui.more

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.settings.AppSettings
import com.example.mediminder.data.settings.FamilyAlerts
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.reminders.FamilyAlerter
import com.example.mediminder.ui.components.AnimatedArt
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.components.ChoiceChips
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.NoticeCard
import com.example.mediminder.ui.components.QuietButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.SwitchRow
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.util.AppLocale
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FamilyAlertViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.settings
    private val alerter = FamilyAlerter(context, settings)

    fun update(transform: (FamilyAlerts) -> FamilyAlerts) = settings.update { it.copy(family = transform(it.family)) }

    fun sendTest(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val sent = withContext(Dispatchers.IO) { alerter.sendTest() }
        onDone(sent)
    }
}

/** A text message to a family member when a medicine is not taken. */
@Composable
fun FamilyAlertScreen(onBack: () -> Unit, viewModel: FamilyAlertViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s by viewModel.state.collectAsState()
    val f = s.family
    val scope = rememberCoroutineScope()
    var problem by remember { mutableStateOf<Int?>(null) }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { viewModel.update { it.copy(on = true) }; problem = null } else problem = R.string.family_permission
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.family_title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedArt(Art.MESSAGE, Hue.TEAL, size = 96.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.family_intro), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            MediCard {
                SwitchRow(
                    title = stringResource(R.string.family_switch),
                    subtitle = if (f.on && f.phone.isNotBlank()) stringResource(R.string.family_on_sub, f.name.ifBlank { f.phone }) else null,
                    checked = f.on,
                    onChange = { on ->
                        when {
                            !on -> viewModel.update { it.copy(on = false) }
                            f.phone.isBlank() -> problem = R.string.family_need_phone
                            !granted() -> askPermission.launch(Manifest.permission.SEND_SMS)
                            else -> { viewModel.update { it.copy(on = true) }; problem = null }
                        }
                    },
                )
            }
            problem?.let { NoticeCard(stringResource(it), Icons.Default.Warning, Tone.WARNING) }

            SectionTitle(stringResource(R.string.family_who))
            OutlinedTextField(
                value = f.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v.take(40)) } },
                label = { Text(stringResource(R.string.family_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.phone,
                onValueChange = { v -> viewModel.update { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' || c == ' ' || c in '০'..'৯' }.take(20)) }; problem = null },
                label = { Text(stringResource(R.string.family_phone)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            val emergency = s.profile
            if (emergency.emergencyPhone.isNotBlank() && emergency.emergencyPhone != f.phone) {
                QuietButton(
                    stringResource(R.string.family_use_emergency, emergency.emergencyName.ifBlank { emergency.emergencyPhone }),
                    onClick = { viewModel.update { it.copy(name = emergency.emergencyName, phone = emergency.emergencyPhone) } },
                    icon = Icons.Default.ContactPhone,
                )
            }

            Text(stringResource(R.string.family_after), style = MaterialTheme.typography.titleMedium)
            ChoiceChips(listOf(30, 60, 120), f.afterMinutes, { afterLabel(it) }, { m -> viewModel.update { it.copy(afterMinutes = m) } })

            SectionTitle(stringResource(R.string.family_preview))
            MediCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                val ctx = AppLocale.localized(context)
                Text(
                    FamilyAlerter.missedText(ctx, s.userName, listOf(FamilyAlerter.Missed("", "Napa 500 mg", 8 * 60 + 15))),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (f.phone.isNotBlank() && granted()) {
                SecondaryButton(
                    stringResource(R.string.family_test),
                    onClick = {
                        viewModel.sendTest { sent ->
                            scope.launch {
                                if (sent) FeedbackBus.show(FeedbackKind.SAVED, R.string.feedback_family_test, f.name.ifBlank { f.phone })
                                else problem = R.string.family_test_failed
                            }
                        }
                    },
                    icon = Icons.Default.Send,
                )
            }
            NoticeCard(stringResource(R.string.family_cost), Icons.Default.Info, Tone.INFO)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun afterLabel(minutes: Int): String {
    val context = LocalContext.current
    return when {
        minutes < 60 -> stringResource(R.string.family_minutes, Formats.number(context, minutes))
        minutes == 60 -> stringResource(R.string.family_hour_one)
        else -> stringResource(R.string.family_hours, Formats.number(context, minutes / 60))
    }
}
