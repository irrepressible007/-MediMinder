package com.example.mediminder.ui.medicines

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.mediminder.R
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.catalogue.CatalogueMedicine
import com.example.mediminder.data.catalogue.CatalogueRepository
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.local.entity.AlertType
import com.example.mediminder.data.local.entity.DoseLog
import com.example.mediminder.data.local.entity.DoseStatus
import com.example.mediminder.data.local.entity.MedicationWithSchedules
import com.example.mediminder.data.local.entity.RestockEvent
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.Adherence
import com.example.mediminder.domain.MedicationAdherence
import com.example.mediminder.domain.Stock
import com.example.mediminder.domain.minuteOf
import com.example.mediminder.domain.toLocalDateTime
import com.example.mediminder.ui.components.ConfirmDialog
import com.example.mediminder.ui.components.InfoRow
import com.example.mediminder.ui.components.PillAvatar
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.ui.restock.RestockDialog
import com.example.mediminder.ui.restock.packSizeFrom
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class MedicineDetailUi(
    val item: MedicationWithSchedules,
    val restocks: List<RestockEvent>,
    val logs: List<DoseLog>,
    val adherence: MedicationAdherence?,
    val catalogueItem: CatalogueMedicine?,
    val alternatives: List<CatalogueMedicine>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicineDetailViewModel @Inject constructor(
    private val repository: MedicineRepository,
    private val catalogue: CatalogueRepository,
    private val settings: SettingsRepository,
    db: MediMinderDatabase,
) : ViewModel() {
    private val id = MutableStateFlow<Long?>(null)
    private val catalogueInfo = MutableStateFlow<Pair<CatalogueMedicine?, List<CatalogueMedicine>>>(null to emptyList())

    val refillBuyDays: Int get() = settings.current.refillBuyDays

    private val today = LocalDate.now().toEpochDay()

    val state: StateFlow<MedicineDetailUi?> = id.filterNotNull().flatMapLatest { medId ->
        combine(
            repository.observe(medId),
            repository.observeRestocks(medId),
            db.doseLogDao().observeForMedication(medId, 60),
            db.doseLogDao().observeRange(today - 29, today),
            catalogueInfo,
        ) { item, restocks, logs, monthLogs, (catalogueItem, alternatives) ->
            if (item == null) return@combine null
            val summary = Adherence.summarise(
                listOf(item), monthLogs.filter { it.medicationId == medId },
                LocalDate.now().minusDays(29), LocalDate.now(), LocalDateTime.now(),
            )
            MedicineDetailUi(item, restocks, logs, summary.perMedication.firstOrNull(), catalogueItem, alternatives)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(medicationId: Long) {
        if (id.value == medicationId) return
        id.value = medicationId
        viewModelScope.launch {
            val item = repository.get(medicationId) ?: return@launch
            val m = item.medication
            val catalogueItem = m.catalogueId?.let { catalogue.byId(it) }
            val alternatives = catalogue.alternatives(m.genericName ?: catalogueItem?.genericName, m.strength, catalogueItem?.id ?: -1)
                .filter { !it.brandName.equals(m.name, ignoreCase = true) }
            catalogueInfo.value = catalogueItem to alternatives
        }
    }

    fun restock(quantity: Double, price: Double?, pharmacy: String?) = viewModelScope.launch {
        id.value?.let { repository.restock(it, quantity, price, pharmacy) }
    }

    fun setStock(count: Double) = viewModelScope.launch { id.value?.let { repository.setStock(it, count) } }

    fun setArchived(archived: Boolean) = viewModelScope.launch { id.value?.let { repository.setArchived(it, archived) } }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        id.value?.let { repository.delete(it) }
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineDetailScreen(
    medicationId: Long,
    openRestock: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: MedicineDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(medicationId) { viewModel.load(medicationId) }
    val context = LocalContext.current
    val ui by viewModel.state.collectAsState()
    var restockOpen by rememberSaveable { mutableStateOf(openRestock) }
    var countOpen by rememberSaveable { mutableStateOf(false) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui?.item?.medication?.name.orEmpty(), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit)) }
                    IconButton(onClick = { deleteOpen = true }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete)) }
                },
            )
        },
    ) { padding ->
        val state = ui
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val m = state.item.medication
        val schedules = state.item.schedules.sortedBy { it.minuteOfDay }
        val today = LocalDate.now().toEpochDay()
        val daysLeft = Stock.daysLeft(m, schedules)
        val low = Stock.isLow(m, schedules, today)

        if (restockOpen) {
            RestockDialog(
                medicineName = m.name,
                unit = m.unit,
                suggested = Stock.suggestedPurchase(m, schedules, viewModel.refillBuyDays, today).takeIf { it > 0 },
                packSize = packSizeFrom(state.catalogueItem?.packInfo),
                onDismiss = { restockOpen = false },
                onConfirm = viewModel::restock,
            )
        }
        if (countOpen) CountDialog(m.stock, onDismiss = { countOpen = false }, onConfirm = viewModel::setStock)
        if (deleteOpen) {
            ConfirmDialog(
                title = stringResource(R.string.delete_medicine_title, m.name),
                text = stringResource(R.string.delete_medicine_body),
                confirmLabel = stringResource(R.string.action_delete),
                destructive = true,
                onConfirm = { viewModel.delete(onDeleted) },
                onDismiss = { deleteOpen = false },
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillAvatar(m.name, m.color, size = 56)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(scheduleSummary(context, state.item), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        InfoRow(stringResource(R.string.editor_generic), m.genericName)
                        InfoRow(stringResource(R.string.detail_form), m.form)
                        InfoRow(stringResource(R.string.detail_manufacturer), m.manufacturer)
                        state.catalogueItem?.let { c -> InfoRow(stringResource(R.string.detail_price), priceText(context, c)) }
                        InfoRow(stringResource(R.string.editor_notes), m.notes)
                    }
                }
            }

            // Dose times
            if (!m.isAsNeeded && schedules.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.detail_times)) }
                item {
                    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            schedules.forEach { s ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (s.alertType == AlertType.ALARM) Icons.Default.Alarm else Icons.Default.Notifications,
                                        contentDescription = stringResource(if (s.alertType == AlertType.ALARM) R.string.alert_alarm else R.string.alert_reminder),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(Formats.time(context, s.minuteOfDay), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(Formats.quantity(context, s.quantity, m.unit))
                                }
                            }
                        }
                    }
                }
            }

            // Stock
            item { SectionTitle(stringResource(R.string.editor_section_stock)) }
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (low) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (m.trackStock) {
                            Text(Formats.quantity(context, m.stock, m.unit), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (daysLeft != null) {
                                Text(stringResource(R.string.detail_days_left, Formats.number(context, daysLeft)))
                                LinearProgressIndicator(progress = { (daysLeft.coerceAtMost(30) / 30f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            }
                            if (low) Text(stringResource(R.string.detail_low_stock), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(stringResource(R.string.detail_stock_not_tracked), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { restockOpen = true }) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_restock))
                            }
                            OutlinedButton(onClick = { countOpen = true }) { Text(stringResource(R.string.action_set_count)) }
                        }
                    }
                }
            }
            if (state.restocks.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.detail_purchases)) }
                items(state.restocks.take(10), key = { "r${it.id}" }) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("+" + Formats.quantity(context, r.quantity, m.unit), fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(Formats.date(context, r.date.toLocalDateTime().toLocalDate()), r.pharmacy).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        r.pricePaid?.let { Text(Formats.taka(context, it), fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            // Other brands of the same medicine
            if (state.alternatives.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.detail_alternatives))
                    Text(stringResource(R.string.detail_alternatives_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            val current = state.catalogueItem?.unitPrice
                            state.alternatives.take(8).forEachIndexed { i, alt ->
                                if (i > 0) HorizontalDivider()
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${alt.brandName} ${alt.strength.orEmpty()}", fontWeight = FontWeight.SemiBold)
                                        Text(listOfNotNull(alt.dosageForm, alt.manufacturer).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        priceText(context, alt)?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                                        if (current != null && alt.unitPrice != null && alt.unitPrice < current) {
                                            Text(
                                                stringResource(R.string.detail_cheaper, Formats.taka(context, current - alt.unitPrice)),
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Adherence & history
            state.adherence?.rate?.let { rate ->
                item {
                    SectionTitle(stringResource(R.string.detail_adherence))
                    Text(stringResource(R.string.detail_adherence_value, Formats.number(context, rate), Formats.number(context, state.adherence.taken), Formats.number(context, state.adherence.expected)))
                }
            }
            if (state.logs.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.detail_history)) }
                items(state.logs.take(20), key = { "l${it.id}" }) { log ->
                    val at = log.loggedAt.toLocalDateTime()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(
                            Formats.shortDate(context, at.toLocalDate()) + ", " + Formats.time(context, minuteOf(at.toLocalTime())),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(when (log.status) {
                                DoseStatus.TAKEN -> R.string.state_taken
                                DoseStatus.SKIPPED -> R.string.state_skipped
                                DoseStatus.SNOOZED -> R.string.state_snoozed
                            }),
                            color = if (log.status == DoseStatus.TAKEN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.setArchived(!m.archived) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (m.archived) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (m.archived) R.string.action_resume_medicine else R.string.action_stop_medicine))
                }
            }
        }
    }
}

@Composable
private fun CountDialog(current: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current % 1.0 == 0.0) current.toInt().toString() else current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.count_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                label = { Text(stringResource(R.string.count_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = text.toDoubleOrNull() != null, onClick = { onConfirm(text.toDouble()); onDismiss() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
