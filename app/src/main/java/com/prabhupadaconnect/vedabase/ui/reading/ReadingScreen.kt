package com.prabhupadaconnect.vedabase.ui.reading

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.core.model.CorpusRecord
import com.prabhupadaconnect.vedabase.ui.common.NoOpTextToolbar
import com.prabhupadaconnect.vedabase.ui.common.SelectionActionBar
import com.prabhupadaconnect.vedabase.ui.theme.bodyTextStyle
import com.prabhupadaconnect.vedabase.ui.theme.devanagariTextStyle
import com.prabhupadaconnect.vedabase.ui.theme.transliterationTextStyle
import com.prabhupadaconnect.vedabase.ui.theme.translationTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(
    recordKey: String,
    onNavigateBack: () -> Unit,
    viewModel: ReadingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.uiEvents.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var showNoteDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(recordKey) { viewModel.open(recordKey) }

    androidx.compose.runtime.LaunchedEffect(event) {
        when (event) {
            is ReadingUiEvent.OverlapRejected -> snackbarHostState.showSnackbar("That range overlaps an existing highlight")
            ReadingUiEvent.HighlightCreated -> snackbarHostState.showSnackbar("Highlight added")
            ReadingUiEvent.NoteCreated -> snackbarHostState.showSnackbar("Note saved")
            null -> Unit
        }
        if (event != null) viewModel.consumeEvent()
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            if (!state.settings.focusModeEnabled) {
                TopAppBar(
                    title = { Text(state.breadcrumb, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleBookmark() }) {
                            Icon(
                                if (state.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = "Toggle bookmark"
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFocusMode() }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "Focus mode")
                        }
                    }
                )
            }
        }
    ) { padding ->
        // Suppresses the OS copy/paste bubble entirely - the highlight/note
        // action menu is the plain SelectionActionBar below, driven by
        // `state.pendingSelection` directly. See HighlightableBlock's doc
        // comment for why a custom Popup-based TextToolbar was dropped.
        CompositionLocalProvider(LocalTextToolbar provides NoOpTextToolbar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(recordKey) {
                        var dragTotal = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onHorizontalDrag = { _, amount -> dragTotal += amount },
                            onDragEnd = {
                                if (dragTotal < -120f) viewModel.goToNext()
                                if (dragTotal > 120f) viewModel.goToPrevious()
                            }
                        )
                    }
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                    state.record == null -> Text("Verse not found", modifier = Modifier.padding(32.dp))
                    else -> ReadingContent(
                        record = state.record!!,
                        highlightsByField = state.highlightsByField,
                        settings = state.settings,
                        onSelectionChanged = { field, start, end, text ->
                            viewModel.onTextSelected(field, start, end - start, text)
                        }
                    )
                }

                if (!state.settings.focusModeEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { viewModel.goToPrevious() }, enabled = state.hasPrevious) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous verse")
                        }
                        IconButton(onClick = { viewModel.goToNext() }, enabled = state.hasNext) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next verse")
                        }
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.toggleFocusMode() },
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit focus mode")
                    }
                }

                SelectionActionBar(
                    visible = state.pendingSelection != null,
                    onHighlight = { color -> viewModel.createHighlight(color) },
                    onAddNote = { showNoteDialog = true },
                    onCopy = {
                        state.pendingSelection?.let { sel ->
                            val ref = state.record?.reference ?: recordKey
                            clipboard.setText(AnnotatedString("$ref\n\n${sel.selectedText}"))
                        }
                    },
                    onShare = { /* wired by the host Activity via an Intent.ACTION_SEND chooser */ },
                    onDismiss = { viewModel.clearPendingSelection() },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                )
            }
        }
    }

    if (showNoteDialog) {
        var noteText by remember(state.pendingSelection) { mutableStateOf(state.pendingSelection?.selectedText.orEmpty()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNoteDialog = false; viewModel.clearPendingSelection() },
            title = { Text("Add note") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.createNoteFromSelection(noteText)
                    showNoteDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showNoteDialog = false; viewModel.clearPendingSelection() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReadingContent(
    record: CorpusRecord,
    highlightsByField: Map<String, List<com.prabhupadaconnect.vedabase.core.model.Highlight>>,
    settings: com.prabhupadaconnect.vedabase.core.model.AppSettings,
    onSelectionChanged: (field: String, start: Int, end: Int, text: String) -> Unit
) {
    val maxWidth = when (settings.readingWidth) {
        com.prabhupadaconnect.vedabase.core.model.ReadingWidthOption.Narrow -> 480.dp
        com.prabhupadaconnect.vedabase.core.model.ReadingWidthOption.Comfortable -> 640.dp
        com.prabhupadaconnect.vedabase.core.model.ReadingWidthOption.Wide -> 900.dp
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = maxWidth)) {
                Text(record.reference ?: record.recordKey, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                if (record.hasDevanagari) {
                    Text(
                        record.devanagari,
                        style = devanagariTextStyle(settings.fontSize, settings.lineSpacing),
                        color = com.prabhupadaconnect.vedabase.ui.theme.SanskritChantingColor
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (settings.showTransliteration && record.hasTransliteration) {
                    HighlightableBlock(
                        text = record.transliteration,
                        highlights = highlightsByField[HighlightField.TRANSLITERATION].orEmpty(),
                        textStyle = transliterationTextStyle(settings.fontSize, settings.lineSpacing),
                        onSelectionChanged = { s, e, t -> onSelectionChanged(HighlightField.TRANSLITERATION, s, e, t) }
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (settings.showSynonyms && record.hasSynonyms) {
                    HighlightableBlock(
                        text = record.synonyms,
                        highlights = highlightsByField[HighlightField.SYNONYMS].orEmpty(),
                        textStyle = bodyTextStyle(settings.fontSize, settings.lineSpacing),
                        onSelectionChanged = { s, e, t -> onSelectionChanged(HighlightField.SYNONYMS, s, e, t) }
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (record.hasTranslation) {
                    HighlightableBlock(
                        text = record.cleanTranslation,
                        highlights = highlightsByField[HighlightField.TRANSLATION].orEmpty(),
                        textStyle = translationTextStyle(settings.fontSize, settings.lineSpacing),
                        onSelectionChanged = { s, e, t -> onSelectionChanged(HighlightField.TRANSLATION, s, e, t) }
                    )
                    Spacer(Modifier.height(20.dp))
                }

                if (settings.showPurport && record.hasPurports) {
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    record.purportParagraphs.forEachIndexed { index, paragraph ->
                        val field = HighlightField.purport(index)
                        HighlightableBlock(
                            text = paragraph,
                            highlights = highlightsByField[field].orEmpty(),
                            textStyle = bodyTextStyle(settings.fontSize, settings.lineSpacing),
                            onSelectionChanged = { s, e, t -> onSelectionChanged(field, s, e, t) }
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
