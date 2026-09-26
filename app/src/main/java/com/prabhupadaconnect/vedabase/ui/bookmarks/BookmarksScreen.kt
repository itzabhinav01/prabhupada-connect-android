package com.prabhupadaconnect.vedabase.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onOpenRecord: (String) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewCollectionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bookmarks") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewCollectionDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New collection")
            }
        }
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.padding(padding)) {
            item { Text("Uncategorized", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp)) }
            items(state.uncategorized, key = { it.id }) { bookmark ->
                BookmarkRow(bookmark.recordKey, bookmark.title, onOpenRecord, onRemove = { viewModel.removeBookmark(bookmark.recordKey) })
            }

            state.collections.forEach { collection ->
                item(key = "header_${collection.id}") {
                    Text(collection.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                }
                items(state.bookmarksIn(collection.id), key = { it.id }) { bookmark ->
                    BookmarkRow(bookmark.recordKey, bookmark.title, onOpenRecord, onRemove = { viewModel.removeBookmark(bookmark.recordKey) })
                }
            }
        }
    }

    if (showNewCollectionDialog) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNewCollectionDialog = false },
            title = { Text("New collection") },
            text = {
                androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.createCollection(name.trim())
                    showNewCollectionDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showNewCollectionDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BookmarkRow(recordKey: String, title: String?, onOpenRecord: (String) -> Unit, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(title ?: recordKey) },
        trailingContent = {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove bookmark") }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenRecord(recordKey) }
    )
    HorizontalDivider()
}
