package com.prabhupadaconnect.vedabase.ui.highlights

import com.prabhupadaconnect.vedabase.core.model.CorpusRecord
import com.prabhupadaconnect.vedabase.core.model.Highlight
import com.prabhupadaconnect.vedabase.core.model.HighlightColor
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightsMapperTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun record(recordKey: String, bookKey: String, reference: String?) = CorpusRecord(
        recordKey = recordKey, bookKey = bookKey, sequence = 0, parentKey = null, recordType = "Verse",
        reference = reference, referenceStatus = "", title = null, rawDevanagari = "", transliteration = "",
        synonyms = "", translation = "", purports = ""
    )

    private fun highlight(
        id: String,
        recordKey: String,
        color: HighlightColor,
        selectedText: String? = "the soul is never born",
        startOffset: Int = 0,
        length: Int = 23
    ) = Highlight(
        id = id, recordKey = recordKey, field = "Translation", color = color,
        createdUtc = now, updatedUtc = now, startOffset = startOffset, length = length, selectedText = selectedText
    )

    @Test
    fun `a highlight with a matching record is enriched with book title, reference, and quoted snippet`() {
        val h = highlight("h1", "BG-2-20", HighlightColor.Yellow)
        val recordsByKey = mapOf("BG-2-20" to record("BG-2-20", "BG", "Bg 2.20"))

        val item = HighlightsMapper.buildItems(listOf(h), recordsByKey).single()

        assertEquals("BG", item.bookKey)
        assertEquals("Bhagavad-gītā As It Is", item.bookTitle)
        assertEquals("Bg 2.20", item.reference)
        assertEquals("“the soul is never born”", item.snippetDisplay)
    }

    @Test
    fun `a legacy whole-block highlight shows a block label instead of a fabricated snippet`() {
        val h = highlight("h1", "BG-2-20", HighlightColor.Green, startOffset = -1, length = -1)
        val recordsByKey = mapOf("BG-2-20" to record("BG-2-20", "BG", "Bg 2.20"))

        val item = HighlightsMapper.buildItems(listOf(h), recordsByKey).single()

        assertTrue(item.isLegacyBlockLevel)
        assertEquals("[Translation - whole section highlighted]", item.snippetDisplay)
    }

    @Test
    fun `a snippet longer than 80 characters is truncated with an ellipsis`() {
        val longText = "x".repeat(120)
        val h = highlight("h1", "BG-2-20", HighlightColor.Blue, selectedText = longText)
        val recordsByKey = mapOf("BG-2-20" to record("BG-2-20", "BG", "Bg 2.20"))

        val item = HighlightsMapper.buildItems(listOf(h), recordsByKey).single()

        assertEquals("“" + "x".repeat(80) + "…" + "”", item.snippetDisplay)
    }

    @Test
    fun `a highlight whose record is missing from the corpus renders as unavailable, never dropped`() {
        val h = highlight("h1", "STALE-KEY", HighlightColor.Yellow)

        val item = HighlightsMapper.buildItems(listOf(h), emptyMap()).single()

        assertEquals("STALE-KEY", item.reference)
        assertEquals("Record unavailable", item.bookTitle)
        assertEquals("", item.bookKey)
    }

    @Test
    fun `color filter of null returns every item`() {
        val items = HighlightsMapper.buildItems(
            listOf(
                highlight("h1", "BG-2-20", HighlightColor.Yellow),
                highlight("h2", "BG-2-20", HighlightColor.Green)
            ),
            mapOf("BG-2-20" to record("BG-2-20", "BG", "Bg 2.20"))
        )

        assertEquals(2, HighlightsMapper.filterByColor(items, null).size)
    }

    @Test
    fun `color filter narrows to only that color`() {
        val items = HighlightsMapper.buildItems(
            listOf(
                highlight("h1", "BG-2-20", HighlightColor.Yellow),
                highlight("h2", "BG-2-20", HighlightColor.Green),
                highlight("h3", "BG-2-20", HighlightColor.Green)
            ),
            mapOf("BG-2-20" to record("BG-2-20", "BG", "Bg 2.20"))
        )

        val filtered = HighlightsMapper.filterByColor(items, HighlightColor.Green)

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.color == HighlightColor.Green })
    }
}
