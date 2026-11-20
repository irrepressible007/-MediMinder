package com.example.mediminder.ui.appointments

import com.example.mediminder.ui.components.Art
import androidx.compose.material.icons.filled.LocalHospital
import com.example.mediminder.ui.components.popOnEnter
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.theme.soft
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.mediminder.R
import com.example.mediminder.ui.components.rememberEntranceState
import com.example.mediminder.ui.components.entrance
import androidx.compose.foundation.lazy.itemsIndexed
import com.example.mediminder.data.AppointmentRepository
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.AppointmentStatus
import com.example.mediminder.data.local.entity.Doctor
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.ui.components.ConfirmDialog
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.ui.components.ExpandableSection
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.QuietButton
import com.example.mediminder.ui.components.ScreenHeader
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class AppointmentsViewModel @Inject constructor(private val repository: AppointmentRepository) : ViewModel() {
    val appointments: StateFlow<List<Appointment>?> = repository.observeAppointments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val doctors: StateFlow<List<Doctor>> = repository.observeDoctors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStatus(id: Long, status: AppointmentStatus) = viewModelScope.launch { repository.setStatus(id, status) }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
    fun saveDoctor(doctor: Doctor) = viewModelScope.launch { repository.saveDoctor(doctor) }
    fun deleteDoctor(doctor: Doctor) = viewModelScope.launch { repository.deleteDoctor(doctor) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(
    modifier: Modifier,
    onNew: (doctorId: Long) -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: AppointmentsViewModel = hiltViewModel(),
) {
    val all by viewModel.appointments.collectAsState()
    val doctors by viewModel.doctors.collectAsState()
    var editingDoctor by remember { mutableStateOf<Doctor?>(null) }
    var deleting by remember { mutableStateOf<Appointment?>(null) }
    var optionsFor by remember { mutableStateOf<Appointment?>(null) }

    editingDoctor?.let { d ->
        DoctorDialog(d, onDismiss = { editingDoctor = null }, onSave = viewModel::saveDoctor, onDelete = if (d.id != 0L) ({ viewModel.deleteDoctor(d) }) else null)
    }
    deleting?.let { a ->
        ConfirmDialog(
            title = stringResource(R.string.appt_delete_title),
            text = stringResource(R.string.appt_delete_body, a.doctorName),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.delete(a.id) },
            onDismiss = { deleting = null },
        )
    }
    optionsFor?.let { a ->
        ModalBottomSheet(onDismissRequest = { optionsFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(a.doctorName, style = MaterialTheme.typography.headlineSmall)
                SecondaryButton(stringResource(R.string.action_edit), onClick = { optionsFor = null; onEdit(a.id) }, icon = Icons.Default.Edit)
                if (a.status == AppointmentStatus.UPCOMING) {
                    SecondaryButton(stringResource(R.string.appt_mark_visited), onClick = { viewModel.setStatus(a.id, AppointmentStatus.DONE); optionsFor = null }, icon = Icons.Default.Check)
                    SecondaryButton(stringResource(R.string.action_cancel_appointment), onClick = { viewModel.setStatus(a.id, AppointmentStatus.CANCELLED); optionsFor = null }, icon = Icons.Default.Cancel, contentColor = MaterialTheme.colorScheme.onSurface)
                } else {
                    SecondaryButton(stringResource(R.string.action_reopen), onClick = { viewModel.setStatus(a.id, AppointmentStatus.UPCOMING); optionsFor = null }, icon = Icons.Default.Replay)
                }
                QuietButton(stringResource(R.string.action_delete), onClick = { deleting = a; optionsFor = null }, icon = Icons.Default.Delete, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }

    val list = all ?: return
    val now = System.currentTimeMillis()
    val upcoming = list.filter { it.status == AppointmentStatus.UPCOMING && it.dateTime >= now - 3 * 60 * 60_000 }.sortedBy { it.dateTime }
    val past = list.filter { it !in upcoming }.sortedByDescending { it.dateTime }

    val entrance = rememberEntranceState()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ScreenHeader(stringResource(R.string.appt_title), icon = Icons.Default.LocalHospital, hue = Hue.TEAL) }
            if (upcoming.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.CalendarMonth,
                        title = stringResource(R.string.appt_empty_title),
                        body = stringResource(R.string.appt_empty_body),
                        hue = Hue.TEAL,
                        art = Art.STETHOSCOPE,
                    )
                }
            } else {
                item { SectionTitle(stringResource(R.string.appt_next)) }
                item { VisitCard(upcoming.first(), highlight = true, onOpen = { onEdit(upcoming.first().id) }, onOptions = { optionsFor = upcoming.first() }, modifier = Modifier.entrance(entrance, 0)) }
                if (upcoming.size > 1) {
                    item { SectionTitle(stringResource(R.string.appt_later)) }
                    itemsIndexed(upcoming.drop(1), key = { _, a -> a.id }) { i, a ->
                        VisitCard(a, highlight = false, onOpen = { onEdit(a.id) }, onOptions = { optionsFor = a }, modifier = Modifier.animateItem().entrance(entrance, i + 1))
                    }
                }
            }

            item {
                ExpandableSection(
                    stringResource(R.string.doctors_title_count, doctors.size),
                    subtitle = stringResource(R.string.doctors_empty).takeIf { doctors.isEmpty() },
                ) {
                    doctors.forEach { d -> DoctorCard(d, onBook = { onNew(d.id) }, onEdit = { editingDoctor = d }) }
                    SecondaryButton(stringResource(R.string.action_add_doctor), onClick = { editingDoctor = Doctor(name = "") }, icon = Icons.Default.Add)
                }
            }
            if (past.isNotEmpty()) item {
                ExpandableSection(stringResource(R.string.appt_past_title, past.size)) {
                    past.forEach { a -> VisitCard(a, highlight = false, onOpen = { onEdit(a.id) }, onOptions = { optionsFor = a }) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onNew(0) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 64.dp).popOnEnter(),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.action_new_appointment), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun dial(context: Context, phone: String) = context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))

@Composable
private fun VisitCard(a: Appointment, highlight: Boolean, onOpen: () -> Unit, onOptions: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val at = a.dateTime.toLocalDateTime()
    val upcoming = a.status == AppointmentStatus.UPCOMING
    MediCard(
        onClick = onOpen,
        modifier = modifier,
        containerColor = if (upcoming) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        borderWidth = if (highlight) 2.dp else 1.dp,
    ) {
        Row {
            // Calendar-page date block
            Column(
                Modifier
                    .widthIn(min = 72.dp)
                    .background(if (upcoming) Hue.TEAL.soft else MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Formats.number(context, at.dayOfMonth), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(at.format(DateTimeFormatter.ofPattern("MMM", Formats.locale(context))), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.doctorName, style = MaterialTheme.typography.titleLarge)
                listOfNotNull(a.specialty, a.hospital).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    Formats.shortDate(context, at.toLocalDate()) + ", " + Formats.time(context, minuteOf(at.toLocalTime())),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                )
                a.serialNumber?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.appt_serial_value, Formats.digits(context, it)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                when (a.status) {
                    AppointmentStatus.DONE -> Text(stringResource(R.string.appt_status_done), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppointmentStatus.CANCELLED -> Text(stringResource(R.string.appt_status_cancelled), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    AppointmentStatus.UPCOMING -> Unit
                }
            }
        }
        val phone = a.phone?.takeIf { it.isNotBlank() }
        val place = listOfNotNull(a.hospital, a.address).filter { it.isNotBlank() }.joinToString(", ")
        if (upcoming && (phone != null || place.isNotEmpty())) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (phone != null) SecondaryButton(stringResource(R.string.action_call_short), onClick = { dial(context, phone) }, icon = Icons.Default.Call, modifier = Modifier.weight(1f))
                if (place.isNotEmpty()) SecondaryButton(
                    stringResource(R.string.action_map),
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))) },
                    icon = Icons.Default.Map,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        QuietButton(stringResource(R.string.wiz_more), onClick = onOptions, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun DoctorCard(doctor: Doctor, onBook: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    MediCard(onClick = onEdit) {
        Text(doctor.name, style = MaterialTheme.typography.titleLarge)
        listOfNotNull(doctor.specialty, doctor.hospital, doctor.visitingHours).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            doctor.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                SecondaryButton(stringResource(R.string.action_call_short), onClick = { dial(context, phone) }, icon = Icons.Default.Call, modifier = Modifier.weight(1f))
            }
            SecondaryButton(stringResource(R.string.action_book_visit), onClick = onBook, icon = Icons.Default.CalendarMonth, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun DoctorDialog(doctor: Doctor, onDismiss: () -> Unit, onSave: (Doctor) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(doctor.name) }
    var specialty by rememberSaveable { mutableStateOf(doctor.specialty.orEmpty()) }
    var hospital by rememberSaveable { mutableStateOf(doctor.hospital.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(doctor.address.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(doctor.phone.orEmpty()) }
    var hours by rememberSaveable { mutableStateOf(doctor.visitingHours.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (doctor.id == 0L) R.string.doctor_new else R.string.doctor_edit), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.doctor_name)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.doctor_phone)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(hospital, { hospital = it }, label = { Text(stringResource(R.string.doctor_hospital)) }, singleLine = true)
                OutlinedTextField(specialty, { specialty = it }, label = { Text(stringResource(R.string.doctor_specialty)) }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.doctor_address)) }, singleLine = true)
                OutlinedTextField(hours, { hours = it }, label = { Text(stringResource(R.string.doctor_hours)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp), onClick = {
                onSave(doctor.copy(
                    name = name.trim(), specialty = specialty.trim().ifBlank { null }, hospital = hospital.trim().ifBlank { null },
                    address = address.trim().ifBlank { null }, phone = phone.trim().ifBlank { null }, visitingHours = hours.trim().ifBlank { null },
                ))
                onDismiss()
            }) { Text(stringResource(R.string.action_save), style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge) }
            }
        },
    )
}
