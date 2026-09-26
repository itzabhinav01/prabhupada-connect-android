package com.prabhupadaconnect.vedabase.core.util

/**
 * Formats raw corpus prose (Translation and Purports) by collapsing Folio's
 * fixed-width 70-column line-wrap artifacts into continuous flowing
 * paragraphs. Ported 1:1 from the desktop app's `ProseFormatter` (C#).
 */
object ProseFormatter {

    private fun isWrapBoundaryChar(c: Char): Boolean =
        c.isWhitespace() || c == '-' || c == '‐' || c == '‑' || c == '–' || c == '—'

    private fun isClauseEndingPunctuation(c: Char): Boolean =
        c == ';' || c == ',' || c == ':' || c == ')' || c == ']' || c == '!' || c == '?'

    private fun isLineBreakAt(s: String, i: Int): Int {
        if (i < s.length && s[i] == '\r' && i + 1 < s.length && s[i + 1] == '\n') return 2
        if (i < s.length && (s[i] == '\n' || s[i] == '\r')) return 1
        return 0
    }

    private fun isSanskritDiacriticLetter(c: Char): Boolean = c in SANSKRIT_DIACRITICS

    private val SANSKRIT_DIACRITICS = setOf(
        'ā', 'Ā', 'ī', 'Ī', 'ū', 'Ū',
        'ṛ', 'Ṛ', 'ṝ', 'Ṝ',
        'ḷ', 'Ḷ', 'ḹ', 'Ḹ',
        'ñ', 'Ñ', 'ṅ', 'Ṅ',
        'ṇ', 'Ṇ', 'ṭ', 'Ṭ', 'ḍ', 'Ḍ',
        'ṣ', 'Ṣ', 'ś', 'Ś',
        'ḥ', 'Ḥ', 'ṁ', 'Ṁ'
    )

    private fun extractTrailingFragment(sb: StringBuilder): String {
        var start = sb.length
        while (start > 0 && !isWrapBoundaryChar(sb[start - 1])) start--
        return sb.substring(start, sb.length)
    }

    private val COMMON_ENGLISH_WORDS: Set<String> = setOf(
        "a", "an", "the", "this", "that", "these", "those", "some", "any", "all", "both", "each", "every", "either", "neither", "no", "none",
        "i", "you", "he", "she", "it", "we", "they", "him", "her", "them", "his", "its", "their", "my", "your", "our", "who", "whom", "whose", "which", "what",
        "am", "is", "are", "was", "were", "be", "been", "being", "has", "have", "had", "do", "does", "did", "done",
        "can", "could", "will", "would", "shall", "should", "may", "might", "must",
        "and", "or", "but", "nor", "so", "yet", "if", "unless", "because", "although", "though", "while", "when", "where", "as", "than",
        "in", "on", "at", "by", "to", "of", "for", "from", "with", "without", "into", "onto", "upon", "over", "under", "between", "among", "through",
        "during", "before", "after", "above", "below", "near", "about", "against", "across", "along", "around", "behind", "beside", "beyond",
        "not", "also", "only", "even", "still", "again", "then", "therefore", "however", "thus", "hence", "naturally", "actually", "certainly",
        "indeed", "simply", "clearly", "especially", "particularly", "generally", "usually", "always", "never", "sometimes", "often", "already",
        "just", "very", "quite", "rather", "more", "most", "less", "least", "much", "many", "few", "little", "such", "other", "another", "same",
        "one", "two", "three", "first", "second", "third", "last", "next", "own", "new", "old", "great", "high", "low", "good", "bad", "true", "false",
        "real", "pure", "whole", "entire", "complete", "perfect", "supreme", "different", "various", "several", "certain",
        "person", "persons", "people", "man", "men", "woman", "king", "kings", "god", "godhead", "lord", "personality",
        "soul", "souls", "body", "bodies", "mind", "minds", "life", "lives", "world", "material", "spiritual", "devotee", "devotees",
        "service", "knowledge", "activity", "activities", "consciousness", "nature", "natures", "position", "positions", "class", "classes",
        "society", "circumstance", "circumstances", "theory", "theories", "thread", "threads", "family", "families", "sacrifice", "sacrifices",
        "scripture", "scriptures", "evidence", "example", "examples", "birthright", "caste", "castes", "statement", "statements", "stated",
        "states", "state", "book", "books", "guide", "guides", "word", "words", "time", "times", "place", "places", "thing", "things", "way", "ways",
        "part", "parts", "point", "points", "process", "processes", "principle", "principles", "practice", "practices", "duty", "duties",
        "member", "members", "fact", "facts", "idea", "ideas", "proof", "proofs", "reason", "reasons", "result", "results", "cause", "causes",
        "effect", "effects", "form", "forms", "force", "forces", "power", "powers", "truth", "truths", "quality", "qualities",
        "called", "named", "known", "said", "says", "say", "claim", "claims", "claimed", "accept", "accepts", "accepted", "think", "thinks",
        "thought", "know", "knows", "see", "sees", "seen", "come", "comes", "came", "go", "goes", "went", "gone", "give", "gives", "given",
        "take", "takes", "taken", "make", "makes", "made", "find", "finds", "found", "become", "becomes", "became", "receive", "receives"
    )

    /**
     * Cleans a prose block by removing fixed-width hard-wrap artifacts while
     * preserving true paragraph breaks (double newlines).
     */
    fun cleanProse(original: String?): String {
        if (original.isNullOrBlank()) return ""

        val sb = StringBuilder(original.length)
        var i = 0

        while (i < original.length) {
            val len = isLineBreakAt(original, i)
            if (len > 0) {
                val isParagraphBreak = isLineBreakAt(original, i + len) > 0
                if (isParagraphBreak) {
                    sb.append("\n\n")
                    while (i < original.length && (original[i] == '\r' || original[i] == '\n' || original[i] == ' ' || original[i] == '\t')) {
                        i++
                    }
                    continue
                }

                val spaceAlreadyBefore = sb.isNotEmpty() && sb[sb.length - 1].isWhitespace()
                val afterIdx = i + len
                val spaceAlreadyAfter = afterIdx < original.length && original[afterIdx].isWhitespace()

                if (spaceAlreadyBefore || spaceAlreadyAfter) {
                    i += len
                    continue
                }

                val prevChar = if (sb.isNotEmpty()) sb[sb.length - 1] else '\u0000'
                val hasClausePunctuation = isClauseEndingPunctuation(prevChar)
                val hasPeriodWordEnd = prevChar == '.' && (afterIdx >= original.length || !original[afterIdx].isDigit())

                val trailingFragment = extractTrailingFragment(sb)
                val trailingHasDiacritic = trailingFragment.any { isSanskritDiacriticLetter(it) }
                val trailingIsRecognizedWord = !trailingHasDiacritic && COMMON_ENGLISH_WORDS.contains(trailingFragment.lowercase())

                if (hasClausePunctuation || hasPeriodWordEnd || trailingIsRecognizedWord) {
                    sb.append(' ')
                }

                i += len
            } else {
                sb.append(original[i])
                i++
            }
        }

        return sb.toString().trim()
    }

    /** Splits purports into individual clean paragraphs for UI display. */
    fun getCleanParagraphs(original: String?): List<String> {
        if (original.isNullOrBlank()) return emptyList()

        val rawParagraphs = original.split("\r\n\r\n", "\n\n", "\r\r").filter { it.isNotEmpty() }
        return rawParagraphs.mapNotNull { raw ->
            val cleaned = cleanProse(raw)
            cleaned.ifBlank { null }
        }
    }
}
