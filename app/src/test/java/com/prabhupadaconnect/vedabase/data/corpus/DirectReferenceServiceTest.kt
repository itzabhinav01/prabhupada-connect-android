package com.prabhupadaconnect.vedabase.data.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [DirectReferenceService]'s pure "@" grammar parsing - the part that
 * needs no corpus/database access. Index-building and suggestion resolution
 * (which do need the corpus hierarchy) are exercised on-device instead.
 */
class DirectReferenceServiceTest {

    @Test
    fun `isReferenceQuery recognizes a leading at-sign, with or without leading whitespace`() {
        assertTrue(DirectReferenceService.isReferenceQuery("@BG 1.1"))
        assertTrue(DirectReferenceService.isReferenceQuery("  @sb 1.1.1"))
        assertFalse(DirectReferenceService.isReferenceQuery("BG 1.1"))
        assertFalse(DirectReferenceService.isReferenceQuery(""))
    }

    @Test
    fun `parses a two-level BG reference`() {
        val result = DirectReferenceService.parse("@BG 1.1")
        assertEquals("BG", result.work?.bookKey)
        assertEquals(listOf(1, 1), result.parts)
    }

    @Test
    fun `parses a three-level SB reference`() {
        val result = DirectReferenceService.parse("@SB 1.1.1")
        assertEquals("SB", result.work?.bookKey)
        assertEquals(listOf(1, 1, 1), result.parts)
    }

    @Test
    fun `parses a CC Madhya reference into the MADHYA work`() {
        val result = DirectReferenceService.parse("@CC Madhya 9.29")
        assertEquals("MADHYA", result.work?.bookKey)
        assertEquals(listOf(9, 29), result.parts)
    }

    @Test
    fun `a bare lila alias resolves the same as the CC-prefixed form`() {
        val bare = DirectReferenceService.parse("@Adi 1.1")
        val prefixed = DirectReferenceService.parse("@CC Adi 1.1")
        assertEquals("DI", bare.work?.bookKey)
        assertEquals(bare.work?.bookKey, prefixed.work?.bookKey)
        assertEquals(bare.parts, prefixed.parts)
    }

    @Test
    fun `parses a single-level NOD chapter reference`() {
        val result = DirectReferenceService.parse("@NOD 2")
        assertEquals("NOD", result.work?.bookKey)
        assertEquals(listOf(2), result.parts)
    }

    @Test
    fun `colloquial aliases resolve to the same work as the canonical BookKey`() {
        assertEquals("BG", DirectReferenceService.parse("@bg 2.13").work?.bookKey)
        assertEquals("BG", DirectReferenceService.parse("@gita 2.13").work?.bookKey)
        assertEquals("SB", DirectReferenceService.parse("@bhagavatam 1.1.1").work?.bookKey)
        assertEquals("SB", DirectReferenceService.parse("@srimadbhagavatam 1.1.1").work?.bookKey)
    }

    @Test
    fun `parsing is tolerant of case and missing separators`() {
        val spaced = DirectReferenceService.parse("@Bg 1.1")
        val noSpace = DirectReferenceService.parse("@BG1.1")
        val lower = DirectReferenceService.parse("@bg 1.1")

        assertEquals("BG", spaced.work?.bookKey)
        assertEquals("BG", noSpace.work?.bookKey)
        assertEquals("BG", lower.work?.bookKey)
        assertEquals(listOf(1, 1), spaced.parts)
        assertEquals(listOf(1, 1), noSpace.parts)
    }

    @Test
    fun `partial numeric prefix is parsed as-typed for progressive suggestions`() {
        val result = DirectReferenceService.parse("@SB 10")
        assertEquals("SB", result.work?.bookKey)
        assertEquals(listOf(10), result.parts)
    }

    @Test
    fun `a bare at-sign with no work text yet parses to a null work`() {
        val result = DirectReferenceService.parse("@")
        assertNull(result.work)
        assertEquals("", result.workTextTyped)
        assertEquals(emptyList<Int>(), result.parts)
    }

    @Test
    fun `an unrecognized work name parses to a null work but preserves the typed text`() {
        val result = DirectReferenceService.parse("@nonexistentbook 1.1")
        assertNull(result.work)
        assertEquals("nonexistentbook", result.workTextTyped)
    }

    @Test
    fun `the purged books JAPA and BROKENNAMES are not recognized`() {
        assertNull(DirectReferenceService.parse("@BrokenNames 3").work)
        assertNull(DirectReferenceService.parse("@Japa 5").work)
    }
}
