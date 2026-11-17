package com.example.mediminder.ui.restock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mediminder.R
import com.example.mediminder.util.Formats

/**
 * "I bought more" — how many, what it cost (optional) and where (optional).
 * [packSize] (e.g. 10 for a strip, 30 for a box) offers a one-tap amount.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestockDialog(
    medicineName: String,
    unit: String,
    suggested: Int?,
    packSize: Int?,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, price: Double?, pharmacy: String?) -> Unit,
) {
    val context = LocalContext.current
    var quantity by rememberSaveable { mutableStateOf(suggested?.takeIf { it > 0 }?.toString().orEmpty()) }
    var price by rememberSaveable { mutableStateOf("") }
    var pharmacy by rememberSaveable { mutableStateOf("") }
    val amount = quantity.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restock_title, medicineName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text(stringResource(R.string.restock_quantity, Formats.unitName(context, unit, 2.0))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOfNotNull(packSize, 10, 14, 30, suggested).filter { it > 0 }.distinct().sorted()).forEach { n ->
                        AssistChip(onClick = { quantity = n.toString() }, label = { Text(Formats.number(context, n)) })
                    }
                }
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' }.take(8) },
                    label = { Text(stringResource(R.string.restock_price)) },
                    prefix = { Text("৳ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pharmacy,
                    onValueChange = { pharmacy = it.take(80) },
                    label = { Text(stringResource(R.string.restock_pharmacy)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.restock_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null && amount > 0, onClick = {
                onConfirm(amount ?: 0.0, price.toDoubleOrNull(), pharmacy.trim().ifBlank { null })
                onDismiss()
            }) { Text(stringResource(R.string.action_add_stock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Pack size from catalogue text like "30's pack: ৳ 270.00" or "10's strip". */
fun packSizeFrom(packInfo: String?): Int? =
    packInfo?.let { Regex("""(\d+)'s""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
