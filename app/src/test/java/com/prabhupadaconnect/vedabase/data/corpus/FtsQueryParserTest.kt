package com.prabhupadaconnect.vedabase.data.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FtsQueryParserTest {

    @Test
    fun `blank query returns empty string`() {
        assertEquals("", FtsQueryParser.parse(""))
        assertEquals("", FtsQueryParser.parse("   "))
        assertEquals("", FtsQueryParser.parse(null))
    }

    @Test
    fun `single plain word passes through unquoted`() {
        assertEquals("bhakti", FtsQueryParser.parse("bhakti"))
    }

    @Test
    fun `phonetic expansion for krishna includes iast and anglicized spellings`() {
        val result = FtsQueryParser.parse("krishna")
        assertTrue("expected krishna in $result", result.contains("\"krishna\""))
        assertTrue("expected krsna in $result", result.contains("\"krsna\""))
        assertTrue("expected kṛṣṇa in $result", result.contains("\"kṛṣṇa\""))
        assertTrue("expected OR-joined group", result.startsWith("(") && result.contains(" OR "))
    }

    @Test
    fun `phonetic expansion is case sensitive to the lookup key only, not a partial match`() {
        // "krishnas" is not a known expansion key, so it must pass through untouched.
        assertEquals("krishnas", FtsQueryParser.parse("krishnas"))
    }

    @Test
    fun `explicit uppercase boolean operators are preserved between terms`() {
        assertEquals("bhakti AND yoga", FtsQueryParser.parse("bhakti AND yoga"))
        assertEquals("bhakti OR yoga", FtsQueryParser.parse("bhakti OR yoga"))
    }

    @Test
    fun `lowercase boolean keywords standing alone are disarmed`() {
        // "and"/"or"/"not" as bare lowercase words are stripped, not treated as operators.
        val result = FtsQueryParser.parse("duty and devotion")
        assertEquals("duty devotion", result)
    }

    @Test
    fun `trailing boolean operator is dropped`() {
        assertEquals("bhakti", FtsQueryParser.parse("bhakti AND"))
    }

    @Test
    fun `whole query wrapped in quotes becomes a single exact phrase`() {
        assertEquals("\"devotional service\"", FtsQueryParser.parse("\"devotional service\""))
    }

    @Test
    fun `unbalanced trailing quote is auto-closed`() {
        val result = FtsQueryParser.parse("\"devotional service")
        assertEquals("\"devotional service\"", result)
    }

    @Test
    fun `citation pattern for bhagavad gita chapter and verse is quoted verbatim`() {
        assertEquals("\"Bg 2.13\"", FtsQueryParser.parse("Bg 2.13"))
        assertEquals("\"SB 1.1.1\"", FtsQueryParser.parse("SB 1.1.1"))
    }

    @Test
    fun `noi citation shorthand expands to noi AND the verse number`() {
        assertEquals("(noi AND \"4\")", FtsQueryParser.parse("Noi 4"))
    }

    @Test
    fun `iso citation shorthand expands to iso AND the mantra number`() {
        assertEquals("(iso AND \"1\")", FtsQueryParser.parse("Iso 1"))
    }

    @Test
    fun `cc lila citation is quoted as a phrase`() {
        assertEquals("\"Adi 1.1\"", FtsQueryParser.parse("Cc Adi 1.1"))
    }

    @Test
    fun `proximity syntax converts to NEAR expression`() {
        val result = FtsQueryParser.parse("bhakti w/5 yoga")
        assertEquals("NEAR(\"bhakti\" \"yoga\", 5)", result)
    }

    @Test
    fun `exact word mode quotes every plain token`() {
        val result = FtsQueryParser.parse("duty", isExactWord = true)
        assertEquals("\"duty\"", result)
    }

    @Test
    fun `tokens containing punctuation are quoted even without exact word mode`() {
        val result = FtsQueryParser.parse("kurukshetra-field")
        assertEquals("\"kurukshetra-field\"", result)
    }

    @Test
    fun `devanagari input is normalized and returned without further tokenization`() {
        val result = FtsQueryParser.parse("अर्जुन")
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `multi word query without operators is space joined`() {
        assertEquals("duty devotion", FtsQueryParser.parse("duty devotion"))
    }
}
