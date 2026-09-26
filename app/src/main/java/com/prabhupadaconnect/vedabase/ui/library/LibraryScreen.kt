package com.prabhupadaconnect.vedabase.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.core.model.BookNode

/** Hierarchical scripture browser: Book -> Chapter -> Verse, in canonical Prabhupāda reading order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenRecord: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Library") }) }) { padding ->
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            return@Scaffold
        }

        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.padding(padding)) {
            items(state.books, key = { it.bookKey }) { book ->
                BookRow(
                    book = book,
                    expanded = book.bookKey in state.expandedBookKeys,
                    expandedChapters = state.expandedChapterTitles,
                    onToggleBook = { viewModel.toggleBook(book.bookKey) },
                    onToggleChapter = { viewModel.toggleChapter("${book.bookKey}::$it") },
                    onOpenRecord = onOpenRecord
                )
            }
        }
    }
}

@Composable
private fun BookRow(
    book: BookNode,
    expanded: Boolean,
    expandedChapters: Set<String>,
    onToggleBook: () -> Unit,
    onToggleChapter: (String) -> Unit,
    onOpenRecord: (String) -> Unit
) {
    Column {
        ListItem(
            headlineContent = { Text(book.title) },
            supportingContent = { Text("${book.chapters.sumOf { it.records.size }} verses") },
            leadingContent = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
            trailingContent = { Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleBook)
        )

        if (expanded) {
            book.chapters.forEach { chapter ->
                val chapterKey = chapter.title
                val chapterExpanded = chapterKey in expandedChapters
                ListItem(
                    headlineContent = { Text(chapter.title, style = MaterialTheme.typography.bodyMedium) },
                    trailingContent = {
                        Icon(if (chapterExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp)
                        .clickable { onToggleChapter(chapterKey) }
                )

                if (chapterExpanded) {
                    chapter.records.forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRecord(record.recordKey) }
                                .padding(start = 48.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(record.reference, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
