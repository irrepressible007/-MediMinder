package com.example.mediminder.ui.photos

import com.example.mediminder.util.ErrorLog
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mediminder.R
import com.example.mediminder.data.photos.MedicinePhotos
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.SecondaryButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** True once the camera may be used; asks for it the first time. */
@Composable
fun rememberCameraPermission(): Boolean {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    return granted
}

/**
 * The back camera, full screen. Binds a photo use case when [capture] is given and a live
 * analyser when [analyzer] is given. If the phone can't run all three at once, the photo
 * is dropped first — reading the name matters more.
 */
@Composable
fun CameraView(capture: ImageCapture?, analyzer: ImageAnalysis.Analyzer?, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = analyzer?.let {
                    ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        .also { a -> a.setAnalyzer(executor, it) }
                }
                fun bind(vararg cases: androidx.camera.core.UseCase?) {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *cases.filterNotNull().toTypedArray())
                }
                try {
                    bind(preview, analysis, capture)
                } catch (e: Exception) {
                    ErrorLog.record("Camera", "All camera uses at once failed; trying without the photo", e)
                    runCatching { bind(preview, analysis ?: capture) }.onFailure { ErrorLog.record("Camera", "The camera could not start", it) }
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

/** Takes one photo and returns it upright, or null if the camera couldn't. */
suspend fun ImageCapture.takeUprightPhoto(context: Context): Bitmap? = suspendCancellableCoroutine { cont ->
    try {
        takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = try {
                    val raw = image.toBitmap()
                    val degrees = image.imageInfo.rotationDegrees
                    if (degrees == 0) raw
                    else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
                } catch (e: Exception) {
                    ErrorLog.record("Camera", "Could not read the photo", e)
                    null
                } finally {
                    image.close()
                }
                if (cont.isActive) cont.resume(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                ErrorLog.record("Camera", "Taking the photo failed", exception)
                if (cont.isActive) cont.resume(null)
            }
        })
    } catch (e: Exception) {
        // Not bound (camera fell back to preview only)
        if (cont.isActive) cont.resume(null)
    }
}

/** "Take a photo of the box or strip": one big shutter, then keep it or take it again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureScreen(onBack: () -> Unit, onCaptured: (photoRef: String) -> Unit, maxSide: Int = 1280) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasPermission = rememberCameraPermission()
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var taken by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.photo_capture_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), modifier = Modifier.size(28.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val photo = taken
                when {
                    photo != null -> Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    hasPermission -> {
                        CameraView(capture = capture, analyzer = null, modifier = Modifier.fillMaxSize())
                        // A frame to put the box or strip in
                        Box(
                            Modifier.align(Alignment.Center).fillMaxWidth(0.82f).aspectRatio(4f / 3f)
                                .border(BorderStroke(3.dp, Color.White.copy(alpha = 0.9f)), MaterialTheme.shapes.large)
                        )
                    }
                    else -> Text(
                        stringResource(R.string.scanner_permission), color = Color.White, style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            Column(
                Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val photo = taken
                if (photo == null) {
                    Text(stringResource(R.string.photo_hint), color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    FilledIconButton(
                        onClick = {
                            if (busy) return@FilledIconButton
                            busy = true
                            scope.launch {
                                taken = capture.takeUprightPhoto(context)
                                busy = false
                            }
                        },
                        enabled = hasPermission && !busy,
                        modifier = Modifier.size(84.dp).border(4.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) { Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.cd_take_photo), modifier = Modifier.size(38.dp)) }
                    Text(stringResource(R.string.cd_take_photo), color = Color.White, style = MaterialTheme.typography.labelLarge)
                } else {
                    PrimaryButton(
                        text = stringResource(R.string.photo_use),
                        icon = Icons.Default.Check,
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val ref = MedicinePhotos.save(context, photo, maxSide)
                                busy = false
                                onCaptured(ref)
                            }
                        },
                    )
                    SecondaryButton(stringResource(R.string.photo_retake), onClick = { taken = null }, icon = Icons.Default.Replay, contentColor = Color.White)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** The medicine's photo, or nothing — callers decide the fallback. */
@Composable
fun MedicinePhoto(ref: String?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val model = remember(ref) { MedicinePhotos.model(context, ref) } ?: return
    coil.compose.AsyncImage(model = model, contentDescription = contentDescription, contentScale = contentScale, modifier = modifier.clip(MaterialTheme.shapes.medium))
}
