package com.example.mediminder.ui.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.size
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.R
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import com.example.mediminder.data.settings.MedicalProfile
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.ui.components.ChoiceChips
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.medicines.scheduleSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MedicalIdViewModel @Inject constructor(
    private val settings: SettingsRepository,
    medicines: MedicineRepository,
) : ViewModel() {
    val profile: MedicalProfile get() = settings.current.profile
    val name: String get() = settings.current.userName
    val medicines: StateFlow<List<MedicationWithSchedules>> = medicines.observeAll()
        .map { list -> list.filter { !it.medication.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(profile: MedicalProfile) = settings.update { it.copy(profile = profile) }
}

private val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

/** Health details for an emergency — shown to a doctor or shared with family. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalIdScreen(onBack: () -> Unit, viewModel: MedicalIdViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val start = viewModel.profile
    var blood by rememberSaveable { mutableStateOf(start.bloodGroup) }
    var allergies by rememberSaveable { mutableStateOf(start.allergies) }
    var conditions by rememberSaveable { mutableStateOf(start.conditions) }
    var contactName by rememberSaveable { mutableStateOf(start.emergencyName) }
    var contactPhone by rememberSaveable { mutableStateOf(start.emergencyPhone) }
    var birthYear by rememberSaveable { mutableStateOf(start.birthYear?.toString().orEmpty()) }
    val meds by viewModel.medicines.collectAsState()

    fun current() = MedicalProfile(blood, allergies.trim(), conditions.trim(), contactName.trim(), contactPhone.trim(), birthYear.toIntOrNull())

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.medical_id_title), onBack = { viewModel.save(current()); onBack() })
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.medical_id_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle(stringResource(R.string.medical_id_blood))
            ChoiceChips(BLOOD_GROUPS, blood.ifBlank { null }, { it }, { blood = if (blood == it) "" else it })
            OutlinedTextField(allergies, { allergies = it }, label = { Text(stringResource(R.string.medical_id_allergies)) }, placeholder = { Text(stringResource(R.string.medical_id_allergies_hint)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(conditions, { conditions = it }, label = { Text(stringResource(R.string.medical_id_conditions)) }, placeholder = { Text(stringResource(R.string.medical_id_conditions_hint)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                birthYear, { birthYear = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.medical_id_birth_year)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle(stringResource(R.string.medical_id_emergency))
            OutlinedTextField(contactName, { contactName = it }, label = { Text(stringResource(R.string.medical_id_contact_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    contactPhone, { contactPhone = it }, label = { Text(stringResource(R.string.medical_id_contact_phone)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f),
                )
                if (contactPhone.isNotBlank()) {
                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contactPhone"))) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Call, contentDescription = stringResource(R.string.action_call_short), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(stringResource(R.string.medical_id_allergy_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle(stringResource(R.string.medical_id_current_meds))
            MediCard {
                if (meds.isEmpty()) Text(stringResource(R.string.medical_id_no_meds), style = MaterialTheme.typography.bodyLarge)
                meds.forEach { item ->
                    Text(
                        "• ${listOf(item.medication.name, item.medication.strength).filter { it.isNotBlank() }.joinToString(" ")} — ${scheduleSummary(context, item)}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            PrimaryButton(stringResource(R.string.action_save), onClick = { viewModel.save(current()); onBack() })
            SecondaryButton(stringResource(R.string.medical_id_share), onClick = {
                viewModel.save(current())
                val text = medicalIdText(context, viewModel.name, current(), meds)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
            }, icon = Icons.Default.Share)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun medicalIdText(context: android.content.Context, name: String, p: MedicalProfile, meds: List<MedicationWithSchedules>): String = buildString {
    appendLine(context.getString(R.string.medical_id_share_header, name.ifBlank { "—" }))
    if (p.bloodGroup.isNotBlank()) appendLine(context.getString(R.string.medical_id_share_blood, p.bloodGroup))
    if (p.birthYear != null) appendLine(context.getString(R.string.medical_id_share_birth, p.birthYear.toString()))
    appendLine(context.getString(R.string.medical_id_share_allergies, p.allergies.ifBlank { context.getString(R.string.medical_id_none) }))
    if (p.conditions.isNotBlank()) appendLine(context.getString(R.string.medical_id_share_conditions, p.conditions))
    if (p.emergencyName.isNotBlank() || p.emergencyPhone.isNotBlank()) appendLine(context.getString(R.string.medical_id_share_contact, "${p.emergencyName} ${p.emergencyPhone}".trim()))
    if (meds.isNotEmpty()) {
        appendLine(context.getString(R.string.medical_id_current_meds) + ":")
        meds.forEach { appendLine("• ${listOf(it.medication.name, it.medication.strength).filter { s -> s.isNotBlank() }.joinToString(" ")} — ${scheduleSummary(context, it)}") }
    }
}
