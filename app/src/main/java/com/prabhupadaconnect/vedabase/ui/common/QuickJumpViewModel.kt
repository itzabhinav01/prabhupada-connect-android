package com.prabhupadaconnect.vedabase.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhupadaconnect.vedabase.data.corpus.DirectReferenceService
import com.prabhupadaconnect.vedabase.data.corpus.ReferenceSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickJumpUiState(
    val query: String = "",
    val suggestions: List<ReferenceSuggestion> = emptyList()
)

/**
 * Backs the "@" direct-reference Quick Jump bar shared by the Library and
 * Search screens - a thin, self-contained wrapper over
 * [DirectReferenceService] so neither screen's own ViewModel needs to know
 * about reference-grammar parsing at all.
 */
@HiltViewModel
class QuickJumpViewModel @Inject constructor(
    private val directReferenceService: DirectReferenceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickJumpUiState())
    val uiState: StateFlow<QuickJumpUiState> = _uiState.asStateFlow()

    private var suggestionsJob: Job? = null

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        suggestionsJob?.cancel()

        if (!DirectReferenceService.isReferenceQuery(query)) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }

        suggestionsJob = viewModelScope.launch {
            delay(150)
            val suggestions = directReferenceService.getSuggestions(query)
            _uiState.update { it.copy(suggestions = suggestions) }
        }
    }

    fun clear() {
        suggestionsJob?.cancel()
        _uiState.value = QuickJumpUiState()
    }
}
