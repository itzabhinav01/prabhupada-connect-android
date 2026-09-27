package com.prabhupadaconnect.vedabase.ui.reading

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhupadaconnect.vedabase.core.model.AppSettings
import com.prabhupadaconnect.vedabase.core.model.CorpusRecord
import com.prabhupadaconnect.vedabase.core.model.Highlight
import com.prabhupadaconnect.vedabase.core.model.HighlightColor
import com.prabhupadaconnect.vedabase.core.model.UserNote
import com.prabhupadaconnect.vedabase.core.util.CitationMatch
import com.prabhupadaconnect.vedabase.data.corpus.CorpusRepository
import com.prabhupadaconnect.vedabase.data.corpus.DirectReferenceService
import com.prabhupadaconnect.vedabase.data.settings.SettingsDataStore
import com.prabhupadaconnect.vedabase.data.user.UserRepository
import com.prabhupadaconnect.vedabase.highlight.HighlightRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A field identifier for a highlight/note anchor - "Transliteration" | "Synonyms" | "Translation" | "Purport:{index}". */
object HighlightField {
    const val TRANSLITERATION = "Transliteration"
    const val SYNONYMS = "Synonyms"
    const val TRANSLATION = "Translation"
    fun purport(index: Int) = "Purport:$index"
}

data class PendingSelection(
    val field: String,
    val startOffset: Int,
    val length: Int,
    val selectedText: String
)

sealed interface ReadingUiEvent {
    data class OverlapRejected(val existing: Highlight) : ReadingUiEvent
    data object HighlightCreated : ReadingUiEvent
    data object NoteCreated : ReadingUiEvent
}

data class ReadingTab(
    val recordKey: String,
    val title: String
)

data class ReadingUiState(
    val isLoading: Boolean = true,
    val record: CorpusRecord? = null,
    val breadcrumb: String = "",
    val isBookmarked: Boolean = false,
    val highlightsByField: Map<String, List<Highlight>> = emptyMap(),
    val notes: List<UserNote> = emptyList(),
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val pendingSelection: PendingSelection? = null,
    val tabs: List<ReadingTab> = emptyList(),
    val activeTabIndex: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val corpusRepository: CorpusRepository,
    private val userRepository: UserRepository,
    private val settingsDataStore: SettingsDataStore,
    private val directReferenceService: DirectReferenceService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialKey = savedStateHandle.get<String>("recordKey") ?: "BG-1-1"
    private val recordKey = MutableStateFlow(initialKey)
    private val pendingSelection = MutableStateFlow<PendingSelection?>(null)
    private val events = MutableStateFlow<ReadingUiEvent?>(null)
    val uiEvents: StateFlow<ReadingUiEvent?> = events.asStateFlow()

    private val tabsState = MutableStateFlow<List<ReadingTab>>(listOf(ReadingTab(initialKey, initialKey)))
    private val activeTabIndexState = MutableStateFlow(0)

    private val recordState = MutableStateFlow<CorpusRecord?>(null)
    private val breadcrumbState = MutableStateFlow("")
    private val adjacencyState = MutableStateFlow(false to false)
    private val isLoadingState = MutableStateFlow(true)

    private val highlightsFlow = recordKey.flatMapLatest { key ->
        userRepository.observeHighlightsForRecord(key)
    }
    private val notesFlow = recordKey.flatMapLatest { key ->
        userRepository.observeNotesForRecord(key)
    }
    private val bookmarkFlow = recordKey.flatMapLatest { key ->
        // Bookmarks table is small; re-derive membership on every active-list emission.
        userRepository.observeActiveBookmarks()
    }

    private val tabsFlow = combine(tabsState, activeTabIndexState) { tabs, activeIdx ->
        tabs to activeIdx
    }

    // Split into two ≤5-arg groups and combine those - kotlinx.coroutines only
    // provides typed `combine` overloads up to 5 flows; beyond that its
    // vararg overload collapses every flow to a single shared element type,
    // which is unnecessary risk here when two typed combines compose cleanly.
    private data class RecordGroup(
        val record: CorpusRecord?,
        val breadcrumb: String,
        val adjacency: Pair<Boolean, Boolean>,
        val isLoading: Boolean,
        val highlights: List<Highlight>
    )

    private data class ContextGroup(
        val notes: List<UserNote>,
        val bookmarks: List<com.prabhupadaconnect.vedabase.core.model.UserBookmark>,
        val settings: AppSettings,
        val pending: PendingSelection?,
        val tabsInfo: Pair<List<ReadingTab>, Int>
    )

    private val recordGroup = combine(
        recordState, breadcrumbState, adjacencyState, isLoadingState, highlightsFlow
    ) { record, breadcrumb, adjacency, isLoading, highlights ->
        RecordGroup(record, breadcrumb, adjacency, isLoading, highlights)
    }

    private val contextGroup = combine(
        notesFlow, bookmarkFlow, settingsDataStore.settings, pendingSelection, tabsFlow
    ) { notes, bookmarks, settings, pending, tabsInfo ->
        ContextGroup(notes, bookmarks, settings, pending, tabsInfo)
    }

    val uiState: StateFlow<ReadingUiState> = combine(recordGroup, contextGroup) { r, c ->
        ReadingUiState(
            isLoading = r.isLoading,
            record = r.record,
            breadcrumb = r.breadcrumb,
            isBookmarked = r.record != null && c.bookmarks.any { it.recordKey == r.record.recordKey },
            highlightsByField = r.highlights.groupBy { it.field },
            notes = c.notes,
            hasPrevious = r.adjacency.first,
            hasNext = r.adjacency.second,
            settings = c.settings,
            pendingSelection = c.pending,
            tabs = c.tabsInfo.first,
            activeTabIndex = c.tabsInfo.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingUiState())

    init {
        viewModelScope.launch { recordKey.collect { loadRecord(it) } }
    }

    private suspend fun loadRecord(key: String) {
        isLoadingState.value = true
        val record = corpusRepository.getRecord(key)
        recordState.value = record
        breadcrumbState.value = record?.let { corpusRepository.getCanonicalChapterHeader(it.bookKey, it.reference) } ?: ""
        val prev = record?.let { corpusRepository.getAdjacentRecordKey(it.recordKey, next = false) }
        val next = record?.let { corpusRepository.getAdjacentRecordKey(it.recordKey, next = true) }
        adjacencyState.value = (prev != null) to (next != null)
        isLoadingState.value = false
        if (record != null) {
            userRepository.recordOpened(record.recordKey)
            val currentTabs = tabsState.value.toMutableList()
            val activeIdx = activeTabIndexState.value
            if (activeIdx in currentTabs.indices && currentTabs[activeIdx].recordKey == key) {
                currentTabs[activeIdx] = currentTabs[activeIdx].copy(
                    title = record.reference?.ifEmpty { record.recordKey } ?: record.recordKey
                )
                tabsState.value = currentTabs
            }
        }
    }

    fun selectTab(index: Int) {
        val tabs = tabsState.value
        if (index in tabs.indices) {
            activeTabIndexState.value = index
            recordKey.value = tabs[index].recordKey
        }
    }

    fun closeTab(index: Int) {
        val currentTabs = tabsState.value.toMutableList()
        if (currentTabs.size <= 1) return
        if (index in currentTabs.indices) {
            val currentActive = activeTabIndexState.value
            currentTabs.removeAt(index)
            val newActive = when {
                currentActive == index -> if (index >= currentTabs.size) currentTabs.size - 1 else index
                currentActive > index -> currentActive - 1
                else -> currentActive
            }
            tabsState.value = currentTabs
            activeTabIndexState.value = newActive
            recordKey.value = currentTabs[newActive].recordKey
        }
    }

    fun openInNewTab(targetRecordKey: String) {
        val currentTabs = tabsState.value.toMutableList()
        val existingIndex = currentTabs.indexOfFirst { it.recordKey == targetRecordKey }
        if (existingIndex != -1) {
            selectTab(existingIndex)
            return
        }
        if (currentTabs.size < 3) {
            currentTabs.add(ReadingTab(targetRecordKey, targetRecordKey))
            val newIndex = currentTabs.lastIndex
            tabsState.value = currentTabs
            activeTabIndexState.value = newIndex
            recordKey.value = targetRecordKey
        } else {
            val replaceIndex = activeTabIndexState.value.coerceIn(0, currentTabs.size - 1)
            currentTabs[replaceIndex] = ReadingTab(targetRecordKey, targetRecordKey)
            tabsState.value = currentTabs
            recordKey.value = targetRecordKey
        }
    }

    fun open(newRecordKey: String) {
        val activeIdx = activeTabIndexState.value
        val currentTabs = tabsState.value.toMutableList()
        if (currentTabs.isEmpty()) {
            tabsState.value = listOf(ReadingTab(newRecordKey, newRecordKey))
            activeTabIndexState.value = 0
        } else if (activeIdx in currentTabs.indices) {
            currentTabs[activeIdx] = ReadingTab(newRecordKey, newRecordKey)
            tabsState.value = currentTabs
        }
        recordKey.value = newRecordKey
    }

    fun goToNext() = viewModelScope.launch {
        val next = recordState.value?.let { corpusRepository.getAdjacentRecordKey(it.recordKey, next = true) }
        if (next != null) {
            val activeIdx = activeTabIndexState.value
            val currentTabs = tabsState.value.toMutableList()
            if (activeIdx in currentTabs.indices) {
                currentTabs[activeIdx] = currentTabs[activeIdx].copy(recordKey = next, title = next)
                tabsState.value = currentTabs
            }
            recordKey.value = next
        }
    }

    fun goToPrevious() = viewModelScope.launch {
        val prev = recordState.value?.let { corpusRepository.getAdjacentRecordKey(it.recordKey, next = false) }
        if (prev != null) {
            val activeIdx = activeTabIndexState.value
            val currentTabs = tabsState.value.toMutableList()
            if (activeIdx in currentTabs.indices) {
                currentTabs[activeIdx] = currentTabs[activeIdx].copy(recordKey = prev, title = prev)
                tabsState.value = currentTabs
            }
            recordKey.value = prev
        }
    }

    fun toggleBookmark() = viewModelScope.launch {
        val key = recordState.value?.recordKey ?: return@launch
        if (userRepository.isBookmarked(key)) userRepository.removeBookmark(key) else userRepository.addBookmark(key)
    }

    fun toggleFocusMode() = viewModelScope.launch {
        settingsDataStore.setFocusMode(!uiState.value.settings.focusModeEnabled)
    }

    fun onTextSelected(field: String, startOffset: Int, length: Int, selectedText: String) {
        pendingSelection.value = PendingSelection(field, startOffset, length, selectedText)
    }

    fun clearPendingSelection() {
        pendingSelection.value = null
    }

    /** Rejects an overlapping highlight exactly as the desktop app's ReadingViewModel does - never merges, never silently allows it. */
    fun createHighlight(color: HighlightColor) = viewModelScope.launch {
        val selection = pendingSelection.value ?: return@launch
        val record = recordState.value ?: return@launch

        val existing = userRepository.getActiveHighlightsForField(record.recordKey, selection.field)
        val overlap = HighlightRenderer.findOverlap(existing, selection.startOffset, selection.length)
        if (overlap != null) {
            events.value = ReadingUiEvent.OverlapRejected(overlap)
            return@launch
        }

        userRepository.addHighlight(
            recordKey = record.recordKey,
            field = selection.field,
            startOffset = selection.startOffset,
            length = selection.length,
            selectedText = selection.selectedText,
            color = color
        )
        pendingSelection.value = null
        events.value = ReadingUiEvent.HighlightCreated
    }

    fun removeHighlight(highlightId: String) = viewModelScope.launch {
        userRepository.removeHighlight(highlightId)
    }

    fun createNoteFromSelection(content: String, title: String? = null) = viewModelScope.launch {
        val record = recordState.value ?: return@launch
        val selection = pendingSelection.value
        userRepository.createNote(
            recordKey = record.recordKey,
            content = content,
            title = title,
            field = selection?.field,
            startOffset = selection?.startOffset ?: -1,
            length = selection?.length ?: -1
        )
        pendingSelection.value = null
        events.value = ReadingUiEvent.NoteCreated
    }

    fun updateNote(id: String, content: String, title: String?) = viewModelScope.launch {
        userRepository.updateNote(id, content, title)
    }

    fun deleteNote(id: String) = viewModelScope.launch {
        userRepository.deleteNote(id)
    }

    /**
     * Resolves an in-purport citation ("Bg. 4.1", "Cc. Madhya 20.108") tapped
     * by the reader to a navigable RecordKey via the same [DirectReferenceService]
     * index the "@" quick-jump bar uses, and hands it to [onResolved] - a
     * miss (a citation to a work/verse this corpus doesn't have) is silently
     * ignored rather than navigating nowhere or showing an error for what is,
     * from the reader's perspective, just inert unstyled text.
     */
    fun resolveCitation(citation: CitationMatch, onResolved: ((String) -> Unit)? = null) = viewModelScope.launch {
        val resolvedKey = directReferenceService.tryResolveExact("@${citation.bookKey} ${citation.numbers}")
        if (resolvedKey != null) {
            openInNewTab(resolvedKey)
            onResolved?.invoke(resolvedKey)
        }
    }

    fun consumeEvent() {
        events.value = null
    }
}
