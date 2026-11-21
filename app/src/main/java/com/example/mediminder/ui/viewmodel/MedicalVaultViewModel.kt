package com.example.mediminder.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.R
import com.example.mediminder.data.local.dao.VaultItemDao
import com.example.mediminder.data.local.entity.VaultItem
import com.example.mediminder.data.photos.MedicinePhotos
import com.example.mediminder.ui.components.FeedbackBus
import com.example.mediminder.ui.components.FeedbackKind
import com.example.mediminder.util.AppLocale
import com.example.mediminder.util.ErrorLog
import com.example.mediminder.util.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** Photos of prescriptions, test reports and cards, kept on this phone. */
@HiltViewModel
class MedicalVaultViewModel @Inject constructor(
    private val vaultItemDao: VaultItemDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val vaultItems = vaultItemDao.getAllVaultItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The name a new document gets unless the person types one: "Document, 21 Nov 2026". */
    fun defaultTitle(): String {
        val ctx = AppLocale.localized(context)
        return ctx.getString(R.string.vault_item_title, Formats.date(ctx, LocalDate.now()))
    }

    /**
     * Keeps a copy of the picture at [source] (upright, at most ~2400 px so the writing stays
     * readable) under the app's own storage. [cameraRef] is the camera's temporary photo, removed
     * once copied.
     */
    fun add(source: Uri, title: String, cameraRef: String? = null) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val bitmap = MedicinePhotos.decodeForReading(context, source, maxSide = 2400) ?: return@withContext false
                    val dir = File(context.filesDir, "documents").apply { mkdirs() }
                    val file = File(dir, "doc_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    bitmap.recycle()
                    vaultItemDao.insert(
                        VaultItem(title = title.trim().ifBlank { defaultTitle() }, dateAdded = System.currentTimeMillis(), localImagePath = file.absolutePath)
                    )
                    true
                } catch (e: Exception) {
                    ErrorLog.record("Documents", "Could not save a document", e)
                    false
                } finally {
                    cameraRef?.let { MedicinePhotos.delete(context, it) }
                }
            }
            if (saved) FeedbackBus.show(FeedbackKind.ADDED, R.string.vault_saved)
            else FeedbackBus.show(FeedbackKind.DELETED, R.string.vault_failed)
        }
    }

    /** The camera's photo was not kept after all. */
    fun discardCameraPhoto(cameraRef: String?) {
        cameraRef?.let { MedicinePhotos.delete(context, it) }
    }

    fun deleteVaultItem(item: VaultItem) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { File(item.localImagePath).takeIf { it.exists() }?.delete() }
                vaultItemDao.delete(item)
            } catch (e: Exception) {
                ErrorLog.record("Documents", "Could not remove a document", e)
            }
        }
    }
}
