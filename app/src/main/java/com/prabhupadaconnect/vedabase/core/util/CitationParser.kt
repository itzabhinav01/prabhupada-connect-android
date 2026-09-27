package com.prabhupadaconnect.vedabase.core.util

/** One detected in-text scripture citation, resolvable via [com.prabhupadaconnect.vedabase.data.corpus.DirectReferenceService]. */
data class CitationMatch(val range: IntRange, val bookKey: String, val numbers: String)

/**
 * Detects references to OTHER works quoted/cited inline in a purport -
 * "Brahma-saṁhitā (5.52)", "Bg. 4.1", "SB 1.2.3", "Cc. Madhya 20.108", - and
 * resolves the book token to a [com.prabhupadaconnect.vedabase.data.corpus.DirectReferenceService]
 * BookKey, ready for exact-reference resolution to a RecordKey. Ported 1:1
 * from the desktop app's `DetectCitations`/`CitationLilaPattern`/
 * `CitationBookPattern` (C#).
 *
 * Deliberately covers only the handful of frequently-cross-cited works (BG,
 * SB, the three Cc līlās, BS, ISO, NOD, NOI) rather than every book's
 * colloquial name - the rest are overwhelmingly prose works without a
 * "chapter.verse" citation convention to begin with.
 *
 * Must run on the paragraph's cleaned/flowing display text, matching what
 * the reader actually sees on screen - never the raw corpus field - so
 * there is no separate offset-mapping step to drift out of sync.
 */
object CitationParser {

    private val CITATION_LILA_PATTERN = Regex(
        "\\b(?:Śrī\\s+)?(?:Caitanya-carit[āa]m[ṛr]ta|Cc\\.?|CC)\\s+(?<lila>[ĀA]di|Madhya|Antya)(?:-l[īi]l[āa])?\\s*\\(?\\s*(?<nums>\\d+(?:\\.\\d+){1,2})\\s*\\)?",
        RegexOption.IGNORE_CASE
    )

    // No trailing \b after the book group - see the desktop port's own note:
    // "Bg." ends in a period, and \b only fires at a word-char/non-word-char
    // transition, so a trailing \b after a period-ending alternative would
    // silently never match. The mandatory \s*\(?\s*\d+ that follows already
    // disambiguates a real citation from a book-name fragment mid-word.
    private val CITATION_BOOK_PATTERN = Regex(
        "\\b(?<book>Bhagavad-g[īi]t[āa]|Bg\\.|Śr[īi]mad-Bh[āa]gavatam|SB|Brahma-sa[ṁm]hit[āa]|(?:Śrī\\s+)?[ĪI]śopani[sṣ]ad|Nectar of Devotion|Nectar of Instruction)\\s*\\(?\\s*(?<nums>\\d+(?:\\.\\d+){0,2})\\s*\\)?",
        RegexOption.IGNORE_CASE
    )

    private fun mapCitationWordToBookKey(bookText: String): String? {
        val lower = bookText.trim().lowercase()
        return when {
            lower.startsWith("bhagavad") || lower == "bg." -> "BG"
            lower.startsWith("śrīmad") || lower.startsWith("srimad") || lower == "sb" -> "SB"
            lower.startsWith("brahma-sa") -> "BS"
            lower.contains("śopaniṣad") || lower.contains("sopanisad") || lower.contains("śopanisad") -> "ISO"
            lower.startsWith("nectar of devotion") -> "NOD"
            lower.startsWith("nectar of instruction") -> "NOI"
            else -> null
        }
    }

    fun findCitations(displayText: String): List<CitationMatch> {
        if (displayText.isEmpty()) return emptyList()

        val matches = mutableListOf<CitationMatch>()

        for (m in CITATION_LILA_PATTERN.findAll(displayText)) {
            val lila = m.groups["lila"]!!.value
            val bookKey = when {
                lila.startsWith("a", ignoreCase = true) -> "DI"
                lila.startsWith("m", ignoreCase = true) -> "MADHYA"
                else -> "ANTYA"
            }
            matches.add(CitationMatch(m.range, bookKey, m.groups["nums"]!!.value))
        }

        for (m in CITATION_BOOK_PATTERN.findAll(displayText)) {
            val numsGroup = m.groups["nums"]
            if (numsGroup == null || numsGroup.value.isEmpty()) continue
            val bookKey = mapCitationWordToBookKey(m.groups["book"]!!.value) ?: continue
            val range = m.range
            // Skip if this overlaps a Cc/līlā match already found above.
            if (matches.any { existing -> range.first <= existing.range.last && range.last >= existing.range.first }) continue
            matches.add(CitationMatch(range, bookKey, numsGroup.value))
        }

        return matches.sortedBy { it.range.first }
    }
}
