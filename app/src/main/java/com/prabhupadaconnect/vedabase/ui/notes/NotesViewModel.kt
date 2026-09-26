package com.prabhupadaconnect.vedabase.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhupadaconnect.vedabase.core.model.UserNote
import com.prabhupadaconnect.vedabase.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NotesFilter { All, General, ScriptureAnchored }

data class NotesUiState(
    val notes: List<UserNote> = emptyList(),
    val filter: NotesFilter = NotesFilter.All
) {
    val filtered: List<UserNote>
        get() = when (filter) {
            NotesFilter.All -> notes
            NotesFilter.General -> notes.filter { it.recordKey == null }
            NotesFilter.ScriptureAnchored -> notes.filter { it.recordKey != null }
        }
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filter = kotlinx.coroutines.flow.MutableStateFlow(NotesFilter.All)

    val uiState: StateFlow<NotesUiState> = kotlinx.coroutines.flow.combine(
        userRepository.observeAllNotes(), filter
    ) { notes, f -> NotesUiState(notes, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotesUiState())

    // Pre-filled from a Reading-screen "Add Note" action, if any.
    val prefillRecordKey: String? = savedStateHandle.get<String>("recordKey")
    val prefillField: String? = savedStateHandle.get<String>("field")
    val prefillStart: Int = savedStateHandle.get<Int>("start") ?: -1
    val prefillLength: Int = savedStateHandle.get<Int>("length") ?: -1
    val prefillSelectedText: String? = savedStateHandle.get<String>("selectedText")

    fun setFilter(f: NotesFilter) {
        filter.value = f
    }

    fun createNote(content: String, title: String?) = viewModelScope.launch {
        userRepository.createNote(
            recordKey = prefillRecordKey,
            content = content,
            title = title,
            field = prefillField,
            startOffset = prefillStart,
            length = prefillLength
        )
    }

    fun updateNote(id: String, content: String, title: String?) = viewModelScope.launch {
        userRepository.updateNote(id, content, title)
    }

    fun deleteNote(id: String) = viewModelScope.launch {
        userRepository.deleteNote(id)
    }
}
