package com.prabhupadaconnect.vedabase.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhupadaconnect.vedabase.core.model.BookmarkCollection
import com.prabhupadaconnect.vedabase.core.model.UserBookmark
import com.prabhupadaconnect.vedabase.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookmarksUiState(
    val collections: List<BookmarkCollection> = emptyList(),
    val bookmarks: List<UserBookmark> = emptyList()
) {
    val uncategorized: List<UserBookmark> get() = bookmarks.filter { it.collectionId == null }
    fun bookmarksIn(collectionId: String): List<UserBookmark> = bookmarks.filter { it.collectionId == collectionId }
}

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    val uiState: StateFlow<BookmarksUiState> = combine(
        userRepository.observeCollections(),
        userRepository.observeActiveBookmarks()
    ) { collections, bookmarks -> BookmarksUiState(collections, bookmarks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookmarksUiState())

    fun createCollection(name: String) = viewModelScope.launch {
        val nextOrder = uiState.value.collections.size
        userRepository.createCollection(name, nextOrder)
    }

    fun moveToCollection(recordKey: String, collectionId: String?) = viewModelScope.launch {
        userRepository.setBookmarkCollection(recordKey, collectionId)
    }

    fun removeBookmark(recordKey: String) = viewModelScope.launch {
        userRepository.removeBookmark(recordKey)
    }
}
