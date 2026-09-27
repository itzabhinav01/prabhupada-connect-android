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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prabhupadaconnect.vedabase.core.model.CorpusRecord
import com.prabhupadaconnect.vedabase.core.model.UserNote
import com.prabhupadaconnect.vedabase.core.util.CitationParser
import com.prabhupadaconnect.vedabase.core.util.ProseFormatter
import com.prabhupadaconnect.vedabase.core.util.PurportBlockDetector
import com.prabhupadaconnect.vedabase.core.util.SynonymsFormatter
import com.prabhupadaconnect.vedabase.ui.common.NoOpTextToolbar
import com.prabhupadaconnect.vedabase.ui.common.SelectionActionBar
import com.prabhupadaconnect.vedabase.ui.theme.bodyTextStyle
import com.prabhupadaconnect.vedabase.ui.theme.devanagariTextStyle
import com.prabhupadaconnect.vedabase.ui.theme.transliterationTextStyle
import com.prabhupadaconnect.vedabase.ui.theme.translationTextStyle

/** A pending note-editor invocation - null means the dialog is closed. */
private data class NoteDialogTarget(val existingId: String?, val initialContent: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(
    recordKey: String,
    onNavigateBack: () -> Unit,
    onNavigateToRecord: (String) -> Unit = {},
    viewModel: ReadingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.uiEvents.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var noteDialogTarget by remember { mutableStateOf<NoteDialogTarget?>(null) }

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
                Column {
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
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(if (state.isBookmarked) "Bookmarked" else "Bookmark verse") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleBookmark()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (state.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                            contentDescription = null
                                        )
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Add personal note") },
                                    onClick = {
                                        showMenu = false
                                        noteDialogTarget = NoteDialogTarget(existingId = null, initialContent = "")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Edit, contentDescription = null)
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Toggle focus mode") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleFocusMode()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Fullscreen, contentDescription = null)
                                    }
                                )
                            }
                        }
                    )
                    if (state.tabs.size > 1) {
                        ReadingTabBar(
                            tabs = state.tabs,
                            activeTabIndex = state.activeTabIndex,
                            onSelectTab = { viewModel.selectTab(it) },
                            onCloseTab = { viewModel.closeTab(it) }
                        )
                    }
                }
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
                        notes = state.notes,
                        settings = state.settings,
                        onSelectionChanged = { field, start, end, text ->
                            viewModel.onTextSelected(field, start, end - start, text)
                        },
                        onCitationTapped = { citation -> viewModel.resolveCitation(citation) },
                        onAddNoteForVerse = { noteDialogTarget = NoteDialogTarget(existingId = null, initialContent = "") },
                        onEditNote = { note -> noteDialogTarget = NoteDialogTarget(existingId = note.id, initialContent = note.content) },
                        onDeleteNote = { note -> viewModel.deleteNote(note.id) }
                    )
                }

                if (!state.settings.focusModeEnabled) {
                    androidx.compose.material3.Surface(
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.goToPrevious() },
                                enabled = state.hasPrevious,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                            ) {
                                Text("‹ Prev", fontWeight = FontWeight.SemiBold)
                            }

                            Text(
                                text = "swipe ‹ › or tap",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )

                            androidx.compose.material3.Button(
                                onClick = { viewModel.goToNext() },
                                enabled = state.hasNext,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFFE5A93C),
                                    contentColor = androidx.compose.ui.graphics.Color.Black
                                )
                            ) {
                                Text("Next ›", fontWeight = FontWeight.Bold)
                            }
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
                    onAddNote = { noteDialogTarget = NoteDialogTarget(existingId = null, initialContent = state.pendingSelection?.selectedText.orEmpty()) },
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

    noteDialogTarget?.let { target ->
        var noteText by remember(target) { mutableStateOf(target.initialContent) }
        val verseRef = state.record?.reference ?: state.record?.recordKey ?: "Verse"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { noteDialogTarget = null; viewModel.clearPendingSelection() },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            if (target.existingId != null) "Edit Note" else "Add Note",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            verseRef,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = {
                            Text(
                                "Write your reflection or realization...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        minLines = 4,
                        maxLines = 8,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        if (target.existingId != null) {
                            viewModel.updateNote(target.existingId, noteText, null)
                        } else {
                            viewModel.createNoteFromSelection(noteText)
                        }
                        noteDialogTarget = null
                    },
                    enabled = noteText.isNotBlank(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFE5A93C),
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    )
                ) {
                    Text("Save Note", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { noteDialogTarget = null; viewModel.clearPendingSelection() }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReadingContent(
    record: CorpusRecord,
    highlightsByField: Map<String, List<com.prabhupadaconnect.vedabase.core.model.Highlight>>,
    notes: List<UserNote>,
    settings: com.prabhupadaconnect.vedabase.core.model.AppSettings,
    onSelectionChanged: (field: String, start: Int, end: Int, text: String) -> Unit,
    onCitationTapped: (com.prabhupadaconnect.vedabase.core.util.CitationMatch) -> Unit,
    onAddNoteForVerse: () -> Unit,
    onEditNote: (UserNote) -> Unit,
    onDeleteNote: (UserNote) -> Unit
) {
    val maxWidth = when (settings.readingWidth) {
        com.prabhupadaconnect.vedabase.core.model.ReadingWidthOption.Narrow -> 480.dp
        com.prabhupadaconnect.vedabase.core.model.ReadingWidthOption.Comfortable -> 640.dp
        com.prabhupadaconnect.vedabase.core.model.ReadingWidthOption.Wide -> 900.dp
    }

    val songPayload = record.songPayload

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = maxWidth)) {
                Text(record.reference ?: record.recordKey, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                if (songPayload != null) {
                    // A structured song/mantra payload replaces the normal
                    // verse/synonyms/translation/purport stack entirely -
                    // its stanzas already carry their own transliteration,
                    // synonyms and translation, and rendering both would
                    // just duplicate the same content twice.
                    SongView(songPayload, settings)
                } else {
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
                        val lemmaColor = MaterialTheme.colorScheme.primary
                        val delimiterColor = MaterialTheme.colorScheme.onSurfaceVariant
                        HighlightableBlock(
                            text = record.synonyms,
                            baseAnnotated = remember(record.synonyms) {
                                SynonymsFormatter.format(record.synonyms, lemmaColor, delimiterColor)
                            },
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
                        record.purportParagraphPairs.forEachIndexed { index, paragraph ->
                            val field = HighlightField.purport(index)
                            val isQuote = remember(paragraph.raw) { PurportBlockDetector.isQuotedVerseParagraph(paragraph.raw) }
                            val paragraphText = remember(paragraph.raw, paragraph.cleaned, isQuote) {
                                if (isQuote) PurportBlockDetector.formatQuotedVerse(paragraph.raw) else paragraph.cleaned
                            }
                            val citations = remember(paragraphText) { CitationParser.findCitations(paragraphText) }

                            val block: @Composable () -> Unit = {
                                HighlightableBlock(
                                    text = paragraphText,
                                    highlights = highlightsByField[field].orEmpty(),
                                    textStyle = if (isQuote) {
                                        transliterationTextStyle(settings.fontSize, settings.lineSpacing)
                                    } else {
                                        bodyTextStyle(settings.fontSize, settings.lineSpacing)
                                    },
                                    citations = citations,
                                    onTap = { offset ->
                                        citations.firstOrNull { offset in it.range }?.let(onCitationTapped)
                                    },
                                    onSelectionChanged = { s, e, t -> onSelectionChanged(field, s, e, t) }
                                )
                            }

                            if (isQuote) {
                                QuoteBlockCard { block() }
                            } else {
                                block()
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                PersonalNotesSection(
                    notes = notes,
                    onAddNote = onAddNoteForVerse,
                    onEditNote = onEditNote,
                    onDeleteNote = onDeleteNote
                )
            }
        }
    }
}

@Composable
private fun PersonalNotesSection(
    notes: List<UserNote>,
    onAddNote: () -> Unit,
    onEditNote: (UserNote) -> Unit,
    onDeleteNote: (UserNote) -> Unit
) {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    Text("Personal Notes", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    if (notes.isEmpty()) {
        Text(
            "No notes on this verse yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
    } else {
        notes.forEach { note ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.Top
                ) {
                    Text(
                        note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        IconButton(onClick = { onEditNote(note) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit note")
                        }
                        IconButton(onClick = { onDeleteNote(note) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    OutlinedButton(onClick = onAddNote) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Add note on this verse")
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ReadingTabBar(
    tabs: List<ReadingTab>,
    activeTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs.size) { index ->
                val tab = tabs[index]
                val selected = index == activeTabIndex
                androidx.compose.material3.InputChip(
                    selected = selected,
                    onClick = { onSelectTab(index) },
                    label = {
                        Text(
                            tab.title,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onCloseTab(index) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close tab",
                                modifier = Modifier.size(13.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = androidx.compose.material3.InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

