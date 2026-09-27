package com.prabhupadaconnect.vedabase.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhupadaconnect.vedabase.core.model.SearchResult
import com.prabhupadaconnect.vedabase.core.model.UserSearchResult
import com.prabhupadaconnect.vedabase.core.registry.BookRegistry
import com.prabhupadaconnect.vedabase.data.corpus.CorpusRepository
import com.prabhupadaconnect.vedabase.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class SearchTab { Scripture, Research }

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val selectedTab: SearchTab = SearchTab.Scripture,
    val availableBooks: List<String> = BookRegistry.canonicalBookOrder,
    val selectedBookFilters: Set<String> = emptySet(),
    val isExactWord: Boolean = false,
    val isExactCase: Boolean = false,
    val sortOrder: String = "relevance",
    val scriptureResults: List<SearchResult> = emptyList(),
    val scriptureTotalCount: Int = 0,
    val researchResults: List<UserSearchResult> = emptyList()
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val corpusRepository: CorpusRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /**
     * Every parameter a search actually depends on, bundled into one value so
     * [distinctUntilChanged] dedupes on the whole search, not just the typed
     * text. `queryFlow` used to hold just the query string - toggling a book
     * filter chip (or exact-word, exact-case, sort order, tab) re-published
     * that *same* string, which `distinctUntilChanged` then swallowed as a
     * no-op, so the filter was recorded in [SearchUiState] but a re-search
     * with it never actually ran until the user also edited the query text.
     */
    private data class SearchTrigger(
        val query: String,
        val tab: SearchTab,
        val bookFilters: Set<String>,
        val isExactWord: Boolean,
        val isExactCase: Boolean,
        val sortOrder: String
    )

    private val triggerFlow = MutableStateFlow(SearchTrigger("", SearchTab.Scripture, emptySet(), false, false, "relevance"))
    private var searchJob: Job? = null

    init {
        triggerFlow
            .debounce(250)
            .distinctUntilChanged()
            .onEach { runSearch(it) }
            .launchIn(viewModelScope)
    }

    private fun pushTrigger() {
        val s = _uiState.value
        triggerFlow.value = SearchTrigger(s.query, s.selectedTab, s.selectedBookFilters, s.isExactWord, s.isExactCase, s.sortOrder)
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        pushTrigger()
    }

    fun onTabSelected(tab: SearchTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        pushTrigger()
    }

    fun toggleBookFilter(bookKey: String) {
        val current = _uiState.value.selectedBookFilters.toMutableSet()
        if (!current.add(bookKey)) current.remove(bookKey)
        _uiState.value = _uiState.value.copy(selectedBookFilters = current)
        pushTrigger()
    }

    fun clearAllFilters() {
        _uiState.value = _uiState.value.copy(selectedBookFilters = emptySet())
        pushTrigger()
    }

    fun setExactWord(value: Boolean) {
        _uiState.value = _uiState.value.copy(isExactWord = value)
        pushTrigger()
    }

    fun setExactCase(value: Boolean) {
        _uiState.value = _uiState.value.copy(isExactCase = value)
        pushTrigger()
    }

    fun setSortOrder(order: String) {
        _uiState.value = _uiState.value.copy(sortOrder = order)
        pushTrigger()
    }

    private fun runSearch(trigger: SearchTrigger) {
        searchJob?.cancel()
        if (trigger.query.isBlank()) {
            _uiState.value = _uiState.value.copy(scriptureResults = emptyList(), scriptureTotalCount = 0, researchResults = emptyList(), isSearching = false)
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)

            when (trigger.tab) {
                SearchTab.Scripture -> {
                    val outcome = corpusRepository.search(
                        query = trigger.query,
                        bookKeys = trigger.bookFilters.toList().ifEmpty { null },
                        isExactWord = trigger.isExactWord,
                        isExactCase = trigger.isExactCase,
                        sortOrder = trigger.sortOrder
                    )
                    _uiState.value = _uiState.value.copy(
                        scriptureResults = outcome.results,
                        scriptureTotalCount = outcome.totalCount,
                        isSearching = false
                    )
                }
                SearchTab.Research -> {
                    // One-shot snapshot search over personal research data (notes,
                    // highlights, bookmarks) - simple substring matching, since this
                    // is bounded, locally-owned data rather than the 50k+ record
                    // canonical corpus that needs FTS5.
                    val notes = userRepository.observeAllNotes().first()
                    val results = notes
                        .filter { it.content.contains(trigger.query, ignoreCase = true) || it.title?.contains(trigger.query, ignoreCase = true) == true }
                        .map {
                            UserSearchResult(
                                recordKey = it.recordKey,
                                sourceType = "Note",
                                contentSnippet = it.content.take(160),
                                timestampUtc = it.updatedUtc
                            )
                        }
                    _uiState.value = _uiState.value.copy(researchResults = results, isSearching = false)
                }
            }
        }
    }
}
