package com.prabhupadaconnect.vedabase.ui.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhupadaconnect.vedabase.core.model.HighlightColor
import com.prabhupadaconnect.vedabase.data.corpus.CorpusRepository
import com.prabhupadaconnect.vedabase.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HighlightsUiState(
    val isLoading: Boolean = true,
    val allItems: List<HighlightListItem> = emptyList(),
    val selectedColor: HighlightColor? = null,
    val statusText: String = "Loading highlights..."
) {
    /** null selectedColor means "All". */
    val filteredItems: List<HighlightListItem> get() = HighlightsMapper.filterByColor(allItems, selectedColor)
}

/**
 * Backs the dedicated Highlights workspace screen - a centralized view/search/
 * manage surface across all scriptures, ported from the desktop app's
 * `HighlightsViewModel` (C#): same "only show colors/books actually present"
 * philosophy, applied here as simple All/Yellow/Green/Blue filter chips.
 */
@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val corpusRepository: CorpusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HighlightsUiState())
    val uiState: StateFlow<HighlightsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.observeAllHighlights().collectLatest { highlights ->
                if (highlights.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, allItems = emptyList(), statusText = "No highlights yet.") }
                    return@collectLatest
                }

                val records = corpusRepository.getRecords(highlights.map { it.recordKey })
                val recordsByKey = records.associateBy { it.recordKey }
                val items = HighlightsMapper.buildItems(highlights, recordsByKey)

                _uiState.update {
                    it.copy(isLoading = false, allItems = items, statusText = "${items.size} highlights")
                }
            }
        }
    }

    fun selectColor(color: HighlightColor?) = _uiState.update { it.copy(selectedColor = color) }

    fun removeHighlight(highlightId: String) = viewModelScope.launch {
        userRepository.removeHighlight(highlightId)
    }
}
