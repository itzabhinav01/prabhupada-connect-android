package com.prabhupadaconnect.vedabase.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhupadaconnect.vedabase.data.corpus.CorpusRepository
import com.prabhupadaconnect.vedabase.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class RecentlyReadUiState(
    val isLoading: Boolean = true,
    val items: List<RecentlyReadItem> = emptyList(),
    val statusText: String = "Loading history..."
)

/**
 * Ported from the desktop app's `RecentlyReadViewModel` (C#) - same bounded
 * display slice (independent of user.db's own retention cap), same
 * single-batched-query enrichment of history rows with corpus context.
 */
@HiltViewModel
class RecentlyReadViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val corpusRepository: CorpusRepository
) : ViewModel() {

    // Bounded display list - independent of user.db's own retention cap. The
    // screen only ever needs to show a small recent slice.
    private val displayLimit = 100

    private val _uiState = MutableStateFlow(RecentlyReadUiState())
    val uiState: StateFlow<RecentlyReadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.observeRecentlyRead(displayLimit).collectLatest { history ->
                if (history.isEmpty()) {
                    _uiState.value = RecentlyReadUiState(isLoading = false, statusText = "Nothing here yet.")
                    return@collectLatest
                }

                // One batched corpus query instead of one query per row.
                val records = corpusRepository.getRecords(history.map { it.recordKey })
                val recordsByKey = records.associateBy { it.recordKey }
                val items = RecentlyReadMapper.buildItems(history, recordsByKey)

                _uiState.value = RecentlyReadUiState(
                    isLoading = false,
                    items = items,
                    statusText = "${items.size} recently read"
                )
            }
        }
    }

    fun clearHistory() = viewModelScope.launch {
        userRepository.clearReadingHistory()
    }
}
