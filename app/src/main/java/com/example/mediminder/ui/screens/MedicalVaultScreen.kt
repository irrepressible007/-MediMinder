package com.example.mediminder.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.mediminder.R
import com.example.mediminder.data.local.entity.VaultItem
import com.example.mediminder.data.photos.MedicinePhotos
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.components.ConfirmDialog
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.ui.components.PrimaryButton
import com.example.mediminder.ui.components.SecondaryButton
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.viewmodel.MedicalVaultViewModel
import java.io.File

/** A picture waiting to be named and saved: from the gallery, or the app's camera ([cameraRef]). */
private data class Pending(val uri: Uri, val cameraRef: String?)

/**
 * Photos of prescriptions, test reports and cards. Add one with the camera or from the gallery,
 * name it, save it; tap one to see it full size.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalVaultScreen(
    onBack: () -> Unit,
    capturedPhoto: String?,
    onPhotoConsumed: () -> Unit,
    onTakePhoto: () -> Unit,
    viewModel: MedicalVaultViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val vaultItems by viewModel.vaultItems.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var viewing by remember { mutableStateOf<VaultItem?>(null) }
    var deleting by remember { mutableStateOf<VaultItem?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pending = Pending(uri, cameraRef = null)
    }
    // Back from the app's camera with a photo
    LaunchedEffect(capturedPhoto) {
        val ref = capturedPhoto ?: return@LaunchedEffect
        MedicinePhotos.file(context, ref)?.let { pending = Pending(Uri.fromFile(it), ref) }
        onPhotoConsumed()
    }

    if (choosing) {
        ModalBottomSheet(onDismissRequest = { choosing = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.vault_add), style = MaterialTheme.typography.headlineSmall)
                PrimaryButton(stringResource(R.string.rx_camera), onClick = { choosing = false; onTakePhoto() }, icon = Icons.Default.CameraAlt)
                SecondaryButton(
                    stringResource(R.string.rx_gallery),
                    onClick = {
                        choosing = false
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    icon = Icons.Default.PhotoLibrary,
                )
            }
        }
    }

    pending?.let { p ->
        SaveDocumentDialog(
            uri = p.uri,
            defaultTitle = remember(p) { viewModel.defaultTitle() },
            onSave = { title -> viewModel.add(p.uri, title, p.cameraRef); pending = null },
            onCancel = { viewModel.discardCameraPhoto(p.cameraRef); pending = null },
        )
    }

    viewing?.let { item -> DocumentViewer(item, onClose = { viewing = null }, onDelete = { viewing = null; deleting = item }) }

    // Deleting a photo of a prescription can't be undone: always ask first
    deleting?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.vault_delete_title),
            text = stringResource(R.string.vault_delete_body, item.title),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.deleteVaultItem(item); deleting = null },
            onDismiss = { deleting = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(R.string.vault_title), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.heightIn(min = 64.dp),
                onClick = { choosing = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.vault_add), style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { padding ->
        if (vaultItems.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Folder,
                hue = Hue.AMBER,
                art = Art.FOLDER,
                title = stringResource(R.string.vault_title),
                body = stringResource(R.string.vault_empty),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(vaultItems, key = { it.id }) { item ->
                    VaultItemCard(item = item, onOpen = { viewing = item }, onDelete = { deleting = item })
                }
            }
        }
    }
}

/** The picture just chosen, with a name to give it: Save or Cancel. */
@Composable
private fun SaveDocumentDialog(uri: Uri, defaultTitle: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(defaultTitle) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.vault_review_title), style = MaterialTheme.typography.headlineSmall)
            AsyncImage(
                model = uri,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(60) },
                label = { Text(stringResource(R.string.vault_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(stringResource(R.string.action_cancel), onClick = onCancel, modifier = Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.action_save), onClick = { onSave(title) }, modifier = Modifier.weight(1.4f))
            }
        }
    }
}

/** One document, full size. */
@Composable
private fun DocumentViewer(item: VaultItem, onClose: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = File(item.localImagePath),
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White)
                }
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun VaultItemCard(item: VaultItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = File(item.localImagePath),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = 300f)
                )
            )
            IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).size(56.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(50)).padding(8.dp).size(24.dp),
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
    }
}
