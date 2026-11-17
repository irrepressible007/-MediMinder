package com.example.mediminder.ui.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.mediminder.R
import com.example.mediminder.data.AppointmentRepository
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.Doctor
import com.example.mediminder.domain.toEpochMillis
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.ui.components.ChoiceChips
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.components.TimePill
import com.example.mediminder.ui.components.showDatePicker
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class AppointmentForm(
    val loaded: Boolean = false,
    val id: Long = 0,
    val original: Appointment? = null,
    val doctorId: Long? = null,
    val doctorName: String = "",
    val specialty: String = "",
    val hospital: String = "",
    val address: String = "",
    val phone: String = "",
    val saveDoctor: Boolean = true,
    val date: LocalDate = LocalDate.now().plusDays(1),
    val minuteOfDay: Int = 17 * 60,
    val serial: String = "",
    val fee: String = "",
    val purpose: String = "",
    val notes: String = "",
    val reminderMinutes: Int = 120,
    val remindDayBefore: Boolean = true,
    val alertType: AlertType = AlertType.REMINDER,
    val done: Boolean = false,
)

@HiltViewModel
class AppointmentEditorViewModel @Inject constructor(private val repository: AppointmentRepository) : ViewModel() {
    private val _form = MutableStateFlow(AppointmentForm())
    val form: StateFlow<AppointmentForm> = _form.asStateFlow()
    val doctors: StateFlow<List<Doctor>> = repository.observeDoctors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(appointmentId: Long, doctorId: Long) {
        if (_form.value.loaded) return
        viewModelScope.launch {
            val existing = appointmentId.takeIf { it != 0L }?.let { repository.getAppointment(it) }
            if (existing != null) {
                val at = existing.dateTime.toLocalDateTime()
                _form.value = AppointmentForm(
                    loaded = true, id = existing.id, original = existing, doctorId = existing.doctorId,
                    doctorName = existing.doctorName, specialty = existing.specialty.orEmpty(), hospital = existing.hospital.orEmpty(),
                    address = existing.address.orEmpty(), phone = existing.phone.orEmpty(), saveDoctor = false,
                    date = at.toLocalDate(), minuteOfDay = at.hour * 60 + at.minute,
                    serial = existing.serialNumber.orEmpty(), fee = existing.fee?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty(),
                    purpose = existing.purpose.orEmpty(), notes = existing.notes.orEmpty(),
                    reminderMinutes = existing.reminderMinutesBefore, remindDayBefore = existing.remindDayBefore, alertType = existing.reminderAlertType,
                )
            } else {
                val doctor = doctorId.takeIf { it != 0L }?.let { repository.getDoctor(it) }
                _form.value = AppointmentForm(loaded = true).let { f -> if (doctor != null) f.withDoctor(doctor) else f }
            }
        }
    }

    private fun AppointmentForm.withDoctor(d: Doctor) = copy(
        doctorId = d.id, doctorName = d.name, specialty = d.specialty.orEmpty(), hospital = d.hospital.orEmpty(),
        address = d.address.orEmpty(), phone = d.phone.orEmpty(), saveDoctor = false,
    )

    fun pickDoctor(d: Doctor) = _form.update { it.withDoctor(d) }
    fun update(transform: (AppointmentForm) -> AppointmentForm) = _form.update(transform)

    fun save() {
        val f = _form.value
        if (f.doctorName.isBlank()) return
        viewModelScope.launch {
            var doctorId = f.doctorId
            if (doctorId == null && f.saveDoctor) {
                doctorId = repository.saveDoctor(
                    Doctor(name = f.doctorName.trim(), specialty = f.specialty.ifBlank { null }, hospital = f.hospital.ifBlank { null }, address = f.address.ifBlank { null }, phone = f.phone.ifBlank { null })
                )
            }
            val appointment = (f.original ?: Appointment(doctorName = f.doctorName, dateTime = 0)).copy(
                doctorId = doctorId,
                doctorName = f.doctorName.trim(),
                specialty = f.specialty.trim().ifBlank { null },
                hospital = f.hospital.trim().ifBlank { null },
                address = f.address.trim().ifBlank { null },
                phone = f.phone.trim().ifBlank { null },
                dateTime = f.date.atTime(LocalTime.of(f.minuteOfDay / 60, f.minuteOfDay % 60)).toEpochMillis(),
                serialNumber = f.serial.trim().ifBlank { null },
                fee = f.fee.toDoubleOrNull(),
                purpose = f.purpose.trim().ifBlank { null },
                notes = f.notes.trim().ifBlank { null },
                reminderMinutesBefore = f.reminderMinutes,
                remindDayBefore = f.remindDayBefore,
                reminderAlertType = f.alertType,
            )
            repository.save(appointment)
            _form.update { it.copy(done = true) }
        }
    }

    fun delete() = viewModelScope.launch {
        if (_form.value.id != 0L) repository.delete(_form.value.id)
        _form.update { it.copy(done = true) }
    }
}

private val REMINDER_OPTIONS = listOf(0, 30, 60, 120, 180, 24 * 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentEditorScreen(appointmentId: Long, doctorId: Long, onBack: () -> Unit, viewModel: AppointmentEditorViewModel = hiltViewModel()) {
    LaunchedEffect(appointmentId, doctorId) { viewModel.load(appointmentId, doctorId) }
    val f by viewModel.form.collectAsState()
    val doctors by viewModel.doctors.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(f.done) { if (f.done) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (appointmentId == 0L) R.string.appt_new else R.string.appt_edit), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
                actions = {
                    if (appointmentId != 0L) IconButton(onClick = viewModel::delete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete)) }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = viewModel::save,
                enabled = f.doctorName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp).imePadding(),
            ) { Text(stringResource(R.string.action_save_appointment)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(stringResource(R.string.appt_section_doctor))
            if (doctors.isNotEmpty() && f.id == 0L) {
                ChoiceChips(doctors, doctors.firstOrNull { it.id == f.doctorId }, { it.name }, viewModel::pickDoctor)
            }
            OutlinedTextField(f.doctorName, { v -> viewModel.update { it.copy(doctorName = v, doctorId = null) } }, label = { Text(stringResource(R.string.doctor_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.specialty, { v -> viewModel.update { it.copy(specialty = v) } }, label = { Text(stringResource(R.string.doctor_specialty)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.hospital, { v -> viewModel.update { it.copy(hospital = v) } }, label = { Text(stringResource(R.string.doctor_hospital)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.address, { v -> viewModel.update { it.copy(address = v) } }, label = { Text(stringResource(R.string.doctor_address)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                f.phone, { v -> viewModel.update { it.copy(phone = v) } }, label = { Text(stringResource(R.string.doctor_phone_serial)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            if (f.doctorId == null && f.id == 0L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(f.saveDoctor, { v -> viewModel.update { it.copy(saveDoctor = v) } })
                    Text(stringResource(R.string.appt_save_doctor))
                }
            }

            SectionTitle(stringResource(R.string.appt_section_when))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDatePicker(context, f.date, LocalDate.now().minusYears(1)) { d -> viewModel.update { it.copy(date = d) } } }) {
                    Text(Formats.date(context, f.date))
                }
                TimePill(f.minuteOfDay, onChange = { m -> viewModel.update { it.copy(minuteOfDay = m) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(f.serial, { v -> viewModel.update { it.copy(serial = v.take(10)) } }, label = { Text(stringResource(R.string.appt_serial)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    f.fee, { v -> viewModel.update { it.copy(fee = v.filter { c -> c.isDigit() || c == '.' }.take(7)) } },
                    label = { Text(stringResource(R.string.appt_fee)) }, prefix = { Text("৳ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(f.purpose, { v -> viewModel.update { it.copy(purpose = v) } }, label = { Text(stringResource(R.string.appt_purpose)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.notes, { v -> viewModel.update { it.copy(notes = v) } }, label = { Text(stringResource(R.string.appt_notes)) }, placeholder = { Text(stringResource(R.string.appt_notes_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth())

            SectionTitle(stringResource(R.string.appt_section_reminder))
            ChoiceChips(REMINDER_OPTIONS, f.reminderMinutes, { minutes ->
                when {
                    minutes == 0 -> stringResource(R.string.reminder_none)
                    minutes < 60 -> stringResource(R.string.reminder_minutes_before, Formats.number(context, minutes))
                    minutes < 24 * 60 -> stringResource(R.string.reminder_hours_before, Formats.number(context, minutes / 60))
                    else -> stringResource(R.string.reminder_day_before)
                }
            }, { m -> viewModel.update { it.copy(reminderMinutes = m) } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.appt_remind_evening), modifier = Modifier.weight(1f))
                Switch(f.remindDayBefore, { v -> viewModel.update { it.copy(remindDayBefore = v) } })
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(AlertType.REMINDER, AlertType.ALARM).forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = f.alertType == type,
                        onClick = { viewModel.update { it.copy(alertType = type) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        icon = { Icon(if (type == AlertType.ALARM) Icons.Default.Alarm else Icons.Default.Notifications, null) },
                    ) { Text(stringResource(if (type == AlertType.ALARM) R.string.alert_alarm else R.string.alert_reminder)) }
                }
            }
            Text(stringResource(R.string.appt_serial_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}
