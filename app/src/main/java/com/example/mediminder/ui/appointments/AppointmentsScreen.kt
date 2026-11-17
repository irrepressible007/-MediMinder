package com.example.mediminder.ui.appointments

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.AppointmentRepository
import com.example.mediminder.data.local.entity.Appointment
import com.example.mediminder.data.local.entity.AppointmentStatus
import com.example.mediminder.data.local.entity.Doctor
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.ui.components.ConfirmDialog
import com.example.mediminder.ui.components.EmptyState
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
    var showPast by rememberSaveable { mutableStateOf(false) }
    var editingDoctor by remember { mutableStateOf<Doctor?>(null) }
    var deleting by remember { mutableStateOf<Appointment?>(null) }

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

    val list = all ?: return
    val now = System.currentTimeMillis()
    val upcoming = list.filter { it.status == AppointmentStatus.UPCOMING && it.dateTime >= now - 3 * 60 * 60_000 }.sortedBy { it.dateTime }
    val past = list.filter { it !in upcoming }.sortedByDescending { it.dateTime }

    Box(modifier.fillMaxSize()) {
        Column {
            TopAppBar(title = { Text(stringResource(R.string.tab_appointments), fontWeight = FontWeight.Bold) })
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SectionTitle(stringResource(R.string.doctors_title)) {
                        TextButton(onClick = { editingDoctor = Doctor(name = "") }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(stringResource(R.string.action_add_doctor))
                        }
                    }
                    if (doctors.isEmpty()) {
                        Text(stringResource(R.string.doctors_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(doctors, key = { it.id }) { d -> DoctorChip(d, onBook = { onNew(d.id) }, onEdit = { editingDoctor = d }) }
                        }
                    }
                }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        SegmentedButton(!showPast, { showPast = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.appt_upcoming, upcoming.size)) }
                        SegmentedButton(showPast, { showPast = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.appt_past, past.size)) }
                    }
                }
                val shown = if (showPast) past else upcoming
                if (shown.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.CalendarMonth,
                            title = stringResource(if (showPast) R.string.appt_empty_past else R.string.appt_empty_title),
                            body = stringResource(R.string.appt_empty_body),
                        )
                    }
                }
                items(shown, key = { it.id }) { a ->
                    AppointmentCard(
                        a,
                        onEdit = { onEdit(a.id) },
                        onDone = { viewModel.setStatus(a.id, AppointmentStatus.DONE) },
                        onCancel = { viewModel.setStatus(a.id, AppointmentStatus.CANCELLED) },
                        onReopen = { viewModel.setStatus(a.id, AppointmentStatus.UPCOMING) },
                        onDelete = { deleting = a },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onNew(0) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            // Static label (no expand/collapse animation)
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_new_appointment))
        }
    }
}

@Composable
private fun DoctorChip(doctor: Doctor, onBook: () -> Unit, onEdit: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.width(210.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(doctor.name, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit), modifier = Modifier.size(18.dp)) }
            }
            listOfNotNull(doctor.specialty, doctor.hospital).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Row {
                doctor.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                    val context = LocalContext.current
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_call_short))
                    }
                }
                TextButton(onClick = onBook) { Text(stringResource(R.string.action_book)) }
            }
        }
    }
}

@Composable
private fun AppointmentCard(
    a: Appointment,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val at = a.dateTime.toLocalDateTime()
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (a.status == AppointmentStatus.UPCOMING) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = if (a.status == AppointmentStatus.UPCOMING) 2.dp else 0.dp),
    ) {
        Row(Modifier.padding(14.dp)) {
            // Date block
            Column(
                Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Formats.number(context, at.dayOfMonth), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(at.format(DateTimeFormatter.ofPattern("MMM", Formats.locale(context))), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.doctorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                listOfNotNull(a.specialty, a.hospital).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    Formats.shortDate(context, at.toLocalDate()) + ", " + Formats.time(context, minuteOf(at.toLocalTime())),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
                )
                val extras = buildList {
                    a.serialNumber?.takeIf { it.isNotBlank() }?.let { add(context.getString(R.string.appt_serial_value, Formats.digits(context, it))) }
                    a.fee?.let { add(context.getString(R.string.appt_fee_value, Formats.taka(context, it))) }
                    if (a.status == AppointmentStatus.DONE) add(context.getString(R.string.appt_status_done))
                    if (a.status == AppointmentStatus.CANCELLED) add(context.getString(R.string.appt_status_cancelled))
                }
                if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    a.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                        AssistChip(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }, label = { Text(stringResource(R.string.action_call_short)) }, leadingIcon = { Icon(Icons.Default.Call, null, Modifier.size(16.dp)) })
                    }
                    val place = listOfNotNull(a.hospital, a.address).filter { it.isNotBlank() }.joinToString(", ")
                    if (place.isNotEmpty()) {
                        AssistChip(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))))
                        }, label = { Text(stringResource(R.string.action_map)) }, leadingIcon = { Icon(Icons.Default.Map, null, Modifier.size(16.dp)) })
                    }
                    if (a.status == AppointmentStatus.UPCOMING) {
                        AssistChip(onClick = onDone, label = { Text(stringResource(R.string.action_done)) }, leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) })
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onEdit() })
                    if (a.status == AppointmentStatus.UPCOMING) DropdownMenuItem(text = { Text(stringResource(R.string.action_cancel_appointment)) }, onClick = { menu = false; onCancel() })
                    else DropdownMenuItem(text = { Text(stringResource(R.string.action_reopen)) }, onClick = { menu = false; onReopen() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
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
        title = { Text(stringResource(if (doctor.id == 0L) R.string.doctor_new else R.string.doctor_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.doctor_name)) }, singleLine = true)
                OutlinedTextField(specialty, { specialty = it }, label = { Text(stringResource(R.string.doctor_specialty)) }, singleLine = true)
                OutlinedTextField(hospital, { hospital = it }, label = { Text(stringResource(R.string.doctor_hospital)) }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.doctor_address)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.doctor_phone)) }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
                OutlinedTextField(hours, { hours = it }, label = { Text(stringResource(R.string.doctor_hours)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(doctor.copy(
                    name = name.trim(), specialty = specialty.trim().ifBlank { null }, hospital = hospital.trim().ifBlank { null },
                    address = address.trim().ifBlank { null }, phone = phone.trim().ifBlank { null }, visitingHours = hours.trim().ifBlank { null },
                ))
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
