package com.prabhupadaconnect.vedabase.ui.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.core.model.HighlightColor

private fun solidColorFor(color: HighlightColor): Color = when (color) {
    HighlightColor.Yellow -> Color(0xFFFDD835)
    HighlightColor.Green -> Color(0xFF4CAF50)
    HighlightColor.Blue -> Color(0xFF42A5F5)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(
    onOpenRecord: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: HighlightsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Highlights") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.allItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.statusText, style = MaterialTheme.typography.bodyMedium)
                }
                else -> Column {
                    ColorFilterRow(
                        selected = state.selectedColor,
                        onSelect = viewModel::selectColor
                    )
                    Text(
                        text = if (state.filteredItems.size == 1) "1 highlight" else "${state.filteredItems.size} highlights",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(state.filteredItems, key = { it.id }) { item ->
                            HighlightRow(
                                item = item,
                                onOpen = { onOpenRecord(item.recordKey) },
                                onDelete = { viewModel.removeHighlight(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorFilterRow(selected: HighlightColor?, onSelect: (HighlightColor?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        HighlightColor.entries.forEach { color ->
            FilterChip(
                selected = selected == color,
                onClick = { onSelect(color) },
                label = { Text(color.name) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(solidColorFor(color), CircleShape)
                    )
                },
                colors = FilterChipDefaults.filterChipColors()
            )
        }
    }
}

@Composable
private fun HighlightRow(item: HighlightListItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        leadingContent = {
            Box(
                Modifier
                    .size(16.dp)
                    .background(solidColorFor(item.color), CircleShape)
            )
        },
        headlineContent = { Text("${item.bookTitle} ${item.reference}") },
        supportingContent = { Text(item.snippetDisplay) },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove highlight") }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    )
    HorizontalDivider()
}
