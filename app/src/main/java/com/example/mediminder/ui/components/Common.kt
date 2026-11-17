package com.example.mediminder.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mediminder.R
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.togetherWith
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import com.example.mediminder.data.photos.MedicinePhotos
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.mediminder.domain.DoseState
import com.example.mediminder.ui.theme.LocalStatusColors
import com.example.mediminder.util.Formats
import java.time.LocalDate

// ── Page structure ──────────────────────────────────────────────────────────

/** Title of a main tab: large and plain, no toolbar chrome. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke(this)
    }
}

/** Toolbar for screens opened from a tab: a big back button and a clear title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), modifier = Modifier.size(28.dp))
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** Flat card with a hairline edge: clear shapes without heavy shadows. */
@Composable
fun MediCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(borderWidth, borderColor)
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().pressScale(interaction, 0.985f),
            shape = MaterialTheme.shapes.large,
            colors = colors,
            border = border,
            interactionSource = interaction,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

// ── Buttons ─────────────────────────────────────────────────────────────────

/** The one main action of a screen: 60dp tall and full width. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp).pressScale(interaction),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        interactionSource = interaction,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** "Taken": green is used for nothing else in the app. */
@Composable
fun TakenButton(onClick: () -> Unit, modifier: Modifier = Modifier, text: String = stringResource(R.string.action_taken)) {
    val status = LocalStatusColors.current
    PrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = Icons.Default.Check,
        colors = ButtonDefaults.buttonColors(containerColor = status.taken, contentColor = status.onTaken),
    )
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp).pressScale(interaction),
        interactionSource = interaction,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.primary) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ── Notices & status ────────────────────────────────────────────────────────

enum class Tone { INFO, WARNING, DANGER, SUCCESS }

@Composable
private fun toneColors(tone: Tone): Pair<Color, Color> {
    val status = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        Tone.INFO -> scheme.primaryContainer to scheme.onPrimaryContainer
        Tone.WARNING -> status.dueContainer to status.onDueContainer
        Tone.DANGER -> scheme.errorContainer to scheme.onErrorContainer
        Tone.SUCCESS -> status.takenContainer to status.onTakenContainer
    }
}

/** One-line message with an icon; tappable when it leads somewhere. */
@Composable
fun NoticeCard(
    text: String,
    icon: ImageVector,
    tone: Tone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val (container, content) = toneColors(tone)
    MediCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = container,
        borderColor = content.copy(alpha = 0.25f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = content, modifier = Modifier.weight(1f))
            if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                if (actionLabel != null) Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = content)
                else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = content)
            }
        }
    }
}

private data class StatusStyle(val container: Color, val content: Color, val icon: ImageVector)

@Composable
private fun statusStyle(state: DoseState): StatusStyle {
    val s = LocalStatusColors.current
    return when (state) {
        DoseState.TAKEN -> StatusStyle(s.takenContainer, s.onTakenContainer, Icons.Default.CheckCircle)
        DoseState.DUE -> StatusStyle(s.dueContainer, s.onDueContainer, Icons.Default.NotificationsActive)
        DoseState.MISSED -> StatusStyle(s.missedContainer, s.onMissedContainer, Icons.Default.ErrorOutline)
        DoseState.SNOOZED -> StatusStyle(s.snoozedContainer, s.onSnoozedContainer, Icons.Default.Snooze)
        DoseState.SKIPPED -> StatusStyle(s.quietContainer, s.onQuietContainer, Icons.Default.Block)
        DoseState.UPCOMING -> StatusStyle(s.quietContainer, s.onQuietContainer, Icons.Default.Schedule)
    }
}

/** Colour that marks a dose state (always shown together with its word and icon). */
@Composable
fun stateColor(state: DoseState): Color {
    val s = LocalStatusColors.current
    return when (state) {
        DoseState.TAKEN -> s.taken
        DoseState.DUE -> s.due
        DoseState.MISSED -> s.missed
        DoseState.SNOOZED -> s.snoozed
        DoseState.SKIPPED, DoseState.UPCOMING -> s.quiet
    }
}

@Composable
fun stateLabel(state: DoseState): String = stringResource(
    when (state) {
        DoseState.TAKEN -> R.string.state_taken
        DoseState.MISSED -> R.string.state_missed
        DoseState.DUE -> R.string.state_due
        DoseState.SKIPPED -> R.string.state_skipped
        DoseState.SNOOZED -> R.string.state_snoozed
        DoseState.UPCOMING -> R.string.state_upcoming
    }
)

/** Icon + word on a tinted pill. */
@Composable
fun StatusChip(state: DoseState, label: String = stateLabel(state), modifier: Modifier = Modifier) {
    val style = statusStyle(state)
    val container by animateColorAsState(style.container, tween(Motion.MEDIUM), label = "chipBg")
    val content by animateColorAsState(style.content, tween(Motion.MEDIUM), label = "chipFg")
    Row(
        modifier.clip(CircleShape).background(container).animateContentSize().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(style.icon, transitionSpec = { popIn() togetherWith fadeOut(tween(Motion.SHORT)) }, label = "chipIcon") { icon ->
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1)
    }
}

// ── Choices ─────────────────────────────────────────────────────────────────

/** A large radio choice — easy to read and hard to miss with a thumb. */
@Composable
fun ChoiceTile(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    badge: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surface, tween(Motion.MEDIUM), label = "tileBg")
    val borderColor by animateColorAsState(if (selected) scheme.primary else scheme.outlineVariant, tween(Motion.MEDIUM), label = "tileBorder")
    val borderWidth by animateDpAsState(if (selected) 2.5.dp else 1.dp, tween(Motion.MEDIUM), label = "tileBorderWidth")
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = modifier.fillMaxWidth().pressScale(interaction, 0.985f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton, interactionSource = interaction, indication = LocalIndication.current)
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) scheme.onPrimaryContainer else scheme.onSurface)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                }
            }
            if (badge != null) {
                Text(badge, style = MaterialTheme.typography.titleMedium, color = if (selected) scheme.primary else scheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
            }
            RadioMark(selected)
        }
    }
}

@Composable
private fun RadioMark(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(28.dp).border(2.dp, if (selected) scheme.primary else scheme.outline, CircleShape), contentAlignment = Alignment.Center) {
        AnimatedVisibility(selected, enter = popIn(), exit = fadeOut(tween(Motion.SHORT)) + scaleOut(targetScale = 0.6f)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Wrapping single-choice chips, for secondary choices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option == selected
            FilterChip(
                selected = on,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 10.dp)) },
                leadingIcon = if (on) ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp)) }) else null,
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = on,
                    borderColor = MaterialTheme.colorScheme.outline, borderWidth = 1.dp,
                ),
            )
        }
    }
}

/** − value + with 48dp buttons. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, minusEnabled: Boolean = true, plusEnabled: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = onMinus, enabled = minusEnabled, modifier = Modifier.size(48.dp), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_decrease))
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 8.dp),
        )
        OutlinedIconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(48.dp), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_increase))
        }
    }
}

/** "More options": keeps rarely needed things folded away until asked for. */
@Composable
fun ExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { open = !open }
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val turn by animateFloatAsState(if (open) 180f else 0f, tween(Motion.MEDIUM, easing = FastOutSlowInEasing), label = "chevron")
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp).rotate(turn))
        }
        AnimatedVisibility(
            open,
            enter = expandVertically(tween(Motion.MEDIUM, easing = FastOutSlowInEasing)) + fadeIn(tween(Motion.MEDIUM)),
            exit = shrinkVertically(tween(Motion.SHORT)) + fadeOut(tween(Motion.SHORT)),
        ) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

// ── Rows ────────────────────────────────────────────────────────────────────

/** A row that opens something: round icon, title, one line of explanation. */
@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tone: Tone? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val (iconBg, iconFg) = if (tone == Tone.DANGER) scheme.errorContainer to scheme.onErrorContainer else scheme.primaryContainer to scheme.primary
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 76.dp).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconFg, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (tone == Tone.DANGER) scheme.error else scheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

/** Label on the left, its current value on the right; tap to change. */
@Composable
fun ValueRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Title, explanation and a switch; the whole row toggles. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 60.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Label + value on detail screens. */
@Composable
fun InfoRow(label: String, value: String?, modifier: Modifier = Modifier) {
    if (value.isNullOrBlank()) return
    Column(modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// ── Little pieces ───────────────────────────────────────────────────────────

/** Round coloured badge with the medicine's first letter (its pill colour). */
@Composable
fun PillAvatar(name: String, colorHex: String, modifier: Modifier = Modifier, size: Int = 48) {
    Box(modifier.size(size.dp).clip(CircleShape).background(parseColor(colorHex)), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "•" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (size >= 44) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
        )
    }
}

/**
 * The medicine's own photo (box or strip) when there is one, otherwise the colour badge.
 * Rounded-square so a photo and a badge sit the same way in a list.
 */
@Composable
fun MedicineImage(photo: String?, name: String, colorHex: String, modifier: Modifier = Modifier, size: Int = 48) {
    val context = LocalContext.current
    val model = remember(photo) { MedicinePhotos.model(context, photo) }
    if (model == null) {
        PillAvatar(name, colorHex, modifier, size)
        return
    }
    val shape = RoundedCornerShape((size * 0.28f).dp)
    SubcomposeAsyncImage(
        model = model,
        contentDescription = stringResource(R.string.cd_medicine_photo, name),
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        error = { PillAvatar(name, colorHex, size = size) },
    )
}

fun parseColor(hex: String, fallback: Color = Color(0xFF0369A1)): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { fallback }

/** A time you can tap to change, e.g. "সকাল ৮:০০". */
@Composable
fun TimePill(minuteOfDay: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { showTimePicker(context, minuteOfDay, onChange) }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(Formats.time(context, minuteOfDay), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

fun showTimePicker(context: Context, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minuteOfDay / 60, minuteOfDay % 60, false).show()
}

fun showDatePicker(context: Context, date: LocalDate, minDate: LocalDate? = null, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
        minDate?.let { datePicker.minDate = it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
    }.show()
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.gentleFloat().size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.size(24.dp))
            action()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
