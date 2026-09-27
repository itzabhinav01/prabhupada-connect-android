package com.prabhupadaconnect.vedabase.core.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Splits a corpus word-for-word Synonyms field ("paśya—behold; etām—this;
 * ...") into its Sanskrit/Bengali lemma and English gloss, so they can be
 * styled differently - vedabase.io itself bolds and colors the lemma,
 * leaving the gloss in the ordinary body color, instead of rendering both in
 * one flat run that makes the boundary hard to read.
 *
 * The corpus's own delimiter between a lemma and its gloss is an em dash
 * ("—"); a plain hyphen is never the delimiter; it always belongs to a
 * hyphenated compound term (e.g. "pāṇḍu-putrāṇām"), so only the em dash is
 * ever split on.
 */
object SynonymsFormatter {

    private const val ENTRY_SEPARATOR = "; "
    private const val LEMMA_DELIMITER = '—' // em dash "—"

    fun format(
        raw: String,
        lemmaColor: Color,
        delimiterColor: Color
    ): AnnotatedString {
        if (raw.isBlank()) return AnnotatedString(raw)

        val entries = raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }

        return buildAnnotatedString {
            entries.forEachIndexed { index, entry ->
                val dashIndex = entry.indexOf(LEMMA_DELIMITER)
                if (dashIndex < 0) {
                    append(entry)
                } else {
                    val lemma = entry.substring(0, dashIndex).trim()
                    val gloss = entry.substring(dashIndex + 1).trim()
                    withStyle(SpanStyle(color = lemmaColor, fontWeight = FontWeight.Bold)) {
                        append(lemma)
                    }
                    withStyle(SpanStyle(color = delimiterColor)) {
                        append(LEMMA_DELIMITER.toString())
                    }
                    append(gloss)
                }
                if (index != entries.lastIndex) append(ENTRY_SEPARATOR)
            }
        }
    }
}
