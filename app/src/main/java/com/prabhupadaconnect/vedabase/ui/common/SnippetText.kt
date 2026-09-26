package com.prabhupadaconnect.vedabase.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import com.prabhupadaconnect.vedabase.data.corpus.SnippetGenerator

/**
 * Renders a [SnippetGenerator]-produced string (matched term wrapped in
 * `«...»`) as an [AnnotatedString] with the match bolded - the Compose-side
 * counterpart of the desktop WebView reader's own `«»`-to-bold handling.
 */
fun snippetToAnnotatedString(marked: String): AnnotatedString {
    if (!marked.contains(SnippetGenerator.MARK_START)) return AnnotatedString(marked)

    return buildAnnotatedString {
        var remaining = marked
        while (true) {
            val startIdx = remaining.indexOf(SnippetGenerator.MARK_START)
            if (startIdx < 0) {
                append(remaining)
                break
            }
            val endIdx = remaining.indexOf(SnippetGenerator.MARK_END, startIdx + SnippetGenerator.MARK_START.length)
            if (endIdx < 0) {
                append(remaining)
                break
            }
            append(remaining.substring(0, startIdx))
            val matched = remaining.substring(startIdx + SnippetGenerator.MARK_START.length, endIdx)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(matched) }
            remaining = remaining.substring(endIdx + SnippetGenerator.MARK_END.length)
        }
    }
}
