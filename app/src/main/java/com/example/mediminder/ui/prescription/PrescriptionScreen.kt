package com.example.mediminder.ui.prescription

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.PrescriptionDraft
import com.example.mediminder.data.PrescriptionImporter
import com.example.mediminder.data.photos.MedicinePhotos
import com.example.mediminder.ui.components.AnimatedArt
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.ui.components.MediCard
import com.example.mediminder.ui.components.NoticeCard
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.QuietButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.components.Tone
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.util.ErrorLog
import com.example.mediminder.util.Formats
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PrescriptionUi(
    val reading: Boolean = false,
    /** null until a photo has been read. */
    val drafts: List<PrescriptionDraft>? = null,
    val chosen: Set<Int> = emptySet(),
    val saving: Boolean = false,
    val added: Int? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class PrescriptionViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val importer: PrescriptionImporter,
) : ViewModel() {
    private val _state = MutableStateFlow(PrescriptionUi())
    val state: StateFlow<PrescriptionUi> = _state

    /** A photo taken with the app's camera (removed again once read). */
    fun readPhoto(ref: String) {
        val file = MedicinePhotos.file(context, ref) ?: return
        read(Uri.fromFile(file)) { MedicinePhotos.delete(context, ref) }
    }

    fun read(uri: Uri, after: () -> Unit = {}) {
        _state.value = PrescriptionUi(reading = true)
        viewModelScope.launch {
            try {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val text = recognizer.process(InputImage.fromFilePath(context, uri)).await().text
                recognizer.close()
                val drafts = importer.read(text)
                // the likely ones that say how to take them start ticked
                _state.value = PrescriptionUi(drafts = drafts, chosen = drafts.indices.filter { drafts[it].ready && drafts[it].confident }.toSet())
            } catch (e: Exception) {
                ErrorLog.record("Prescription", "Could not read the photo", e)
                _state.value = PrescriptionUi(drafts = emptyList(), failed = true)
            } finally {
                after()
            }
        }
    }

    fun toggle(i: Int) = _state.update { s -> s.copy(chosen = if (i in s.chosen) s.chosen - i else s.chosen + i) }

    fun reset() { _state.value = PrescriptionUi() }

    fun add() {
        val s = _state.value
        val drafts = s.drafts ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val n = importer.add(s.chosen.sorted().map { drafts[it] })
            _state.update { it.copy(saving = false, added = n) }
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * Adding medicines from a photo of a prescription: the phone reads it (on the device), finds
 * each medicine in the Bangladeshi list and how to take it, and the person ticks what to add.
 */
@Composable
fun PrescriptionScreen(
    capturedPhoto: String?,
    onPhotoConsumed: () -> Unit,
    onTakePhoto: () -> Unit,
    onSetUp: (catalogueId: Long) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: PrescriptionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val s by viewModel.state.collectAsState()
    LaunchedEffect(capturedPhoto) {
        capturedPhoto ?: return@LaunchedEffect
        viewModel.readPhoto(capturedPhoto)
        onPhotoConsumed()
    }
    LaunchedEffect(s.added) {
        val n = s.added ?: return@LaunchedEffect
        FeedbackBus.show(FeedbackKind.ADDED, R.string.rx_added, Formats.number(context, n))
        onDone()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.read(it) } }
    fun pick() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    val drafts = s.drafts

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.rx_title), onBack = onBack, cancel = !drafts.isNullOrEmpty()) },
        bottomBar = {
            if (!drafts.isNullOrEmpty()) {
                Column(Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding()) {
                    PrimaryButton(
                        stringResource(R.string.rx_add, Formats.number(context, s.chosen.size)),
                        onClick = viewModel::add,
                        enabled = s.chosen.isNotEmpty() && !s.saving,
                    )
                }
            }
        },
    ) { padding ->
        when {
            s.reading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.rx_reading), style = MaterialTheme.typography.titleMedium)
                }
            }
            drafts.isNullOrEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedArt(Art.PRESCRIPTION, Hue.SKY, size = 112.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.rx_intro), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
                if (drafts != null) {
                    NoticeCard(stringResource(if (s.failed) R.string.rx_failed else R.string.rx_nothing), Icons.Default.Warning, Tone.WARNING)
                }
                PrimaryButton(stringResource(R.string.rx_camera), onClick = onTakePhoto, icon = Icons.Default.CameraAlt)
                SecondaryButton(stringResource(R.string.rx_gallery), onClick = ::pick, icon = Icons.Default.PhotoLibrary)
                NoticeCard(stringResource(R.string.rx_handwriting), Icons.Default.Info, Tone.INFO)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        Formats.digits(context, context.resources.getQuantityString(R.plurals.rx_found, drafts.size, drafts.size)),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(stringResource(R.string.rx_check), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                itemsIndexed(drafts) { i, d -> DraftCard(d, checked = i in s.chosen, onToggle = { viewModel.toggle(i) }, onSetUp = { onSetUp(d.medicine.id) }) }
                item { QuietButton(stringResource(R.string.rx_another), onClick = viewModel::reset, icon = Icons.Default.CameraAlt) }
            }
        }
    }
}

@Composable
private fun DraftCard(d: PrescriptionDraft, checked: Boolean, onToggle: () -> Unit, onSetUp: () -> Unit) {
    val context = LocalContext.current
    val m = d.medicine
    MediCard(onClick = if (d.ready) onToggle else null, contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = d.ready, modifier = Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Text(listOfNotNull(m.brandName, m.strength).joinToString(" "), style = MaterialTheme.typography.titleLarge)
                Text(listOfNotNull(m.dosageForm, m.genericName).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                if (d.ready) {
                    val i = d.instructions
                    Text(
                        listOfNotNull(
                            i.pattern?.let { Formats.digits(context, it) } ?: stringResource(R.string.wiz_needed),
                            i.meal?.let { Formats.mealTiming(context, it) },
                            i.days?.let { Formats.digits(context, context.resources.getQuantityString(R.plurals.days_count, it, it)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(stringResource(R.string.rx_no_dose), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    QuietButton(stringResource(R.string.rx_set_up), onClick = onSetUp, icon = Icons.Default.EditNote)
                }
                Text(stringResource(R.string.rx_read_as, d.line), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
