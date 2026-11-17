package com.example.mediminder.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.ui.photos.takeUprightPhoto
import com.example.mediminder.ui.photos.rememberCameraPermission
import com.example.mediminder.ui.photos.CameraView
import com.example.mediminder.data.photos.MedicinePhotos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.camera.core.ImageCapture
import com.example.mediminder.data.catalogue.CatalogueMedicine
import com.example.mediminder.data.catalogue.CatalogueRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/** Matches text read off a medicine box against the catalogue. */
@HiltViewModel
class ScannerViewModel @Inject constructor(private val catalogue: CatalogueRepository) : ViewModel() {
    private val _matches = MutableStateFlow<List<CatalogueMedicine>>(emptyList())
    val matches: StateFlow<List<CatalogueMedicine>> = _matches.asStateFlow()
    private var lastText = ""

    fun onText(text: String) {
        if (text == lastText) return
        lastText = text
        viewModelScope.launch {
            val found = catalogue.matchScannedText(text)
            // Keep earlier finds so the list doesn't flicker as the camera moves
            if (found.isNotEmpty()) _matches.value = (found + _matches.value).distinctBy { it.id }.take(12)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScannerScreen(
    onBack: () -> Unit,
    /** The medicine tapped, and a photo of its box taken at the same moment (if the camera allowed). */
    onPicked: (catalogueId: Long, photoRef: String?) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasPermission = rememberCameraPermission()
    val matches by viewModel.matches.collectAsState()
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val analyzer = remember { OcrAnalyzer { text -> ContextCompat.getMainExecutor(context).execute { viewModel.onText(text) } } }
    var saving by remember { mutableStateOf(false) }

    fun pick(id: Long) {
        if (saving) return
        saving = true
        scope.launch {
            val photo = capture.takeUprightPhoto(context)?.let { MedicinePhotos.save(context, it) }
            onPicked(id, photo)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scanner_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (hasPermission) {
                CameraView(capture = capture, analyzer = analyzer, modifier = Modifier.fillMaxSize())
            } else {
                Text(stringResource(R.string.scanner_permission), color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (saving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.photo_saving), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    return@Column
                }
                Text(
                    stringResource(if (matches.isEmpty()) R.string.scanner_hint else R.string.scanner_pick),
                    color = Color.White, style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(matches, key = { it.id }) { m ->
                        Column(
                            Modifier.fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                .clickable { pick(m.id) }
                                .heightIn(min = 64.dp)
                                .padding(14.dp)
                        ) {
                            Text("${m.brandName} ${m.strength.orEmpty()}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(listOfNotNull(m.dosageForm, m.genericName, m.manufacturer).joinToString(" · "), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** Runs on-device text recognition (ML Kit) at most once a second. */
class OcrAnalyzer(private val onTextFound: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastScannedTime = 0L

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val mediaImage = imageProxy.image
        if (now - lastScannedTime < 1000 || mediaImage == null) {
            imageProxy.close()
            return
        }
        lastScannedTime = now
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { visionText -> if (visionText.text.isNotBlank()) onTextFound(visionText.text) }
            .addOnCompleteListener { imageProxy.close() }
    }
}
