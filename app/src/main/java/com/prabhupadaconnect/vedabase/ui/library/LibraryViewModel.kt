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

/** One of the Library's top-of-screen browsing shortcuts (vedabase.io-style category cards). */
data class LibraryCategory(
    val title: String,
    val emoji: String,
    val bookKeys: List<String>
)

val libraryCategories: List<LibraryCategory> = listOf(
    LibraryCategory("Major Scriptures", "📖", listOf("BG", "SB", "CC")),
    LibraryCategory("Foundational Books", "📚", listOf("NOD", "NOI", "ISO")),
    LibraryCategory("Songs & Mantras", "🎶", listOf("SVA", "TMG")),
    LibraryCategory("Conversations & Essays", "🗣️", listOf("SSR", "PQPA", "BTG"))
)

data class LibraryUiState(
    val isLoading: Boolean = true,
    val books: List<BookNode> = emptyList(),
    val expandedBookKeys: Set<String> = emptySet(),
    val expandedGroupKeys: Set<String> = emptySet(),
    val expandedChapterTitles: Set<String> = emptySet(),
    /** Set briefly after a category card is tapped, so the screen can scroll to that book once and then clear it. */
    val scrollToBookKey: String? = null
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

    fun toggleGroup(key: String) {
        _uiState.value = _uiState.value.let { s ->
            val expanded = s.expandedGroupKeys.toMutableSet()
            if (!expanded.add(key)) expanded.remove(key)
            s.copy(expandedGroupKeys = expanded)
        }
    }

    fun toggleChapter(key: String) {
        _uiState.value = _uiState.value.let { s ->
            val expanded = s.expandedChapterTitles.toMutableSet()
            if (!expanded.add(key)) expanded.remove(key)
            s.copy(expandedChapterTitles = expanded)
        }
    }

    /** A category card was tapped - expand its first book and request a one-shot scroll to it. */
    fun onCategorySelected(category: LibraryCategory) {
        val firstAvailable = category.bookKeys.firstOrNull { key -> _uiState.value.books.any { it.bookKey == key } }
            ?: return
        _uiState.value = _uiState.value.let { s ->
            s.copy(
                expandedBookKeys = s.expandedBookKeys + firstAvailable,
                scrollToBookKey = firstAvailable
            )
        }
    }

    fun consumeScrollRequest() {
        _uiState.value = _uiState.value.copy(scrollToBookKey = null)
    }
}
