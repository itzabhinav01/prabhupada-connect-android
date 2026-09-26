package com.prabhupadaconnect.vedabase.data.corpus

/**
 * Pure-Kotlin search-result snippet generator - replaces FTS5's native
 * `snippet()`, which reproducibly trips `SQLITE_CORRUPT_VTAB` on this
 * corpus's external-content table via requery's bundled SQLite (see
 * [CorpusRepository]'s doc comment on `search`). Runs entirely in Kotlin
 * against the small, already-fetched set of result rows (bounded by the
 * search `limit`, never the full 50k+-record corpus), so it carries none of
 * that risk.
 *
 * Finds the first IAST-diacritic-tolerant match of any query token in a
 * prioritized list of fields (Translation before Purport, matching the
 * order callers pass them in), and returns a window of surrounding context
 * with the match itself wrapped in the same `«...»` sentinel the desktop
 * app's FTS5 snippets use - [toAnnotatedSnippet] turns that into a bolded
 * span for display.
 */
object SnippetGenerator {

    const val MARK_START = "«"
    const val MARK_END = "»"
    private const val ELLIPSIS = "..."
    private const val CONTEXT_BEFORE = 50
    private const val CONTEXT_AFTER = 70
    private const val MIN_TOKEN_LENGTH = 2

    private val TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+")
    private val BOOLEAN_OPERATORS = setOf("AND", "OR", "NOT", "NEAR")

    // Every source character maps to exactly one target character (never
    // removed or expanded), so normalizing never shifts index positions -
    // an offset found in the normalized string is the same offset in the
    // original, diacritics and all.
    private val DIACRITIC_FOLD: Map<Char, Char> = buildMap {
        put('ā', 'a'); put('Ā', 'A') // ā Ā
        put('ī', 'i'); put('Ī', 'I') // ī Ī
        put('ū', 'u'); put('Ū', 'U') // ū Ū
        put('ṛ', 'r'); put('Ṛ', 'R') // ṛ Ṛ
        put('ṝ', 'r'); put('Ṝ', 'R') // ṝ Ṝ
        put('ḷ', 'l'); put('Ḷ', 'L') // ḷ Ḷ
        put('ḹ', 'l'); put('Ḹ', 'L') // ḹ Ḹ
        put('ṃ', 'm'); put('Ṃ', 'M') // ṃ Ṃ
        put('ṁ', 'm'); put('Ṁ', 'M') // ṁ Ṁ
        put('ḥ', 'h'); put('Ḥ', 'H') // ḥ Ḥ
        put('ś', 's'); put('Ś', 'S') // ś Ś
        put('ṣ', 's'); put('Ṣ', 'S') // ṣ Ṣ
        put('ṅ', 'n'); put('Ṅ', 'N') // ṅ Ṅ
        put('ñ', 'n'); put('Ñ', 'N') // ñ Ñ
        put('ṇ', 'n'); put('Ṇ', 'N') // ṇ Ṇ
        put('ṭ', 't'); put('Ṭ', 'T') // ṭ Ṭ
        put('ḍ', 'd'); put('Ḍ', 'D') // ḍ Ḍ
        put('ē', 'e'); put('Ē', 'E') // ē Ē
        put('ō', 'o'); put('Ō', 'O') // ō Ō
    }

    private fun foldDiacritics(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(DIACRITIC_FOLD[c] ?: c)
        return sb.toString()
    }

    /** Extracts plain search tokens from a raw (or already FTS5-escaped) query string. */
    fun extractTokens(query: String): List<String> =
        TOKEN_REGEX.findAll(query)
            .map { it.value }
            .filter { it.length >= MIN_TOKEN_LENGTH && it.uppercase() !in BOOLEAN_OPERATORS }
            .distinct()
            .toList()

    private data class MatchRange(val start: Int, val end: Int)

    private fun findFirstMatch(field: String, tokens: List<String>): MatchRange? {
        if (tokens.isEmpty()) return null
        val foldedField = foldDiacritics(field).lowercase()

        var bestStart = -1
        var bestLength = 0
        for (token in tokens) {
            val foldedToken = foldDiacritics(token).lowercase()
            if (foldedToken.isEmpty()) continue
            val idx = foldedField.indexOf(foldedToken)
            if (idx >= 0 && (bestStart == -1 || idx < bestStart)) {
                bestStart = idx
                bestLength = token.length
            }
        }
        return if (bestStart >= 0) MatchRange(bestStart, bestStart + bestLength) else null
    }

    private fun windowAround(field: String, match: MatchRange): String {
        val start = (match.start - CONTEXT_BEFORE).coerceAtLeast(0)
        val end = (match.end + CONTEXT_AFTER).coerceAtMost(field.length)

        val before = field.substring(start, match.start)
        val matched = field.substring(match.start, match.end)
        val after = field.substring(match.end, end)

        val prefix = if (start > 0) ELLIPSIS else ""
        val suffix = if (end < field.length) ELLIPSIS else ""

        return "$prefix$before$MARK_START$matched$MARK_END$after$suffix"
    }

    /**
     * Returns a marked-up snippet (matched term wrapped in `«...»`) from the
     * first of [fields] (checked in the given order - callers should pass
     * Translation before Purport, matching the desktop app's field
     * priority) that contains any token of [query]. Returns an empty string
     * if [query] has no usable tokens or none of [fields] contain a match
     * (the caller falls back to something else, e.g. the book title).
     */
    fun generate(fields: List<String?>, query: String): String {
        val tokens = extractTokens(query)
        if (tokens.isEmpty()) return ""

        for (field in fields) {
            if (field.isNullOrBlank()) continue
            val match = findFirstMatch(field, tokens) ?: continue
            return windowAround(field, match)
        }
        return ""
    }
}
