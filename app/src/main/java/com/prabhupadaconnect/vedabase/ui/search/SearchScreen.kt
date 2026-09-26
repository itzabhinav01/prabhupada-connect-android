package com.prabhupadaconnect.vedabase.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.core.registry.BookRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenRecord: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Search") }) }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search scripture, notes, bookmarks…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                SearchTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        text = { Text(if (tab == SearchTab.Scripture) "Scripture" else "My Research") }
                    )
                }
            }

            if (state.selectedTab == SearchTab.Scripture) {
                BookFilterRow(
                    selected = state.selectedBookFilters,
                    onToggle = viewModel::toggleBookFilter,
                    onClearAll = viewModel::clearAllFilters
                )
            }

            if (state.isSearching) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }

            when (state.selectedTab) {
                SearchTab.Scripture -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.scriptureResults, key = { it.recordKey }) { result ->
                        ListItem(
                            headlineContent = { Text(result.reference, fontWeight = if (result.isExactMatch) FontWeight.Bold else FontWeight.Normal) },
                            supportingContent = { Text(result.preview.ifBlank { result.bookTitle }, maxLines = 2) },
                            overlineContent = { Text(result.bookTitle) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRecord(result.recordKey) }
                        )
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
                SearchTab.Research -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.researchResults) { result ->
                        ListItem(
                            headlineContent = { Text(result.sourceType) },
                            supportingContent = { Text(result.contentSnippet, maxLines = 3) }
                        )
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BookFilterRow(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClearAll: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            FilterChip(
                selected = selected.isEmpty(),
                onClick = onClearAll,
                label = { Text("All Books") }
            )
        }
        items(BookRegistry.canonicalBookOrder.filter { it != "UNKNOWN" }) { bookKey ->
            val descriptor = BookRegistry.getBook(bookKey)
            FilterChip(
                selected = bookKey in selected,
                onClick = { onToggle(bookKey) },
                label = { Text(descriptor?.abbreviation ?: bookKey) }
            )
        }
    }
}
