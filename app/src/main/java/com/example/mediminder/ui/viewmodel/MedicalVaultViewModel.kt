package com.example.mediminder.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.data.local.dao.VaultItemDao
import com.example.mediminder.data.local.entity.VaultItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MedicalVaultViewModel @Inject constructor(
    private val vaultItemDao: VaultItemDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val vaultItems = vaultItemDao.getAllVaultItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addVaultItem(uri: Uri) {
        viewModelScope.launch {
            try {
                // Copy to internal storage
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val fileName = "vault_${System.currentTimeMillis()}.jpg"
                    val file = File(context.filesDir, fileName)
                    val outputStream = FileOutputStream(file)
                    
                    inputStream.copyTo(outputStream)
                    
                    inputStream.close()
                    outputStream.close()
                    
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val dateStr = dateFormat.format(Date())

                    // Save to DB
                    val newItem = VaultItem(
                        title = "Document on $dateStr",
                        dateAdded = System.currentTimeMillis(),
                        localImagePath = file.absolutePath
                    )
                    vaultItemDao.insert(newItem)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun deleteVaultItem(item: VaultItem) {
        viewModelScope.launch {
            try {
                val file = File(item.localImagePath)
                if (file.exists()) {
                    file.delete()
                }
                vaultItemDao.delete(item)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
