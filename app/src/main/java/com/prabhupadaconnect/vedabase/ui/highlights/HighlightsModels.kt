package com.prabhupadaconnect.vedabase.ui.highlights

import com.prabhupadaconnect.vedabase.core.model.CorpusRecord
import com.prabhupadaconnect.vedabase.core.model.Highlight
import com.prabhupadaconnect.vedabase.core.model.HighlightColor
import com.prabhupadaconnect.vedabase.core.registry.BookRegistry
import java.time.Instant

/**
 * One highlight in the "all highlights" workspace list, with enough corpus
 * context to display and to navigate back to its exact source passage -
 * ported from the desktop app's `HighlightListItem` (C#).
 */
data class HighlightListItem(
    val id: String,
    val recordKey: String,
    val field: String,
    val color: HighlightColor,
    val createdUtc: Instant,
    val reference: String,
    val bookTitle: String,
    val bookKey: String,
    val isLegacyBlockLevel: Boolean,
    val snippetDisplay: String
)

/**
 * Pure mapping/filtering for the Highlights workspace, extracted from the
 * ViewModel so it can be unit tested without Room or Hilt.
 */
object HighlightsMapper {

    private const val SNIPPET_MAX_LENGTH = 80

    fun buildItems(
        highlights: List<Highlight>,
        recordsByKey: Map<String, CorpusRecord>
    ): List<HighlightListItem> = highlights.map { h ->
        val record = recordsByKey[h.recordKey]
        val bookKey = record?.bookKey ?: ""
        HighlightListItem(
            id = h.id,
            recordKey = h.recordKey,
            field = h.field,
            color = h.color,
            createdUtc = h.createdUtc,
            reference = record?.reference?.takeIf { it.isNotBlank() } ?: h.recordKey,
            bookTitle = if (record != null) BookRegistry.getBookTitle(bookKey) else "Record unavailable",
            bookKey = bookKey,
            isLegacyBlockLevel = h.isLegacyBlockLevel,
            snippetDisplay = if (h.isLegacyBlockLevel) {
                "[${h.field} - whole section highlighted]"
            } else {
                "“${truncate(h.selectedText)}”"
            }
        )
    }

    /** Ported 1:1 from the desktop app's `HighlightsViewModel.ApplyFilter` color predicate. */
    fun filterByColor(items: List<HighlightListItem>, color: HighlightColor?): List<HighlightListItem> =
        if (color == null) items else items.filter { it.color == color }

    private fun truncate(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return if (s.length <= SNIPPET_MAX_LENGTH) s else s.substring(0, SNIPPET_MAX_LENGTH) + "…"
    }
}
