package com.example.mediminder.ui.restock

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.ShoppingCart
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.R
import com.example.mediminder.ui.components.MedicineImage
import com.example.mediminder.data.MedicineRepository
import com.example.mediminder.data.catalogue.CatalogueMedicine
import com.example.mediminder.data.catalogue.CatalogueRepository
import com.example.mediminder.data.local.entity.Medication
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.domain.Stock
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.ui.components.PillAvatar
import com.example.mediminder.ui.components.SectionTitle
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RefillItem(
    val medication: Medication,
    val daysLeft: Int?,
    val low: Boolean,
    val buy: Int,
    val catalogueItem: CatalogueMedicine?,
) {
    val estimatedCost: Double? get() = catalogueItem?.unitPrice?.let { it * buy }
}

data class RefillUi(val low: List<RefillItem>, val others: List<RefillItem>, val buyDays: Int) {
    val totalCost: Double? get() = low.mapNotNull { it.estimatedCost }.takeIf { it.isNotEmpty() }?.sum()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class RefillViewModel @Inject constructor(
    private val repository: MedicineRepository,
    private val catalogue: CatalogueRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val cache = HashMap<Long, CatalogueMedicine?>()

    val state: StateFlow<RefillUi?> = combine(repository.observeAll(), settings.settings) { meds, s -> meds to s.refillBuyDays }
        .mapLatest { (meds, buyDays) ->
            val today = LocalDate.now().toEpochDay()
            val items = meds.filter { !it.medication.archived && it.medication.trackStock }.map { item ->
                val m = item.medication
                val catalogueItem = m.catalogueId?.let { id -> cache.getOrPut(id) { catalogue.byId(id) } }
                RefillItem(
                    medication = m,
                    daysLeft = Stock.daysLeft(m, item.schedules),
                    low = Stock.isLow(m, item.schedules, today),
                    buy = Stock.suggestedPurchase(m, item.schedules, buyDays, today),
                    catalogueItem = catalogueItem,
                )
            }.sortedWith(compareBy<RefillItem> { it.daysLeft ?: Int.MAX_VALUE }.thenBy { it.medication.name.lowercase() })
            RefillUi(items.filter { it.low }, items.filter { !it.low }, buyDays)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restock(medicationId: Long, quantity: Double, price: Double?, pharmacy: String?) = viewModelScope.launch {
        repository.restock(medicationId, quantity, price, pharmacy)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefillListScreen(onBack: () -> Unit, onOpenMedicine: (Long) -> Unit, viewModel: RefillViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val ui by viewModel.state.collectAsState()
    var restocking by remember { mutableStateOf<RefillItem?>(null) }

    restocking?.let { item ->
        RestockDialog(
            medicineName = item.medication.name,
            unit = item.medication.unit,
            suggested = item.buy.takeIf { it > 0 },
            packSize = packSizeFrom(item.catalogueItem?.packInfo),
            onDismiss = { restocking = null },
            onConfirm = { q, p, ph -> viewModel.restock(item.medication.id, q, p, ph) },
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.refill_title), onBack = onBack)
        },
    ) { padding ->
        val state = ui ?: return@Scaffold
        if (state.low.isEmpty() && state.others.isEmpty()) {
            EmptyState(
                icon = Icons.Default.CheckCircle,
                title = stringResource(R.string.refill_none_tracked_title),
                body = stringResource(R.string.refill_none_tracked_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    if (state.low.isEmpty()) stringResource(R.string.refill_all_good)
                    else stringResource(R.string.refill_summary, Formats.number(context, state.low.size), Formats.number(context, state.buyDays)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.totalCost?.let {
                    Text(stringResource(R.string.refill_estimated_total, Formats.taka(context, it)), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (state.low.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(stringResource(R.string.refill_send), onClick = {
                        val text = shoppingListText(context, state)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    }, icon = Icons.Default.Share)
                }
            }
            if (state.low.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.refill_running_low)) }
                items(state.low, key = { it.medication.id }) { item ->
                    RefillRow(item, onOpen = { onOpenMedicine(item.medication.id) }, onBought = { restocking = item })
                }
            }
            if (state.others.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.refill_others)) }
                items(state.others, key = { it.medication.id }) { item ->
                    RefillRow(item, onOpen = { onOpenMedicine(item.medication.id) }, onBought = { restocking = item })
                }
            }
        }
    }
}

@Composable
private fun RefillRow(item: RefillItem, onOpen: () -> Unit, onBought: () -> Unit) {
    val context = LocalContext.current
    val m = item.medication
    val status = LocalStatusColors.current
    MediCard(
        onClick = onOpen,
        containerColor = if (item.low) status.dueContainer else MaterialTheme.colorScheme.surface,
        borderColor = if (item.low) status.due else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedicineImage(m.photoUri, m.name, m.color, size = 64)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.titleLarge)
                val stockText = Formats.quantity(context, m.stock, m.unit)
                Text(
                    if (item.daysLeft != null) stringResource(R.string.stock_left_days, stockText, Formats.number(context, item.daysLeft)) else stringResource(R.string.stock_left, stockText),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.low) status.onDueContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.buy > 0) {
                    Text(
                        stringResource(R.string.refill_buy, Formats.quantity(context, item.buy.toDouble(), m.unit)) +
                            (item.estimatedCost?.let { " · ≈ " + Formats.taka(context, it) } ?: ""),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(stringResource(R.string.detail_bought_more), onClick = onBought, icon = Icons.Default.ShoppingCart)
    }
}

/** Plain-text list to send to the pharmacy (WhatsApp, imo, SMS…). */
private fun shoppingListText(context: android.content.Context, state: RefillUi): String = buildString {
    appendLine(context.getString(R.string.refill_share_header))
    state.low.forEachIndexed { i, item ->
        val m = item.medication
        val name = listOf(m.name, m.strength).filter { it.isNotBlank() }.joinToString(" ")
        val qty = if (item.buy > 0) " — " + Formats.quantity(context, item.buy.toDouble(), m.unit) else ""
        appendLine("${Formats.number(context, i + 1)}. $name$qty")
    }
    append(context.getString(R.string.refill_share_footer))
}
