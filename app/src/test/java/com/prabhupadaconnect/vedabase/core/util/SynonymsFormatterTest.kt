package com.prabhupadaconnect.vedabase.core.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynonymsFormatterTest {

    private val lemmaColor = Color(0xFFE5A93C)
    private val delimiterColor = Color.Gray

    @Test
    fun `colors the lemma and leaves the gloss in the default run`() {
        val result = SynonymsFormatter.format("paśya—behold", lemmaColor, delimiterColor)

        assertEquals("paśya—behold", result.text)
        val lemmaSpan = result.spanStyles.first { it.start == 0 && it.end == "paśya".length }
        assertEquals(lemmaColor, lemmaSpan.item.color)
        // The gloss itself ("behold") carries no color override.
        assertTrue(result.spanStyles.none { it.start == result.text.indexOf("behold") && it.item.color == lemmaColor })
    }

    @Test
    fun `hyphenated compound lemmas are not split on their internal hyphen`() {
        val result = SynonymsFormatter.format("pāṇḍu-putrāṇām—of the sons of Pāṇḍu", lemmaColor, delimiterColor)

        val lemmaSpan = result.spanStyles.first { it.item.color == lemmaColor }
        assertEquals("pāṇḍu-putrāṇām", result.text.substring(lemmaSpan.start, lemmaSpan.end))
    }

    @Test
    fun `multiple entries are separated and each lemma is colored independently`() {
        val result = SynonymsFormatter.format(
            "paśya—behold; etām—this; ācārya—O teacher",
            lemmaColor, delimiterColor
        )

        assertEquals("paśya—behold; etām—this; ācārya—O teacher", result.text)
        val lemmaSpans = result.spanStyles.filter { it.item.color == lemmaColor }
        assertEquals(3, lemmaSpans.size)
    }

    @Test
    fun `an entry with no delimiter is rendered plain, never crashes`() {
        val result = SynonymsFormatter.format("some malformed entry", lemmaColor, delimiterColor)
        assertEquals("some malformed entry", result.text)
        assertTrue(result.spanStyles.none { it.item.color == lemmaColor })
    }

    @Test
    fun `blank input returns an empty annotated string`() {
        assertEquals("", SynonymsFormatter.format("", lemmaColor, delimiterColor).text)
        assertEquals("   ", SynonymsFormatter.format("   ", lemmaColor, delimiterColor).text)
    }
}
