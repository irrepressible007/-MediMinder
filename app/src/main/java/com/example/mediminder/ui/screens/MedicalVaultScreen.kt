package com.example.mediminder.ui.screens

import com.example.mediminder.ui.components.Art
import com.example.mediminder.ui.theme.Hue
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.ui.components.ConfirmDialog
import com.example.mediminder.ui.components.EmptyState
import com.example.mediminder.R
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.mediminder.data.local.entity.VaultItem
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.example.mediminder.ui.viewmodel.MedicalVaultViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalVaultScreen(
    onBack: () -> Unit,
    viewModel: MedicalVaultViewModel = hiltViewModel()
) {
    val vaultItems by viewModel.vaultItems.collectAsState()

    val cropImageLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            if (uriContent != null) {
                viewModel.addVaultItem(uriContent)
            }
        }
    }

    // Deleting a photo of a prescription can't be undone: always ask first
    var deleting by remember { mutableStateOf<VaultItem?>(null) }
    deleting?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.vault_delete_title),
            text = stringResource(R.string.vault_delete_body, item.title),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.deleteVaultItem(item) },
            onDismiss = { deleting = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = stringResource(R.string.vault_title), onBack = onBack)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.heightIn(min = 64.dp),
                onClick = {
                    cropImageLauncher.launch(
                        CropImageContractOptions(
                            uri = null,
                            cropImageOptions = CropImageOptions(
                                imageSourceIncludeGallery = true,
                                imageSourceIncludeCamera = true
                            )
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.vault_add), style = MaterialTheme.typography.labelLarge)
            }
        }
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
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(vaultItems) { item ->
                    VaultItemCard(
                        item = item,
                        onDelete = { deleting = item }
                    )
                }
            }
        }
    }
}

@Composable
fun VaultItemCard(item: VaultItem, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = File(item.localImagePath),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 300f
                        )
                    )
            )
            
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).size(56.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(50))
                        .padding(8.dp)
                        .size(24.dp)
                )
            }
            
            // Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}
