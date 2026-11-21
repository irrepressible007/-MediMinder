package com.example.mediminder.ui.account

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.cloud.CloudAccount
import com.example.mediminder.cloud.CloudBackup
import com.example.mediminder.cloud.CloudConfig
import com.example.mediminder.cloud.CloudCopy
import com.example.mediminder.cloud.CloudUser
import com.example.mediminder.cloud.SignInException
import com.example.mediminder.data.local.MediMinderDatabase
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.ui.components.AnimatedArt
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.components.ConfirmDialog
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.NoticeCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.QuietButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.ui.health.whenText
import com.example.mediminder.ui.theme.Hue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUi(
    val busy: Boolean = false,
    @StringRes val message: Int? = null,
    val error: Boolean = false,
    /** Both this phone and the cloud have data: which to keep? */
    val choose: CloudCopy? = null,
    /** The cloud copy was brought back onto this phone. */
    val restored: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val account: CloudAccount,
    private val cloud: CloudBackup,
    private val db: MediMinderDatabase,
    settings: SettingsRepository,
) : ViewModel() {
    val user: StateFlow<CloudUser?> = account.user
    val settings = settings.settings
    private val _ui = MutableStateFlow(AccountUi())
    val ui: StateFlow<AccountUi> = _ui

    private fun run(step: suspend () -> Unit) {
        _ui.update { it.copy(busy = true, message = null, error = false) }
        viewModelScope.launch {
            try {
                step()
            } catch (e: SignInException) {
                _ui.update { it.copy(message = e.reason.takeIf { r -> r != 0 }, error = true) }
            } catch (e: Exception) {
                com.example.mediminder.util.ErrorLog.record("Account", "Cloud step failed", e)
                _ui.update { it.copy(message = R.string.account_error_generic, error = true) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    fun google(activity: Activity) = run { account.signInWithGoogle(activity); afterSignIn() }
    fun email(email: String, password: String, create: Boolean) = run {
        if (create) account.createEmailAccount(email, password) else account.signInWithEmail(email, password)
        afterSignIn()
    }
    fun resetPassword(email: String) = run {
        account.resetPassword(email)
        _ui.update { it.copy(message = R.string.account_reset_sent) }
    }

    /**
     * Just signed in. Nothing in the cloud yet: back this phone up. Nothing on this phone
     * (a reinstall or a new phone): bring the cloud copy back. Both: ask.
     */
    private suspend fun afterSignIn() {
        val copy = cloud.latest()
        val phoneEmpty = db.medicationDao().getAll().isEmpty() && db.readingDao().getAll().isEmpty()
        when {
            copy == null -> { cloud.upload(); _ui.update { it.copy(message = R.string.account_backed_up_first) } }
            phoneEmpty -> { cloud.restore(); _ui.update { it.copy(message = R.string.account_restored, restored = true) } }
            else -> _ui.update { it.copy(choose = copy) }
        }
    }

    fun keepPhone() = run { _ui.update { it.copy(choose = null) }; cloud.upload(); _ui.update { it.copy(message = R.string.account_backed_up) } }
    fun useCloud() = run { _ui.update { it.copy(choose = null) }; cloud.restore(); _ui.update { it.copy(message = R.string.account_restored, restored = true) } }
    fun backUpNow() = run { cloud.upload(); _ui.update { it.copy(message = R.string.account_backed_up) } }
    fun signOut() = run { account.signOut(); _ui.update { it.copy(message = R.string.account_signed_out) } }
    fun delete() = run { account.deleteAccount(); _ui.update { it.copy(message = R.string.account_deleted) } }
    fun dismissChoice() = _ui.update { it.copy(choose = null) }
}

/** Optional sign-in, so a copy of the data in the cloud comes back after a reinstall or on a new phone. */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onOpenLegal: (doc: String) -> Unit,
    onRestored: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val user by viewModel.user.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val settings by viewModel.settings.collectAsState()
    LaunchedEffect(ui.restored) {
        if (!ui.restored) return@LaunchedEffect
        FeedbackBus.show(FeedbackKind.SAVED, R.string.account_restored)
        onRestored()
    }

    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    ui.choose?.let { copy ->
        AlertDialog(
            onDismissRequest = viewModel::dismissChoice,
            title = { Text(stringResource(R.string.account_choose_title), style = MaterialTheme.typography.headlineSmall) },
            text = { Text(stringResource(R.string.account_choose_body, whenText(context, copy.savedAt), copy.device), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = viewModel::useCloud) { Text(stringResource(R.string.account_use_cloud)) } },
            dismissButton = { TextButton(onClick = viewModel::keepPhone) { Text(stringResource(R.string.account_keep_phone)) } },
        )
    }
    if (confirmRestore) ConfirmDialog(
        title = stringResource(R.string.account_restore_title),
        text = stringResource(R.string.account_restore_body),
        confirmLabel = stringResource(R.string.account_restore),
        onConfirm = { confirmRestore = false; viewModel.useCloud() },
        onDismiss = { confirmRestore = false },
    )
    if (confirmDelete) ConfirmDialog(
        title = stringResource(R.string.account_delete_title),
        text = stringResource(R.string.account_delete_body),
        confirmLabel = stringResource(R.string.account_delete),
        onConfirm = { confirmDelete = false; viewModel.delete() },
        onDismiss = { confirmDelete = false },
        destructive = true,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.account_title), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedArt(Art.CLOUD, Hue.SKY, size = 96.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.account_intro), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.message?.let { NoticeCard(stringResource(it), if (ui.error) Icons.Default.Warning else Icons.Default.Info, if (ui.error) Tone.WARNING else Tone.INFO) }

            if (!CloudConfig.available) {
                NoticeCard(stringResource(R.string.account_unavailable), Icons.Default.Info, Tone.INFO)
                return@Column
            }
            val u = user
            if (u == null) SignedOut(
                busy = ui.busy,
                onGoogle = { (context as? Activity)?.let(viewModel::google) },
                onEmail = viewModel::email,
                onReset = viewModel::resetPassword,
                onOpenLegal = onOpenLegal,
            ) else {
                MediCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name ?: u.email ?: "", style = MaterialTheme.typography.titleLarge)
                            if (u.name != null && u.email != null) Text(u.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(providerName(u.provider)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (settings.lastBackupAt > 0) stringResource(R.string.account_last_backup, whenText(context, settings.lastBackupAt))
                        else stringResource(R.string.account_never_backed_up),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                PrimaryButton(stringResource(R.string.account_back_up_now), onClick = viewModel::backUpNow, icon = Icons.Default.CloudUpload, enabled = !ui.busy)
                SecondaryButton(stringResource(R.string.account_restore), onClick = { confirmRestore = true }, icon = Icons.Default.CloudDownload, enabled = !ui.busy)
                NoticeCard(stringResource(R.string.account_photos_stay), Icons.Default.Info, Tone.INFO)
                QuietButton(stringResource(R.string.account_sign_out), onClick = viewModel::signOut, icon = Icons.Default.Logout, modifier = Modifier.align(Alignment.CenterHorizontally))
                SecondaryButton(
                    stringResource(R.string.account_delete),
                    onClick = { confirmDelete = true },
                    icon = Icons.Default.DeleteForever,
                    contentColor = MaterialTheme.colorScheme.error,
                    enabled = !ui.busy,
                )
            }
        }
    }
}

@Composable
private fun SignedOut(
    busy: Boolean,
    onGoogle: () -> Unit,
    onEmail: (email: String, password: String, create: Boolean) -> Unit,
    onReset: (email: String) -> Unit,
    onOpenLegal: (doc: String) -> Unit,
) {
    var showEmail by rememberSaveable { mutableStateOf(!CloudConfig.google) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (CloudConfig.google) PrimaryButton(stringResource(R.string.account_google), onClick = onGoogle, enabled = !busy)
        if (!showEmail) SecondaryButton(stringResource(R.string.account_email), onClick = { showEmail = true }, icon = Icons.Default.Email)
        else MediCard {
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() },
                label = { Text(stringResource(R.string.account_email_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text(stringResource(R.string.account_password_label)) },
                supportingText = { Text(stringResource(R.string.account_password_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val ok = email.contains('@') && password.length >= 6 && !busy
            PrimaryButton(stringResource(R.string.account_sign_in), onClick = { onEmail(email, password, false) }, enabled = ok)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.account_create), onClick = { onEmail(email, password, true) }, enabled = ok)
            QuietButton(stringResource(R.string.account_forgot), onClick = { onReset(email) }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Text(stringResource(R.string.account_agree), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            QuietButton(stringResource(R.string.legal_terms), onClick = { onOpenLegal("TERMS") }, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.legal_privacy), onClick = { onOpenLegal("PRIVACY") }, modifier = Modifier.weight(1f))
        }
    }
}

@StringRes
private fun providerName(provider: String): Int = when (provider) {
    "google.com" -> R.string.account_with_google
    else -> R.string.account_with_email
}
