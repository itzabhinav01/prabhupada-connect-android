package com.prabhupadaconnect.vedabase.highlight

import com.prabhupadaconnect.vedabase.core.model.Highlight
import com.prabhupadaconnect.vedabase.core.model.HighlightColor
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightRendererTest {

    private fun highlight(
        id: String,
        start: Int,
        length: Int,
        selectedText: String? = null,
        color: HighlightColor = HighlightColor.Yellow,
        createdAt: Instant = Instant.EPOCH,
        deleted: Boolean = false
    ) = Highlight(
        id = id,
        recordKey = "BG-1-1",
        field = "Translation",
        color = color,
        createdUtc = createdAt,
        updatedUtc = createdAt,
        deletedUtc = if (deleted) createdAt.plusSeconds(1) else null,
        startOffset = start,
        length = length,
        selectedText = selectedText
    )

    // -------------------- Highlight.rangesOverlap --------------------

    @Test
    fun `identical ranges overlap`() {
        assertTrue(Highlight.rangesOverlap(0, 5, 0, 5))
    }

    @Test
    fun `partially overlapping ranges overlap`() {
        assertTrue(Highlight.rangesOverlap(0, 5, 3, 5))
        assertTrue(Highlight.rangesOverlap(3, 5, 0, 5))
    }

    @Test
    fun `adjacent ranges do not overlap`() {
        // [0,5) and [5,10) touch at the boundary but do not overlap.
        assertFalse(Highlight.rangesOverlap(0, 5, 5, 5))
        assertFalse(Highlight.rangesOverlap(5, 5, 0, 5))
    }

    @Test
    fun `disjoint ranges do not overlap`() {
        assertFalse(Highlight.rangesOverlap(0, 5, 10, 5))
    }

    @Test
    fun `a range fully containing another overlaps`() {
        assertTrue(Highlight.rangesOverlap(0, 10, 3, 2))
    }

    @Test
    fun `zero or negative length never overlaps`() {
        assertFalse(Highlight.rangesOverlap(0, 0, 0, 5))
        assertFalse(Highlight.rangesOverlap(0, 5, 0, 0))
        assertFalse(Highlight.rangesOverlap(0, -1, 0, 5))
    }

    // -------------------- Highlight.overlapsWith / isLegacyBlockLevel --------------------

    @Test
    fun `legacy block-level highlight never reports overlap`() {
        val legacy = highlight("h1", start = -1, length = -1)
        assertTrue(legacy.isLegacyBlockLevel)
        assertFalse(legacy.overlapsWith(0, 100))
    }

    @Test
    fun `precise highlight reports overlap against an intersecting range`() {
        val h = highlight("h1", start = 10, length = 5)
        assertFalse(h.isLegacyBlockLevel)
        assertTrue(h.overlapsWith(12, 5))
        assertFalse(h.overlapsWith(15, 5)) // adjacent, not overlapping
    }

    // -------------------- HighlightRenderer.findOverlap --------------------

    @Test
    fun `findOverlap returns the specific highlight that intersects`() {
        val existing = listOf(
            highlight("h1", start = 0, length = 5),
            highlight("h2", start = 20, length = 5)
        )
        val hit = HighlightRenderer.findOverlap(existing, start = 3, length = 4)
        assertEquals("h1", hit?.id)
    }

    @Test
    fun `findOverlap ignores deleted highlights`() {
        val existing = listOf(highlight("h1", start = 0, length = 5, deleted = true))
        assertNull(HighlightRenderer.findOverlap(existing, start = 0, length = 5))
    }

    @Test
    fun `findOverlap returns null when the proposed range is free`() {
        val existing = listOf(highlight("h1", start = 0, length = 5))
        assertNull(HighlightRenderer.findOverlap(existing, start = 5, length = 5))
    }

    // -------------------- HighlightRenderer.render --------------------

    @Test
    fun `render applies a background span at the selectedText occurrence`() {
        val text = "the quick brown fox jumps over the lazy dog"
        val h = highlight("h1", start = 4, length = 5, selectedText = "quick")
        val annotated = HighlightRenderer.render(text, listOf(h))

        assertEquals(text, annotated.text)
        val span = annotated.spanStyles.single()
        assertEquals(4, span.start)
        assertEquals(9, span.end)
        assertEquals(HighlightRenderer.colorFor(HighlightColor.Yellow), span.item.background)
    }

    @Test
    fun `render falls back to the stored offsets when selectedText is blank`() {
        val text = "the quick brown fox"
        val h = highlight("h1", start = 4, length = 5, selectedText = null)
        val annotated = HighlightRenderer.render(text, listOf(h))

        val span = annotated.spanStyles.single()
        assertEquals(4, span.start)
        assertEquals(9, span.end)
    }

    @Test
    fun `render falls back to stored offsets when selectedText no longer appears in the text`() {
        val text = "the quick brown fox"
        val h = highlight("h1", start = 4, length = 5, selectedText = "no-longer-there")
        val annotated = HighlightRenderer.render(text, listOf(h))

        val span = annotated.spanStyles.single()
        assertEquals(4, span.start)
        assertEquals(9, span.end)
    }

    @Test
    fun `render skips a legacy block-level highlight with no matching text`() {
        val text = "the quick brown fox"
        val h = highlight("h1", start = -1, length = -1, selectedText = "not present anywhere")
        val annotated = HighlightRenderer.render(text, listOf(h))

        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun `two highlights sharing identical selectedText claim separate occurrences in creation order`() {
        val text = "krishna is krishna is krishna"
        val first = highlight("h1", start = 0, length = 0, selectedText = "krishna", createdAt = Instant.EPOCH)
        val second = highlight("h2", start = 0, length = 0, selectedText = "krishna", createdAt = Instant.EPOCH.plusSeconds(1))

        val annotated = HighlightRenderer.render(text, listOf(first, second))
        val spans = annotated.spanStyles.sortedBy { it.start }

        assertEquals(2, spans.size)
        // First occurrence of "krishna" starts at 0, second at 11.
        assertEquals(0, spans[0].start)
        assertEquals(11, spans[1].start)
    }

    @Test
    fun `render is a no-op for empty text or no highlights`() {
        assertEquals("", HighlightRenderer.render("", emptyList()).text)
        assertTrue(HighlightRenderer.render("", emptyList()).spanStyles.isEmpty())
        assertTrue(HighlightRenderer.render("some text", emptyList()).spanStyles.isEmpty())
    }

    @Test
    fun `colorFor maps each HighlightColor to a distinct color`() {
        val colors = HighlightColor.entries.map { HighlightRenderer.colorFor(it) }
        assertEquals(colors.size, colors.toSet().size)
    }
}
