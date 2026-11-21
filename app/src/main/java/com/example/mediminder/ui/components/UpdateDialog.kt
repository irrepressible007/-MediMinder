package com.example.mediminder.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.mediminder.R
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.updater.UpdateState
import com.example.mediminder.updater.Updater
import com.example.mediminder.util.Formats

/** The update dialog: recommends, downloads with progress, then installs. Placed once at the root. */
@Composable
fun UpdateDialogHost() {
    val context = LocalContext.current
    val visible by Updater.dialog.collectAsState()
    val state by Updater.state.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Updater.onResume(context) }
    if (!visible || state is UpdateState.Idle) return

    val (icon, hue) = when (val s = state) {
        UpdateState.UpToDate -> Icons.Default.CheckCircle to Hue.GREEN
        is UpdateState.Failed -> Icons.Default.ErrorOutline to Hue.ROSE
        is UpdateState.Ready -> (if (s.needsPermission) Icons.Default.VerifiedUser else Icons.Default.SystemUpdate) to Hue.NIGHT
        else -> Icons.Default.SystemUpdate to Hue.NIGHT
    }
    val dismissible = state !is UpdateState.Downloading
    AlertDialog(
        onDismissRequest = { if (state is UpdateState.Available) Updater.later(context) else if (dismissible) Updater.hide() },
        properties = DialogProperties(dismissOnClickOutside = dismissible),
        // A new version arrives as a gift box popping open
        icon = { if (state is UpdateState.Available) AnimatedArt(Art.GIFT, Hue.VIOLET, size = 104.dp) else IconBadge(icon, hue, size = 56.dp) },
        title = {
            Text(
                stringResource(
                    when (val s = state) {
                        UpdateState.Checking -> R.string.update_checking
                        UpdateState.UpToDate -> R.string.update_up_to_date_title
                        is UpdateState.Available -> R.string.update_ready_title
                        is UpdateState.Downloading -> R.string.update_downloading_title
                        is UpdateState.Ready -> if (s.needsPermission) R.string.update_permission_title else R.string.update_install_title
                        is UpdateState.Failed -> R.string.update_problem_title
                        UpdateState.Idle -> R.string.update_checking
                    }
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            AnimatedContent(state, contentKey = { it::class }, transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.SHORT)) }, label = "update") { s ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (s) {
                        UpdateState.Checking, UpdateState.Idle -> Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(Modifier.size(36.dp))
                        }
                        UpdateState.UpToDate -> Body(stringResource(R.string.update_latest))
                        is UpdateState.Available -> {
                            Body(stringResource(R.string.update_ready_body, Formats.digits(context, s.info.versionName)))
                            if (s.info.apkSize > 0) {
                                Text(
                                    stringResource(R.string.update_size, Formats.number(context, Math.round(s.info.apkSize / 1_000_000.0).toInt())),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val notes = s.info.notesFor(bangla = Formats.isBangla(context))
                            if (notes.isNotEmpty()) {
                                ExpandableSection(stringResource(R.string.update_whats_new)) {
                                    notes.forEach { line -> Text("• $line", style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                        }
                        is UpdateState.Downloading -> {
                            val shown by animateFloatAsState(s.progress.coerceAtLeast(0f), tween(Motion.MEDIUM), label = "dl")
                            if (s.progress < 0) {
                                LinearProgressIndicator(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape), strokeCap = StrokeCap.Round)
                            } else {
                                LinearProgressIndicator(
                                    progress = { shown },
                                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                                    color = LocalStatusColors.current.taken,
                                    strokeCap = StrokeCap.Round,
                                    gapSize = 0.dp,
                                    drawStopIndicator = {},
                                )
                                Text(
                                    stringResource(R.string.update_percent, Formats.number(context, (s.progress * 100).toInt())),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Body(stringResource(R.string.update_downloading))
                        }
                        is UpdateState.Ready -> Body(stringResource(if (s.needsPermission) R.string.update_permission_body else R.string.update_install_body))
                        is UpdateState.Failed -> Body(stringResource(s.reason))
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateState.Available -> Action(stringResource(R.string.update_now)) { Updater.startDownload(context, s.info) }
                is UpdateState.Ready ->
                    if (s.needsPermission) Action(stringResource(R.string.update_open_settings)) { Updater.openInstallPermission(context) }
                    else Action(stringResource(R.string.update_install)) { Updater.install(context) }
                is UpdateState.Failed -> Action(stringResource(R.string.update_try_again)) {
                    if (s.info != null) Updater.startDownload(context, s.info) else Updater.checkNow()
                }
                UpdateState.UpToDate -> Action(stringResource(R.string.action_ok)) { Updater.hide() }
                is UpdateState.Downloading -> Action(stringResource(R.string.update_keep_using)) { Updater.hide() }
                else -> Unit
            }
        },
        dismissButton = {
            when (state) {
                is UpdateState.Available -> Action(stringResource(R.string.update_later), quiet = true) { Updater.later(context) }
                is UpdateState.Ready, is UpdateState.Failed -> Action(stringResource(R.string.update_later), quiet = true) { Updater.hide() }
                else -> Unit
            }
        },
    )
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Action(label: String, quiet: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 52.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}
