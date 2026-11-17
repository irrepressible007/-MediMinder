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
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.example.mediminder.ui.photos.MedicinePhoto
import com.example.mediminder.data.photos.MedicinePhotos
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warning
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.ExpandableSection
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.NoticeCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.QuietButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.ui.theme.LocalStatusColors
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
    @ApplicationContext private val context: Context,
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
        id.value?.let { medId ->
            val photo = repository.get(medId)?.medication?.photoUri
            repository.delete(medId)
            MedicinePhotos.delete(context, photo)
        }
        onDone()
    }
}

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
    val status = LocalStatusColors.current
    val ui by viewModel.state.collectAsState()
    var restockOpen by rememberSaveable { mutableStateOf(openRestock) }
    var countOpen by rememberSaveable { mutableStateOf(false) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    var photoOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = ui?.item?.medication?.name.orEmpty(), onBack = onBack) },
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
        val hasPhoto = MedicinePhotos.model(context, m.photoUri) != null
        if (photoOpen && hasPhoto) FullScreenPhoto(m.photoUri, m.name, onDismiss = { photoOpen = false })
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (hasPhoto) item {
                MedicinePhoto(
                    m.photoUri,
                    stringResource(R.string.cd_medicine_photo, m.name),
                    Modifier.fillMaxWidth().aspectRatio(16f / 10f).clickable { photoOpen = true },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    if (!hasPhoto) {
                        PillAvatar(m.name, m.color, size = 60)
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.headlineSmall)
                        m.genericName?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(scheduleSummary(context, state.item), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (m.archived) item {
                NoticeCard(stringResource(R.string.detail_stopped_notice), Icons.Default.Pause, Tone.INFO)
            }

            // When to take it
            if (!m.isAsNeeded && schedules.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.editor_section_schedule)) }
                item {
                    MediCard {
                        schedules.forEachIndexed { i, s ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (s.alertType == AlertType.ALARM) Icons.Default.Alarm else Icons.Default.Notifications,
                                    contentDescription = stringResource(if (s.alertType == AlertType.ALARM) R.string.alert_alarm else R.string.alert_reminder),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(Formats.time(context, s.minuteOfDay), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Text(Formats.quantity(context, s.quantity, m.unit), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        m.dosePattern?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.detail_prescription, Formats.digits(context, it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Stock
            item { SectionTitle(stringResource(R.string.editor_section_stock)) }
            item {
                MediCard(
                    containerColor = if (low) status.dueContainer else MaterialTheme.colorScheme.surface,
                    borderColor = if (low) status.due else MaterialTheme.colorScheme.outlineVariant,
                    borderWidth = if (low) 2.dp else 1.dp,
                ) {
                    if (m.trackStock) {
                        Text(stringResource(R.string.detail_left, Formats.quantity(context, m.stock, m.unit)), style = MaterialTheme.typography.headlineSmall)
                        if (daysLeft != null) Text(stringResource(R.string.detail_days_left, Formats.number(context, daysLeft)), style = MaterialTheme.typography.bodyLarge)
                        if (low) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = status.onDueContainer)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.detail_low_stock), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = status.onDueContainer)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(stringResource(R.string.detail_bought_more), onClick = { restockOpen = true }, icon = Icons.Default.ShoppingCart)
                        QuietButton(stringResource(R.string.detail_fix_count), onClick = { countOpen = true }, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Text(stringResource(R.string.detail_stock_not_tracked), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(stringResource(R.string.detail_start_counting), onClick = { countOpen = true }, icon = Icons.Default.Inventory2)
                    }
                }
            }

            // About this medicine: folded away
            item {
                ExpandableSection(stringResource(R.string.detail_about)) {
                    MediCard {
                        InfoRow(stringResource(R.string.editor_generic), m.genericName)
                        InfoRow(stringResource(R.string.detail_form), m.form)
                        InfoRow(stringResource(R.string.detail_manufacturer), m.manufacturer)
                        state.catalogueItem?.let { c -> InfoRow(stringResource(R.string.detail_price), priceText(context, c)) }
                        InfoRow(stringResource(R.string.editor_notes), m.notes)
                    }
                    if (state.alternatives.isNotEmpty()) {
                        Text(stringResource(R.string.detail_alternatives), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.detail_alternatives_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MediCard {
                            val current = state.catalogueItem?.unitPrice
                            state.alternatives.take(8).forEachIndexed { i, alt ->
                                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${alt.brandName} ${alt.strength.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                                        Text(listOfNotNull(alt.dosageForm, alt.manufacturer).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        priceText(context, alt)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                        if (current != null && alt.unitPrice != null && alt.unitPrice < current) {
                                            Text(stringResource(R.string.detail_cheaper, Formats.taka(context, current - alt.unitPrice)), style = MaterialTheme.typography.labelMedium, color = status.taken, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // History: folded away
            item {
                ExpandableSection(stringResource(R.string.detail_history)) {
                    state.adherence?.rate?.let { rate ->
                        Text(
                            stringResource(R.string.detail_adherence) + ": " + stringResource(R.string.detail_adherence_value, Formats.number(context, rate), Formats.number(context, state.adherence.taken), Formats.number(context, state.adherence.expected)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (state.logs.isNotEmpty()) MediCard {
                        state.logs.take(20).forEach { log ->
                            val at = log.loggedAt.toLocalDateTime()
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(Formats.shortDate(context, at.toLocalDate()) + ", " + Formats.time(context, minuteOf(at.toLocalTime())), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(
                                    stringResource(when (log.status) {
                                        DoseStatus.TAKEN -> R.string.state_taken
                                        DoseStatus.SKIPPED -> R.string.state_skipped
                                        DoseStatus.SNOOZED -> R.string.state_snoozed
                                    }),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (log.status == DoseStatus.TAKEN) status.taken else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    if (state.restocks.isNotEmpty()) {
                        Text(stringResource(R.string.detail_purchases), style = MaterialTheme.typography.titleSmall)
                        MediCard {
                            state.restocks.take(10).forEach { r ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("+" + Formats.quantity(context, r.quantity, m.unit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(listOfNotNull(Formats.date(context, r.date.toLocalDateTime().toLocalDate()), r.pharmacy).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    r.pricePaid?.let { Text(Formats.taka(context, it), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
                                }
                            }
                        }
                    }
                }
            }

            // Actions, spelled out
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    SecondaryButton(stringResource(R.string.action_edit_medicine), onClick = onEdit, icon = Icons.Default.Edit)
                    SecondaryButton(
                        stringResource(if (m.archived) R.string.action_resume_medicine else R.string.action_stop_medicine),
                        onClick = { viewModel.setArchived(!m.archived) },
                        icon = if (m.archived) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                    QuietButton(
                        stringResource(R.string.action_delete_medicine),
                        onClick = { deleteOpen = true },
                        icon = Icons.Default.Delete,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

/** The photo as large as the screen allows — tap anywhere to close. */
@Composable
private fun FullScreenPhoto(photo: String?, name: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss)) {
            MedicinePhoto(photo, stringResource(R.string.cd_medicine_photo, name), Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(56.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun CountDialog(current: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current % 1.0 == 0.0) current.toInt().toString() else current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.count_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                label = { Text(stringResource(R.string.count_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
            )
        },
        confirmButton = {
            TextButton(enabled = text.toDoubleOrNull() != null, onClick = { onConfirm(text.toDouble()); onDismiss() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
