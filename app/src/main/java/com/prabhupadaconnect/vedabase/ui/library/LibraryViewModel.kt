package com.prabhupadaconnect.vedabase.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhupadaconnect.vedabase.core.model.BookNode
import com.prabhupadaconnect.vedabase.data.corpus.CorpusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = true,
    val books: List<BookNode> = emptyList(),
    val expandedBookKeys: Set<String> = emptySet(),
    val expandedChapterTitles: Set<String> = emptySet()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val corpusRepository: CorpusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val hierarchy = corpusRepository.getLibraryHierarchy()
            _uiState.value = _uiState.value.copy(isLoading = false, books = hierarchy)
        }
    }

    fun toggleBook(bookKey: String) {
        _uiState.value = _uiState.value.let { s ->
            val expanded = s.expandedBookKeys.toMutableSet()
            if (!expanded.add(bookKey)) expanded.remove(bookKey)
            s.copy(expandedBookKeys = expanded)
        }
    }

    fun toggleChapter(key: String) {
        _uiState.value = _uiState.value.let { s ->
            val expanded = s.expandedChapterTitles.toMutableSet()
            if (!expanded.add(key)) expanded.remove(key)
            s.copy(expandedChapterTitles = expanded)
        }
    }
}
