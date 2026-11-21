package com.example.mediminder.ui.account

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material.icons.automirrored.filled.Logout
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
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!CloudConfig.available) {
                NoticeCard(stringResource(R.string.account_unavailable), Icons.Default.Info, Tone.INFO)
                return@Column
            }
            val u = user
            if (u == null) {
                SignInHeader(stringResource(R.string.account_sign_in_title), stringResource(R.string.account_intro))
                SignInPanel(viewModel, onOpenLegal)
                return@Column
            }
            // Signed in: who, and that backup runs by itself
            MediCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (u.provider == "google.com") Icons.Default.AccountCircle else Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name ?: u.email ?: "", style = MaterialTheme.typography.titleLarge)
                        if (u.name != null && u.email != null) Text(u.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(providerName(u.provider)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            MediCard(containerColor = MaterialTheme.colorScheme.primaryContainer, borderColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.account_auto_on), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            if (settings.lastBackupAt > 0) stringResource(R.string.account_last_backup, whenText(context, settings.lastBackupAt))
                            else stringResource(R.string.account_never_backed_up),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.account_photos_stay), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.message?.let { NoticeCard(stringResource(it), if (ui.error) Icons.Default.Warning else Icons.Default.Info, if (ui.error) Tone.WARNING else Tone.INFO) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.account_save_now), onClick = viewModel::backUpNow, icon = Icons.Default.CloudUpload, enabled = !ui.busy, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.account_restore_short), onClick = { confirmRestore = true }, icon = Icons.Default.CloudDownload, enabled = !ui.busy, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            QuietButton(stringResource(R.string.account_sign_out), onClick = viewModel::signOut, icon = Icons.AutoMirrored.Filled.Logout, modifier = Modifier.align(Alignment.CenterHorizontally))
            QuietButton(
                stringResource(R.string.account_delete),
                onClick = { confirmDelete = true },
                icon = Icons.Default.DeleteForever,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** The picture, a title and a line of explanation above the sign-in buttons. */
@Composable
fun SignInHeader(title: String, body: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedArt(Art.CLOUD, Hue.SKY, size = 120.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * The familiar sign-in choices: a white "Continue with Google" button with Google's G, "or",
 * then email (which opens a short form). Used on the Account page and during setup.
 */
@Composable
fun SignInPanel(viewModel: AccountViewModel, onOpenLegal: (doc: String) -> Unit) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsState()
    ui.choose?.let { copy ->
        AlertDialog(
            onDismissRequest = viewModel::dismissChoice,
            title = { Text(stringResource(R.string.account_choose_title), style = MaterialTheme.typography.headlineSmall) },
            text = { Text(stringResource(R.string.account_choose_body, whenText(context, copy.savedAt), copy.device), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = viewModel::useCloud) { Text(stringResource(R.string.account_use_cloud)) } },
            dismissButton = { TextButton(onClick = viewModel::keepPhone) { Text(stringResource(R.string.account_keep_phone)) } },
        )
    }
    var showEmail by rememberSaveable { mutableStateOf(!CloudConfig.google) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.message?.let { NoticeCard(stringResource(it), if (ui.error) Icons.Default.Warning else Icons.Default.Info, if (ui.error) Tone.WARNING else Tone.INFO) }
        if (CloudConfig.google) {
            ProviderButton(
                text = stringResource(R.string.account_google),
                onClick = { context.findActivity()?.let(viewModel::google) },
                enabled = !ui.busy,
            ) { Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(22.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.account_or), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (!showEmail) {
            ProviderButton(text = stringResource(R.string.account_email), onClick = { showEmail = true }, enabled = !ui.busy) {
                Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        } else {
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() },
                label = { Text(stringResource(R.string.account_email_label)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text(stringResource(R.string.account_password_label)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                supportingText = { Text(stringResource(R.string.account_password_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            val ok = email.contains('@') && password.length >= 6 && !ui.busy
            PrimaryButton(stringResource(R.string.account_sign_in), onClick = { viewModel.email(email, password, false) }, enabled = ok)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (email.contains('@')) TextButton(onClick = { viewModel.resetPassword(email) }) { Text(stringResource(R.string.account_forgot)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.email(email, password, true) }, enabled = ok) { Text(stringResource(R.string.account_create)) }
            }
        }
        Text(
            stringResource(R.string.account_agree),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { onOpenLegal("TERMS") }) { Text(stringResource(R.string.legal_terms), style = MaterialTheme.typography.labelMedium) }
            TextButton(onClick = { onOpenLegal("PRIVACY") }) { Text(stringResource(R.string.legal_privacy), style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** A sign-in choice as people know it: white, outlined, the provider's mark on the left. */
@Composable
private fun ProviderButton(text: String, onClick: () -> Unit, enabled: Boolean, mark: @Composable () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        mark()
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** The Activity behind a (possibly wrapped) context: Credential Manager needs it. */
private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@StringRes
private fun providerName(provider: String): Int = when (provider) {
    "google.com" -> R.string.account_with_google
    else -> R.string.account_with_email
}
