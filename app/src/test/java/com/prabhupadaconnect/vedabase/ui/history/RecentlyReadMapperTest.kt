package com.prabhupadaconnect.vedabase.ui.history

import com.prabhupadaconnect.vedabase.core.model.CorpusRecord
import com.prabhupadaconnect.vedabase.core.model.ReadingHistoryEntry
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentlyReadMapperTest {

    private fun record(recordKey: String, bookKey: String, reference: String?, title: String? = null) = CorpusRecord(
        recordKey = recordKey, bookKey = bookKey, sequence = 0, parentKey = null, recordType = "Verse",
        reference = reference, referenceStatus = "", title = title, rawDevanagari = "", transliteration = "",
        synonyms = "", translation = "", purports = ""
    )

    @Test
    fun `a history row with a matching corpus record is enriched with book title and reference`() {
        val history = listOf(ReadingHistoryEntry("BG-2-13", Instant.parse("2026-01-01T00:00:00Z"), openCount = 3))
        val recordsByKey = mapOf("BG-2-13" to record("BG-2-13", "BG", "Bg 2.13"))

        val items = RecentlyReadMapper.buildItems(history, recordsByKey)

        assertEquals(1, items.size)
        val item = items[0]
        assertTrue(item.isAvailable)
        assertEquals("BG-2-13", item.recordKey)
        assertEquals("Bg 2.13", item.reference)
        assertEquals("Bhagavad-gītā As It Is", item.bookTitle)
        assertEquals(3, item.openCount)
    }

    @Test
    fun `a history row with a blank reference falls back to the record key`() {
        val history = listOf(ReadingHistoryEntry("BG-2-13", Instant.parse("2026-01-01T00:00:00Z"), openCount = 1))
        val recordsByKey = mapOf("BG-2-13" to record("BG-2-13", "BG", reference = "  "))

        val item = RecentlyReadMapper.buildItems(history, recordsByKey).single()

        assertEquals("BG-2-13", item.reference)
    }

    @Test
    fun `a history row whose record is missing from the corpus renders as unavailable, never dropped`() {
        val history = listOf(ReadingHistoryEntry("STALE-KEY", Instant.parse("2026-01-01T00:00:00Z"), openCount = 5))

        val items = RecentlyReadMapper.buildItems(history, emptyMap())

        assertEquals(1, items.size)
        val item = items[0]
        assertFalse(item.isAvailable)
        assertEquals("STALE-KEY", item.recordKey)
        assertEquals("STALE-KEY", item.reference)
        assertEquals("Record unavailable", item.bookTitle)
        // The open count from the underlying history row is preserved even though the record can't be shown.
        assertEquals(5, item.openCount)
    }

    @Test
    fun `history rows are mapped in the given order`() {
        val history = listOf(
            ReadingHistoryEntry("BG-2-13", Instant.parse("2026-01-02T00:00:00Z"), openCount = 1),
            ReadingHistoryEntry("BG-1-1", Instant.parse("2026-01-01T00:00:00Z"), openCount = 2)
        )
        val recordsByKey = mapOf(
            "BG-2-13" to record("BG-2-13", "BG", "Bg 2.13"),
            "BG-1-1" to record("BG-1-1", "BG", "Bg 1.1")
        )

        val items = RecentlyReadMapper.buildItems(history, recordsByKey)

        assertEquals(listOf("BG-2-13", "BG-1-1"), items.map { it.recordKey })
    }
}
