package com.prabhupadaconnect.vedabase.data.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetGeneratorTest {

    // -------------------- extractTokens --------------------

    @Test
    fun `extractTokens drops boolean operators and sub-minimum-length tokens`() {
        val tokens = SnippetGenerator.extractTokens("bhakti AND yoga a to OR")
        assertEquals(listOf("bhakti", "yoga", "to"), tokens)
    }

    @Test
    fun `extractTokens ignores boolean operators regardless of case`() {
        val tokens = SnippetGenerator.extractTokens("near yoga NOT bhakti")
        assertEquals(listOf("yoga", "bhakti"), tokens)
    }

    @Test
    fun `extractTokens deduplicates identical tokens but is case sensitive`() {
        val tokens = SnippetGenerator.extractTokens("krishna krishna Krishna")
        assertEquals(listOf("krishna", "Krishna"), tokens)
    }

    @Test
    fun `extractTokens on a blank or operator-only query yields nothing`() {
        assertTrue(SnippetGenerator.extractTokens("").isEmpty())
        assertTrue(SnippetGenerator.extractTokens("AND OR NOT").isEmpty())
    }

    // -------------------- generate: windowing --------------------

    @Test
    fun `generate clips long context with leading and trailing ellipses`() {
        val field = "A".repeat(60) + "krishna" + "B".repeat(80)
        val result = SnippetGenerator.generate(listOf(field), "krishna")

        val expected = "..." + "A".repeat(50) + "«krishna»" + "B".repeat(70) + "..."
        assertEquals(expected, result)
    }

    @Test
    fun `generate omits ellipses when the match is near both field boundaries`() {
        val field = "krishna is the supreme lord"
        val result = SnippetGenerator.generate(listOf(field), "krishna")
        assertEquals("«krishna» is the supreme lord", result)
    }

    // -------------------- generate: diacritic tolerance --------------------

    @Test
    fun `generate matches a diacritic-bearing field against a plain query token`() {
        val field = "offer prayers unto kṛṣṇa the lord"
        val result = SnippetGenerator.generate(listOf(field), "krsna")
        assertEquals("offer prayers unto «kṛṣṇa» the lord", result)
    }

    @Test
    fun `generate match is case-insensitive`() {
        val field = "Bhakti is the path"
        val result = SnippetGenerator.generate(listOf(field), "bhakti")
        assertEquals("«Bhakti» is the path", result)
    }

    // -------------------- generate: field priority and fallbacks --------------------

    @Test
    fun `generate prefers the first field in priority order that contains a match`() {
        val translation = "no match here"
        val purport = "purport text with duty inside"
        val result = SnippetGenerator.generate(listOf(translation, purport), "duty")
        assertEquals("purport text with «duty» inside", result)
    }

    @Test
    fun `generate skips null and blank fields before finding a match`() {
        val result = SnippetGenerator.generate(listOf(null, "", "valid duty text"), "duty")
        assertEquals("valid «duty» text", result)
    }

    @Test
    fun `generate returns empty string when the query has no usable tokens`() {
        assertEquals("", SnippetGenerator.generate(listOf("any field text"), ""))
        assertEquals("", SnippetGenerator.generate(listOf("any field text"), "AND OR"))
    }

    @Test
    fun `generate returns empty string when no field contains any query token`() {
        val result = SnippetGenerator.generate(listOf("nothing relevant", "still nothing"), "krishna")
        assertEquals("", result)
    }

    @Test
    fun `generate picks the earliest-occurring token when multiple tokens match`() {
        val field = "duty comes before bhakti in this sentence"
        val result = SnippetGenerator.generate(listOf(field), "bhakti duty")
        assertTrue(result.startsWith("«duty»"))
    }
}
