package com.prabhupadaconnect.vedabase.ui.history

import com.prabhupadaconnect.vedabase.core.model.CorpusRecord
import com.prabhupadaconnect.vedabase.core.model.ReadingHistoryEntry
import com.prabhupadaconnect.vedabase.core.registry.BookRegistry
import java.time.Instant

/**
 * One row of the Recently Read screen, with enough corpus context to
 * display - ported from the desktop app's `RecentlyReadItem` (C#).
 */
data class RecentlyReadItem(
    val recordKey: String,
    val lastOpenedUtc: Instant,
    val openCount: Int,
    val isAvailable: Boolean,
    val bookKey: String = "",
    val bookTitle: String = "",
    val reference: String = "",
    val title: String? = null
)

/**
 * Pure mapping from raw reading-history rows to display items, extracted
 * from the ViewModel so it can be unit tested without Room or Hilt. Ported
 * 1:1 from the desktop app's `RecentlyReadViewModel.LoadAsync` (C#): a
 * history row whose RecordKey is missing from the corpus (never fabricated,
 * never dropped) renders as an unavailable placeholder instead.
 */
object RecentlyReadMapper {

    fun buildItems(
        history: List<ReadingHistoryEntry>,
        recordsByKey: Map<String, CorpusRecord>
    ): List<RecentlyReadItem> = history.map { h ->
        val record = recordsByKey[h.recordKey]
        if (record != null) {
            RecentlyReadItem(
                recordKey = h.recordKey,
                lastOpenedUtc = h.lastOpenedUtc,
                openCount = h.openCount,
                isAvailable = true,
                bookKey = record.bookKey,
                bookTitle = BookRegistry.getBookTitle(record.bookKey),
                reference = record.reference?.takeIf { it.isNotBlank() } ?: record.recordKey,
                title = record.title
            )
        } else {
            RecentlyReadItem(
                recordKey = h.recordKey,
                lastOpenedUtc = h.lastOpenedUtc,
                openCount = h.openCount,
                isAvailable = false,
                bookTitle = "Record unavailable",
                reference = h.recordKey
            )
        }
    }
}
