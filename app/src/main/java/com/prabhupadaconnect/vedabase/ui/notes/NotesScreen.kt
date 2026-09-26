package com.prabhupadaconnect.vedabase.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun NotesScreen(viewModel: NotesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Notes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showEditor = true }) { Icon(Icons.Filled.Add, contentDescription = "New note") }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(padding)) {
            androidx.compose.foundation.layout.Row(modifier = Modifier.padding(12.dp)) {
                NotesFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(f.name) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(state.filtered, key = { it.id }) { note ->
                    ListItem(
                        headlineContent = { Text(note.title ?: note.content.take(48)) },
                        supportingContent = { Text(note.content, maxLines = 2) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showEditor) {
        var content by remember { mutableStateOf(viewModel.prefillSelectedText.orEmpty()) }
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("New note") },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Title (optional)") })
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (content.isNotBlank()) viewModel.createNote(content.trim(), title.ifBlank { null })
                    showEditor = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("Cancel") } }
        )
    }
}
