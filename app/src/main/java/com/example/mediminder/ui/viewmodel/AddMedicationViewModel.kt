package com.example.mediminder.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediminder.data.local.dao.MedicineDictionaryDao
import com.example.mediminder.data.local.entity.MedicineDictionary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AddMedicationViewModel @Inject constructor(
    private val dao: MedicineDictionaryDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<MedicineDictionary>> = _searchQuery
        .flatMapLatest { query ->
            if (query.length >= 2) {
                dao.searchMedicine(query)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
