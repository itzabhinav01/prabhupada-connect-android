package com.prabhupadaconnect.vedabase.core.util

/**
 * Detects a stanza quoted inline in a purport (e.g. BG 9.6 citing
 * Brahma-saṁhitā 5.52) - ported 1:1 from the desktop app's
 * `DetectVerseBlockRanges` (C#). A quoted stanza reads, in the raw corpus
 * field, as a short paragraph (2-6 physical lines) whose text is
 * overwhelmingly IAST/Bengali transliteration rather than English prose.
 * Line count alone false-positives on short English paragraphs; diacritic
 * density alone false-positives on English paragraphs that merely mention a
 * Sanskrit term or two - requiring both, calibrated directly against BG
 * 9.6's actual four paragraphs (0%/5 lines, 7.6%/7 lines, 17.4%/5 lines -
 * the real verse, correctly the only one over the threshold, 0%/3 lines),
 * reliably isolates just the stanza.
 *
 * Must run on the paragraph's RAW line breaks ([ProseFormatter.Paragraph.raw]),
 * never the cleaned/flowed text - [ProseFormatter.cleanProse] deliberately
 * erases single line breaks, which would erase this signal entirely.
 */
object PurportBlockDetector {

    private const val VERSE_DIACRITIC_DENSITY_THRESHOLD = 0.08
    private val LINE_BREAK = Regex("\r\n|\r|\n")

    fun isQuotedVerseParagraph(rawParagraph: String): Boolean {
        val lineCount = rawParagraph.split(LINE_BREAK).size
        if (lineCount < 2 || lineCount > 6) return false

        var diacriticCount = 0
        var nonWhitespaceCount = 0
        for (c in rawParagraph) {
            if (c.isWhitespace()) continue
            nonWhitespaceCount++
            if (c in ProseFormatter.SANSKRIT_DIACRITICS) diacriticCount++
        }
        if (nonWhitespaceCount == 0) return false

        return diacriticCount.toDouble() / nonWhitespaceCount >= VERSE_DIACRITIC_DENSITY_THRESHOLD
    }
}
