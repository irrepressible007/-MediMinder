package com.example.mediminder.ui.more

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation3.runtime.NavKey
import com.example.mediminder.BuildConfig
import com.example.mediminder.R
import com.example.mediminder.reminders.Reliability
import com.example.mediminder.ui.MedicalId
import com.example.mediminder.ui.RefillList
import com.example.mediminder.ui.ReliabilityKey
import com.example.mediminder.ui.SettingsKey
import com.example.mediminder.ui.Stats
import com.example.mediminder.ui.Vault
import com.example.mediminder.updater.UpdateManager
import com.example.mediminder.updater.UpdateResult
import com.example.mediminder.util.Formats
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(modifier: Modifier, onOpen: (NavKey) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reliable by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reliable = Reliability.allCriticalOk(context) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(title = { Text(stringResource(R.string.tab_more), fontWeight = FontWeight.Bold) })
        Entry(Icons.Default.ShoppingCart, stringResource(R.string.refill_title), stringResource(R.string.more_refill_sub)) { onOpen(RefillList) }
        Entry(Icons.Default.BarChart, stringResource(R.string.stats_title), stringResource(R.string.more_stats_sub)) { onOpen(Stats) }
        Entry(Icons.Default.Badge, stringResource(R.string.medical_id_title), stringResource(R.string.more_medical_id_sub)) { onOpen(MedicalId) }
        Entry(Icons.Default.Folder, stringResource(R.string.vault_title), stringResource(R.string.more_vault_sub)) { onOpen(Vault) }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Entry(
            Icons.Default.VerifiedUser,
            stringResource(R.string.reliability_title),
            stringResource(if (reliable) R.string.more_reliability_ok else R.string.more_reliability_problem),
            highlight = !reliable,
        ) { onOpen(ReliabilityKey) }
        Entry(Icons.Default.Settings, stringResource(R.string.settings_title), stringResource(R.string.more_settings_sub)) { onOpen(SettingsKey) }
        Entry(Icons.Default.SystemUpdate, stringResource(R.string.more_updates), stringResource(R.string.more_version, Formats.digits(context, BuildConfig.VERSION_NAME))) {
            scope.launch {
                Toast.makeText(context, context.getString(R.string.update_checking), Toast.LENGTH_SHORT).show()
                when (val result = UpdateManager.checkForUpdates(context)) {
                    is UpdateResult.UpdateAvailable -> UpdateManager.downloadAndInstallUpdate(context, result.downloadUrl, result.version)
                    is UpdateResult.UpToDate -> Toast.makeText(context, context.getString(R.string.update_latest), Toast.LENGTH_SHORT).show()
                    is UpdateResult.Error -> Toast.makeText(context, context.getString(R.string.update_failed, result.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
private fun Entry(icon: ImageVector, title: String, subtitle: String, highlight: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle, color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Icon(icon, contentDescription = null, tint = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
