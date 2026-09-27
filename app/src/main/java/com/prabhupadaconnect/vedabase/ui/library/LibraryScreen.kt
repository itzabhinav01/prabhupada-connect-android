package com.prabhupadaconnect.vedabase.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.core.model.BookGroupNode
import com.prabhupadaconnect.vedabase.core.model.BookNode
import com.prabhupadaconnect.vedabase.core.model.ChapterNode
import com.prabhupadaconnect.vedabase.ui.common.QuickJumpBar

// One header item (Quick Jump bar) + one header item (category cards) precede the book list itself.
private const val HEADER_ITEM_COUNT = 2

/** Hierarchical scripture browser: Book -> (Canto/līlā) -> Chapter -> Verse, in canonical Prabhupāda reading order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenRecord: (String) -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenHighlights: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.scrollToBookKey) {
        val targetKey = state.scrollToBookKey ?: return@LaunchedEffect
        val index = state.books.indexOfFirst { it.bookKey == targetKey }
        if (index >= 0) listState.animateScrollToItem(index + HEADER_ITEM_COUNT)
        viewModel.consumeScrollRequest()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onOpenHighlights) {
                        Icon(Icons.Filled.Highlight, contentDescription = "Highlights")
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "Recently read")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.padding(padding)
        ) {
            item { QuickJumpBar(onOpenRecord = onOpenRecord) }
            item {
                CategoryCardsRow(onCategoryClick = viewModel::onCategorySelected)
            }
            items(state.books, key = { it.bookKey }) { book ->
                BookRow(
                    book = book,
                    expanded = book.bookKey in state.expandedBookKeys,
                    expandedGroups = state.expandedGroupKeys,
                    expandedChapters = state.expandedChapterTitles,
                    onToggleBook = { viewModel.toggleBook(book.bookKey) },
                    onToggleGroup = { viewModel.toggleGroup(it) },
                    onToggleChapter = { viewModel.toggleChapter(it) },
                    onOpenRecord = onOpenRecord
                )
            }
        }
    }
}

@Composable
private fun CategoryCardsRow(onCategoryClick: (LibraryCategory) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(libraryCategories, key = { it.title }) { category ->
            Card(
                modifier = Modifier
                    .width(150.dp)
                    .padding(end = 12.dp)
                    .clickable { onCategoryClick(category) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(category.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        category.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun iconForBook(book: BookNode) = when {
    book.bookKey == "SVA" || book.bookKey == "TMG" -> Icons.Filled.MusicNote
    book.category == "Conversations" -> Icons.Filled.Forum
    book.category == "Essays & Articles" -> Icons.Filled.Article
    book.category == "Philosophy" -> Icons.Filled.Psychology
    book.category == "Biographies" -> Icons.Filled.Person
    else -> Icons.Filled.MenuBook
}

@Composable
private fun BookRow(
    book: BookNode,
    expanded: Boolean,
    expandedGroups: Set<String>,
    expandedChapters: Set<String>,
    onToggleBook: () -> Unit,
    onToggleGroup: (String) -> Unit,
    onToggleChapter: (String) -> Unit,
    onOpenRecord: (String) -> Unit
) {
    Column {
        ListItem(
            headlineContent = { Text(book.title) },
            supportingContent = { Text("${book.verseCount} verses") },
            leadingContent = { Icon(iconForBook(book), contentDescription = null) },
            trailingContent = { Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleBook)
        )

        if (expanded) {
            if (book.hasGroups) {
                book.groups.forEach { group ->
                    GroupSection(
                        bookKey = book.bookKey,
                        group = group,
                        expandedGroups = expandedGroups,
                        expandedChapters = expandedChapters,
                        onToggleGroup = onToggleGroup,
                        onToggleChapter = onToggleChapter,
                        onOpenRecord = onOpenRecord
                    )
                }
            } else {
                book.chapters.forEach { chapter ->
                    ChapterSection(
                        chapterKey = "${book.bookKey}::${chapter.title}",
                        chapter = chapter,
                        expandedChapters = expandedChapters,
                        onToggleChapter = onToggleChapter,
                        onOpenRecord = onOpenRecord,
                        chapterIndent = 24.dp,
                        verseIndent = 48.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupSection(
    bookKey: String,
    group: BookGroupNode,
    expandedGroups: Set<String>,
    expandedChapters: Set<String>,
    onToggleGroup: (String) -> Unit,
    onToggleChapter: (String) -> Unit,
    onOpenRecord: (String) -> Unit
) {
    val groupKey = "$bookKey::${group.title}"
    val groupExpanded = groupKey in expandedGroups

    ListItem(
        headlineContent = { Text(group.title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text("${group.chapters.sumOf { it.records.size }} verses") },
        trailingContent = {
            Icon(if (groupExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .clickable { onToggleGroup(groupKey) }
    )

    if (groupExpanded) {
        group.chapters.forEach { chapter ->
            ChapterSection(
                chapterKey = "$groupKey::${chapter.title}",
                chapter = chapter,
                expandedChapters = expandedChapters,
                onToggleChapter = onToggleChapter,
                onOpenRecord = onOpenRecord,
                chapterIndent = 32.dp,
                verseIndent = 56.dp
            )
        }
    }
}

/**
 * A chapter with exactly one verse renders as a direct, immediately
 * clickable row - no expand chevron for a single child that would just
 * repeat the chapter's own title (e.g. Nectar of Devotion's chapters).
 */
@Composable
private fun ChapterSection(
    chapterKey: String,
    chapter: ChapterNode,
    expandedChapters: Set<String>,
    onToggleChapter: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    chapterIndent: androidx.compose.ui.unit.Dp,
    verseIndent: androidx.compose.ui.unit.Dp
) {
    if (!chapter.hasMultipleRecords) {
        val onlyRecordKey = chapter.records.firstOrNull()?.recordKey
        ListItem(
            headlineContent = { Text(chapter.title, style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = chapterIndent)
                .clickable(enabled = onlyRecordKey != null) { onlyRecordKey?.let(onOpenRecord) }
        )
        return
    }

    val chapterExpanded = chapterKey in expandedChapters
    ListItem(
        headlineContent = { Text(chapter.title, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = {
            Icon(if (chapterExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = chapterIndent)
            .clickable { onToggleChapter(chapterKey) }
    )

    if (chapterExpanded) {
        chapter.records.forEach { record ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRecord(record.recordKey) }
                    .padding(start = verseIndent, top = 8.dp, bottom = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(record.reference, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
