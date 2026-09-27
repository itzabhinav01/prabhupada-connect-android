package com.prabhupadaconnect.vedabase.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.core.model.UserNote

private fun NotesFilter.label(): String = when (this) {
    NotesFilter.All -> "All"
    NotesFilter.General -> "General"
    NotesFilter.ScriptureAnchored -> "Scripture-anchored"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    showTopBar: Boolean = true,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // null = dialog closed; a NoteEditorTarget = dialog open, either for a
    // brand-new note (existingId == null) or editing one already in the list.
    var editorTarget by remember { mutableStateOf<NoteEditorTarget?>(null) }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(title = { Text("Notes") })
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editorTarget = NoteEditorTarget(existingId = null, title = "", content = viewModel.prefillSelectedText.orEmpty())
            }) { Icon(Icons.Filled.Add, contentDescription = "New note") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(modifier = Modifier.padding(12.dp)) {
                NotesFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(f.label()) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            if (state.filtered.isEmpty()) {
                Text(
                    "No notes yet. Tap + to add a general research note, or select text while reading a verse.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp)
                )
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(state.filtered, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onClick = { editorTarget = NoteEditorTarget(note.id, note.title.orEmpty(), note.content) },
                        onDelete = { viewModel.deleteNote(note.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    editorTarget?.let { target ->
        NoteEditorDialog(
            target = target,
            onDismiss = { editorTarget = null },
            onSave = { newTitle, newContent ->
                if (target.existingId != null) {
                    viewModel.updateNote(target.existingId, newContent, newTitle)
                } else {
                    viewModel.createNote(newContent, newTitle)
                }
                editorTarget = null
            }
        )
    }
}

private data class NoteEditorTarget(val existingId: String?, val title: String, val content: String)

@Composable
private fun NoteRow(note: UserNote, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(note.title?.ifBlank { null } ?: note.content.take(48), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(note.content, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        overlineContent = { Text(if (note.recordKey != null) note.recordKey else "General note") },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete note")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun NoteEditorDialog(
    target: NoteEditorTarget,
    onDismiss: () -> Unit,
    onSave: (title: String?, content: String) -> Unit
) {
    var title by remember(target) { mutableStateOf(target.title) }
    var content by remember(target) { mutableStateOf(target.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.existingId != null) "Edit note" else "New note") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Title (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (content.isNotBlank()) onSave(title.ifBlank { null }, content.trim()) },
                enabled = content.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
