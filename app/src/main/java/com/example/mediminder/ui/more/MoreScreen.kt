package com.example.mediminder.ui.more

import androidx.compose.material.icons.filled.Apps
import com.example.mediminder.ui.theme.Hue
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation3.runtime.NavKey
import com.example.mediminder.BuildConfig
import com.example.mediminder.R
import com.example.mediminder.ui.components.rememberEntranceState
import com.example.mediminder.ui.components.entrance
import com.example.mediminder.ui.HistoryKey
import androidx.compose.material.icons.filled.History
import com.example.mediminder.reminders.Reliability
import com.example.mediminder.ui.MedicalId
import com.example.mediminder.ui.RefillList
import com.example.mediminder.ui.ReliabilityKey
import com.example.mediminder.ui.SettingsKey
import com.example.mediminder.ui.Stats
import com.example.mediminder.ui.Vault
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.NavRow
import com.example.mediminder.ui.components.ScreenHeader
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.updater.UpdateManager
import com.example.mediminder.updater.UpdateResult
import com.example.mediminder.util.Formats
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(modifier: Modifier, onOpen: (NavKey) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reliable by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reliable = Reliability.allCriticalOk(context) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(R.string.tab_more), icon = Icons.Default.Apps, hue = Hue.VIOLET)
        val entrance = rememberEntranceState()
        MediCard(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.entrance(entrance, 0)) {
            NavRow(Icons.Default.History, stringResource(R.string.history_title), { onOpen(HistoryKey()) }, subtitle = stringResource(R.string.more_history_sub), hue = Hue.VIOLET)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(Icons.Default.ShoppingCart, stringResource(R.string.refill_title), { onOpen(RefillList) }, subtitle = stringResource(R.string.more_refill_sub), hue = Hue.TEAL)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(Icons.Default.BarChart, stringResource(R.string.stats_title), { onOpen(Stats) }, subtitle = stringResource(R.string.more_stats_sub), hue = Hue.SKY)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(Icons.Default.Badge, stringResource(R.string.medical_id_title), { onOpen(MedicalId) }, subtitle = stringResource(R.string.more_medical_id_sub), hue = Hue.ROSE)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(Icons.Default.Folder, stringResource(R.string.vault_title), { onOpen(Vault) }, subtitle = stringResource(R.string.more_vault_sub), hue = Hue.AMBER)
        }
        MediCard(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.entrance(entrance, 1)) {
            NavRow(
                Icons.Default.VerifiedUser,
                stringResource(R.string.reliability_title),
                { onOpen(ReliabilityKey) },
                subtitle = stringResource(if (reliable) R.string.more_reliability_ok else R.string.more_reliability_problem),
                tone = if (reliable) null else Tone.DANGER,
                hue = Hue.GREEN,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(Icons.Default.Settings, stringResource(R.string.settings_title), { onOpen(SettingsKey) }, subtitle = stringResource(R.string.more_settings_sub), hue = Hue.SLATE)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(
                Icons.Default.SystemUpdate,
                stringResource(R.string.more_updates),
                {
                    scope.launch {
                        Toast.makeText(context, context.getString(R.string.update_checking), Toast.LENGTH_SHORT).show()
                        when (val result = UpdateManager.checkForUpdates(context)) {
                            is UpdateResult.UpdateAvailable -> UpdateManager.downloadAndInstallUpdate(context, result.downloadUrl, result.version)
                            is UpdateResult.UpToDate -> Toast.makeText(context, context.getString(R.string.update_latest), Toast.LENGTH_SHORT).show()
                            is UpdateResult.Error -> Toast.makeText(context, context.getString(R.string.update_failed, result.message), Toast.LENGTH_LONG).show()
                        }
                    }
                },
                subtitle = stringResource(R.string.more_version, Formats.digits(context, BuildConfig.VERSION_NAME)),
                hue = Hue.NIGHT,
            )
        }
    }
}
