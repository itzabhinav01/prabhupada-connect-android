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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.core.registry.BookRegistry
import com.prabhupadaconnect.vedabase.ui.common.QuickJumpBar
import com.prabhupadaconnect.vedabase.ui.common.snippetToAnnotatedString

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
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                placeholder = { Text("Search scripture or @bg 1.1, @sb 1.1.1…") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = {
                        val target = state.exactJumpRecordKey
                            ?: state.directSuggestions.firstOrNull { it.recordKey != null }?.recordKey
                        target?.let(onOpenRecord)
                    }
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Direct Jump banner if an exact verse reference was identified
            if (state.exactJumpRecordKey != null) {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE5A93C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onOpenRecord(state.exactJumpRecordKey!!) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Jump directly to ${state.query.trim().removePrefix("@").uppercase()}",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            // Direct Reference suggestions dropdown
            if (state.directSuggestions.isNotEmpty()) {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(state.directSuggestions, key = { it.queryToComplete }) { suggestion ->
                            ListItem(
                                headlineContent = { Text(suggestion.displayText, fontWeight = if (suggestion.recordKey != null) FontWeight.Bold else FontWeight.Normal) },
                                supportingContent = if (suggestion.subText.isNotBlank()) {
                                    { Text(suggestion.subText) }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (suggestion.recordKey != null) {
                                            onOpenRecord(suggestion.recordKey)
                                        } else {
                                            viewModel.onQueryChanged(suggestion.queryToComplete)
                                        }
                                    }
                            )
                        }
                    }
                }
            }

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
                            supportingContent = {
                                if (result.preview.isNotBlank()) {
                                    Text(snippetToAnnotatedString(result.preview), maxLines = 2)
                                } else {
                                    Text(result.bookTitle, maxLines = 2)
                                }
                            },
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
