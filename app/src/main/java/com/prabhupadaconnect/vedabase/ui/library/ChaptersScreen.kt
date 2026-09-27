package com.prabhupadaconnect.vedabase.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prabhupadaconnect.vedabase.core.model.BookGroupNode
import com.prabhupadaconnect.vedabase.core.model.BookNode
import com.prabhupadaconnect.vedabase.core.model.ChapterNode

/**
 * Dedicated window for selecting a chapter or section within a book,
 * matching Reference Image 2 (no expanding accordions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(
    book: BookNode,
    onNavigateBack: () -> Unit,
    onSelectChapter: (groupTitle: String?, chapter: ChapterNode) -> Unit
) {
    // If the book has groups (like SB's 12 Cantos or CC's 3 Lilas)
    val hasGroups = book.hasGroups
    var selectedGroupIndex by remember { mutableStateOf(0) }

    val activeChapters: List<ChapterNode> = if (hasGroups) {
        if (selectedGroupIndex in book.groups.indices) {
            book.groups[selectedGroupIndex].chapters
        } else {
            emptyList()
        }
    } else {
        book.chapters
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Group selector chips for multi-canto / multi-lila books (e.g. Canto 1, Canto 2...)
            if (hasGroups) {
                Text(
                    text = if (book.bookKey == "CC") "LĪLĀ" else "CANTO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    itemsIndexed(book.groups) { index, group ->
                        FilterChip(
                            selected = selectedGroupIndex == index,
                            onClick = { selectedGroupIndex = index },
                            label = { Text(group.title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            val groupTitle = if (hasGroups && selectedGroupIndex in book.groups.indices) {
                book.groups[selectedGroupIndex].title
            } else null

            if (groupTitle != null) {
                Text(
                    text = groupTitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(activeChapters) { index, chapter ->
                    ChapterCard(
                        fallbackIndex = index + 1,
                        chapter = chapter,
                        onClick = { onSelectChapter(groupTitle, chapter) }
                    )
                }
            }
        }
    }
}

private data class ChapterBadge(
    val label: String,
    val isChapterNumber: Boolean
)

private fun resolveChapterBadge(title: String, fallbackNumber: Int): ChapterBadge {
    val lower = title.lowercase().trim()
    val chMatch = Regex("""(?:Chapter|Ch\.|Adhyāya)\s+(\d+)""", RegexOption.IGNORE_CASE).find(title)
    if (chMatch != null) {
        return ChapterBadge(chMatch.groupValues[1], isChapterNumber = true)
    }
    val leadingNum = Regex("""^(\d+)[\s.:\-]""").find(title.trim())
    if (leadingNum != null) {
        return ChapterBadge(leadingNum.groupValues[1], isChapterNumber = true)
    }
    if (lower.contains("introduction") || lower.contains("intro")) {
        return ChapterBadge("Intro", isChapterNumber = false)
    }
    if (lower.contains("preface")) {
        return ChapterBadge("Pref", isChapterNumber = false)
    }
    if (lower.contains("dedication")) {
        return ChapterBadge("Ded", isChapterNumber = false)
    }
    if (lower.contains("setting the scene")) {
        return ChapterBadge("Scene", isChapterNumber = false)
    }
    if (lower.contains("foreword")) {
        return ChapterBadge("Fore", isChapterNumber = false)
    }
    if (lower.contains("prologue")) {
        return ChapterBadge("Pro", isChapterNumber = false)
    }
    if (lower.contains("epilogue")) {
        return ChapterBadge("Epi", isChapterNumber = false)
    }
    if (lower.contains("appendix")) {
        return ChapterBadge("App", isChapterNumber = false)
    }
    return ChapterBadge(fallbackNumber.toString(), isChapterNumber = true)
}

@Composable
private fun ChapterCard(
    fallbackIndex: Int,
    chapter: ChapterNode,
    onClick: () -> Unit
) {
    val badge = remember(chapter.title, fallbackIndex) {
        resolveChapterBadge(chapter.title, fallbackIndex)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge on the left: vibrant blue for real chapter numbers, slate for intro/preface
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (badge.isChapterNumber) Color(0xFF1976D2)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge.label,
                    color = if (badge.isChapterNumber) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    style = if (badge.isChapterNumber) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${chapter.records.size} verses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
