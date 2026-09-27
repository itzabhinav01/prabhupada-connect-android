package com.prabhupadaconnect.vedabase.highlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.prabhupadaconnect.vedabase.core.model.Highlight
import com.prabhupadaconnect.vedabase.core.model.HighlightColor

/**
 * Renders a corpus text block (Transliteration / Synonyms / Translation /
 * one Purport paragraph) as an [AnnotatedString] with every active
 * [Highlight] painted as a background color span over its exact character
 * range - the Android counterpart of the desktop app's WebView highlight
 * overlay.
 *
 * Text-representation note: the desktop app's own reader is a WebView that
 * paints highlights by searching for each highlight's `selectedText`
 * snapshot inside the *displayed* (already prose-cleaned) text rather than
 * seeking to `startOffset` directly - `startOffset`/`length` are the
 * canonical anchor used for overlap-rejection and for reconstructing intent,
 * but rendering itself is content-addressed, not position-addressed. This
 * renderer follows the same strategy: it is what makes a highlight created
 * on one client (whose corpus field text ships with hard-wrap artifacts
 * removed differently, or not at all) still land in the right place when
 * synced to and rendered on another. A highlight resolves, in order:
 *   1. By searching for its `selectedText` snapshot in the displayed text
 *      (skipping ranges already claimed by an earlier highlight, so two
 *      highlights that happen to share the same snippet text don't both
 *      collapse onto the first occurrence).
 *   2. If `selectedText` is blank or not found (a legacy block-level
 *      highlight, or text that no longer matches after a corpus update),
 *      falls back to `startOffset`/`length` directly, clamped to bounds.
 */
object HighlightRenderer {

    fun colorFor(color: HighlightColor): Color = when (color) {
        HighlightColor.Yellow -> Color(0x55FDD835)
        HighlightColor.Green -> Color(0x554CAF50)
        HighlightColor.Blue -> Color(0x5542A5F5)
    }

    private data class ResolvedRange(val start: Int, val end: Int, val color: Color)

    private fun resolveRanges(text: String, highlights: List<Highlight>): List<ResolvedRange> {
        if (text.isEmpty() || highlights.isEmpty()) return emptyList()

        val claimed = BooleanArray(text.length)
        val resolved = mutableListOf<ResolvedRange>()

        // Highlights are resolved in creation order so that repeated
        // identical snippets in the same field claim their occurrences
        // left-to-right in the order the user actually made them.
        val ordered = highlights.filter { !it.isDeleted }.sortedBy { it.createdUtc }

        for (h in ordered) {
            val snippet = h.selectedText
            var start = -1
            var end = -1

            if (!snippet.isNullOrEmpty()) {
                var searchFrom = 0
                while (searchFrom <= text.length - snippet.length) {
                    val idx = text.indexOf(snippet, searchFrom)
                    if (idx < 0) break
                    val candidateEnd = idx + snippet.length
                    val alreadyClaimed = (idx until candidateEnd).any { claimed.getOrElse(it) { true } }
                    if (!alreadyClaimed) {
                        start = idx
                        end = candidateEnd
                        break
                    }
                    searchFrom = idx + 1
                }
            }

            if (start < 0 && !h.isLegacyBlockLevel) {
                val fallbackStart = h.startOffset.coerceIn(0, text.length)
                val fallbackEnd = (h.startOffset + h.length).coerceIn(fallbackStart, text.length)
                if (fallbackEnd > fallbackStart) {
                    start = fallbackStart
                    end = fallbackEnd
                }
            }

            if (start in 0 until end && end <= text.length) {
                for (i in start until end) claimed[i] = true
                resolved.add(ResolvedRange(start, end, colorFor(h.color)))
            }
        }

        return resolved.sortedBy { it.start }
    }

    /** Builds the styled [AnnotatedString] for [text] with every entry of [highlights] painted. */
    fun render(text: String, highlights: List<Highlight>): AnnotatedString = render(AnnotatedString(text), highlights)

    /**
     * Same as [render], but preserves any styling already present on [base]
     * (e.g. [com.prabhupadaconnect.vedabase.core.util.SynonymsFormatter]'s
     * lemma coloring) instead of starting from plain text - highlight
     * background spans are layered on top of, never in place of, that
     * existing styling.
     */
    fun render(base: AnnotatedString, highlights: List<Highlight>): AnnotatedString {
        val ranges = resolveRanges(base.text, highlights)
        if (ranges.isEmpty()) return base

        return buildAnnotatedString {
            append(base)
            for (r in ranges) {
                addStyle(SpanStyle(background = r.color), r.start, r.end)
            }
        }
    }

    /**
     * True if a proposed new highlight [start, start+length) would overlap
     * any existing active highlight already anchored to this exact field -
     * the same deterministic, non-merging rejection rule as the desktop
     * app's `Highlight.RangesOverlap` (adjacent ranges do not overlap).
     */
    fun findOverlap(existing: List<Highlight>, start: Int, length: Int): Highlight? =
        existing.firstOrNull { !it.isDeleted && it.overlapsWith(start, length) }
}
