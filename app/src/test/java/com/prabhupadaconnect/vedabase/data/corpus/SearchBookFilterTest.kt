package com.prabhupadaconnect.vedabase.data.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchBookFilterTest {

    @Test
    fun `null selection means no filter at all`() {
        assertNull(SearchBookFilter.expand(null))
    }

    @Test
    fun `an empty selection expands to an empty list, never to no filter`() {
        // Distinguishing "no chips selected" (null, search everything) from
        // "every chip somehow deselected to an empty set" (empty list, match
        // nothing) is exactly the SQL `AND 1=0` branch this guards.
        assertEquals(emptyList<String>(), SearchBookFilter.expand(emptyList()))
    }

    @Test
    fun `a single plain book key passes through unchanged`() {
        assertEquals(listOf("BG"), SearchBookFilter.expand(listOf("BG")))
    }

    @Test
    fun `multiple plain book keys all pass through`() {
        assertEquals(listOf("BG", "SB"), SearchBookFilter.expand(listOf("BG", "SB")))
    }

    @Test
    fun `CC expands to its three underlying corpus BookKeys`() {
        assertEquals(listOf("DI", "MADHYA", "ANTYA"), SearchBookFilter.expand(listOf("CC")))
    }

    @Test
    fun `CC expands alongside other plain keys in the same selection`() {
        assertEquals(listOf("BG", "DI", "MADHYA", "ANTYA"), SearchBookFilter.expand(listOf("BG", "CC")))
    }

    @Test
    fun `blank entries are dropped and duplicates are collapsed`() {
        assertEquals(listOf("BG"), SearchBookFilter.expand(listOf("BG", "", "BG", "  ")))
    }
}
