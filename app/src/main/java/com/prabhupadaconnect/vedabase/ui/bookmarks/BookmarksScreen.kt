package com.prabhupadaconnect.vedabase.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.core.model.BookmarkCollection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onOpenRecord: (String) -> Unit,
    onOpenHighlights: () -> Unit = {},
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewCollectionDialog by remember { mutableStateOf(false) }
    var moveDialogRecordKey by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                actions = {
                    IconButton(onClick = onOpenHighlights) {
                        Icon(Icons.Filled.Highlight, contentDescription = "Highlights")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewCollectionDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New collection")
            }
        }
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.padding(padding)) {
            item { Text("Uncategorized", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp)) }
            items(state.uncategorized, key = { it.id }) { bookmark ->
                BookmarkRow(
                    recordKey = bookmark.recordKey,
                    title = bookmark.title,
                    onOpenRecord = onOpenRecord,
                    onRemove = { viewModel.removeBookmark(bookmark.recordKey) },
                    onMoveToCollection = { moveDialogRecordKey = bookmark.recordKey }
                )
            }

            state.collections.forEach { collection ->
                item(key = "header_${collection.id}") {
                    Text(collection.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                }
                items(state.bookmarksIn(collection.id), key = { it.id }) { bookmark ->
                    BookmarkRow(
                        recordKey = bookmark.recordKey,
                        title = bookmark.title,
                        onOpenRecord = onOpenRecord,
                        onRemove = { viewModel.removeBookmark(bookmark.recordKey) },
                        onMoveToCollection = { moveDialogRecordKey = bookmark.recordKey }
                    )
                }
            }
        }
    }

    if (showNewCollectionDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCollectionDialog = false },
            title = { Text("New collection") },
            text = {
                androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.createCollection(name.trim())
                    showNewCollectionDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCollectionDialog = false }) { Text("Cancel") }
            }
        )
    }

    moveDialogRecordKey?.let { recordKey ->
        val currentCollectionId = state.bookmarks.firstOrNull { it.recordKey == recordKey }?.collectionId
        MoveToCollectionDialog(
            collections = state.collections,
            currentCollectionId = currentCollectionId,
            onDismiss = { moveDialogRecordKey = null },
            onSelect = { collectionId ->
                viewModel.moveToCollection(recordKey, collectionId)
                moveDialogRecordKey = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToCollectionDialog(
    collections: List<BookmarkCollection>,
    currentCollectionId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to collection") },
        text = {
            Column {
                CollectionOptionRow(
                    label = "Uncategorized",
                    selected = currentCollectionId == null,
                    onClick = { onSelect(null) }
                )
                collections.forEach { collection ->
                    CollectionOptionRow(
                        label = collection.name,
                        selected = currentCollectionId == collection.id,
                        onClick = { onSelect(collection.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CollectionOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkRow(
    recordKey: String,
    title: String?,
    onOpenRecord: (String) -> Unit,
    onRemove: () -> Unit,
    onMoveToCollection: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title ?: recordKey) },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Bookmark options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Move to collection") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onMoveToCollection()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenRecord(recordKey) }
    )
    HorizontalDivider()
}
