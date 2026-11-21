package com.example.mediminder.ui.more

import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import com.example.mediminder.util.Support
import androidx.compose.material.icons.filled.BugReport
import com.example.mediminder.ui.AccountKey
import com.example.mediminder.cloud.CloudConfig
import androidx.compose.material.icons.filled.Cloud
import com.example.mediminder.ui.LegalKey
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Gavel
import com.example.mediminder.ui.RamadanKey
import androidx.compose.material.icons.filled.NightsStay
import com.example.mediminder.ui.HealthKey
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.runtime.collectAsState
import com.example.mediminder.updater.UpdateState
import com.example.mediminder.updater.Updater
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
import androidx.compose.ui.platform.LocalResources
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
import com.example.mediminder.util.Formats
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(modifier: Modifier, onOpen: (NavKey) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var reliable by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reliable = Reliability.allCriticalOk(context) }
    val update by Updater.state.collectAsState()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(R.string.tab_more), icon = Icons.Default.Apps, hue = Hue.VIOLET)
        val entrance = rememberEntranceState()
        if (CloudConfig.available) MediCard(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.entrance(entrance, 0)) {
            NavRow(Icons.Default.Cloud, stringResource(R.string.account_title), { onOpen(AccountKey) }, subtitle = stringResource(R.string.account_more_sub), hue = Hue.SKY)
        }
        MediCard(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.entrance(entrance, 0)) {
            NavRow(Icons.Default.MonitorHeart, stringResource(R.string.health_title), { onOpen(HealthKey) }, subtitle = stringResource(R.string.more_health_sub), hue = Hue.CORAL)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(Icons.Default.NightsStay, stringResource(R.string.ramadan_title), { onOpen(RamadanKey) }, subtitle = stringResource(R.string.ramadan_more_sub), hue = Hue.NIGHT)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                { Updater.checkNow() },
                subtitle = when (val u = update) {
                    is UpdateState.Available -> stringResource(R.string.update_available_sub, Formats.digits(context, u.info.versionName))
                    is UpdateState.Ready -> stringResource(R.string.update_available_sub, Formats.digits(context, u.info.versionName))
                    else -> stringResource(R.string.more_version, Formats.digits(context, BuildConfig.VERSION_NAME))
                },
                hue = Hue.NIGHT,
            )
        }
        MediCard(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.entrance(entrance, 2)) {
            NavRow(Icons.Default.BugReport, stringResource(R.string.report_problem), { Support.reportProblem(context) }, subtitle = stringResource(R.string.report_problem_sub), hue = Hue.CORAL)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(Icons.Default.Policy, stringResource(R.string.legal_privacy), { onOpen(LegalKey("PRIVACY")) }, subtitle = stringResource(R.string.legal_privacy_sub), hue = Hue.SLATE)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(Icons.Default.Gavel, stringResource(R.string.legal_terms), { onOpen(LegalKey("TERMS")) }, subtitle = stringResource(R.string.legal_terms_sub), hue = Hue.SLATE)
        }
        Signature(Modifier.entrance(entrance, 3))
    }
}

/** The maker's small signature at the very bottom; it opens their GitHub page. */
@Composable
private fun Signature(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val description = stringResource(R.string.signature_open)
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = description) { uri.openUri(Support.GITHUB) }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.signature_made_by, Support.AUTHOR),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Support.GITHUB.removePrefix("https://"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
