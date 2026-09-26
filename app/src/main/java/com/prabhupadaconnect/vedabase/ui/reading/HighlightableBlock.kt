package com.prabhupadaconnect.vedabase.ui.reading

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.input.TextFieldValue
import com.prabhupadaconnect.vedabase.core.model.Highlight
import com.prabhupadaconnect.vedabase.highlight.HighlightRenderer

/**
 * A read-only, selectable corpus text block with character-precise
 * highlight overlays. Selection is tracked via [TextFieldValue.selection]
 * (Compose's only API that exposes exact character offsets for a live text
 * selection) rather than [androidx.compose.foundation.text.selection.SelectionContainer],
 * which does not expose the selected [androidx.compose.ui.text.TextRange]
 * to the caller. Long-pressing a selection surfaces the app's own
 * [com.prabhupadaconnect.vedabase.ui.common.SelectionActionToolbar] in
 * place of the OS copy/paste menu (installed by the caller via
 * `CompositionLocalProvider(LocalTextToolbar provides toolbar)`).
 */
@Composable
fun HighlightableBlock(
    text: String,
    highlights: List<Highlight>,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    onSelectionChanged: (start: Int, end: Int, selectedText: String) -> Unit
) {
    var fieldValue by remember(text) { mutableStateOf(TextFieldValue(HighlightRenderer.render(text, highlights))) }

    // Re-apply highlight spans whenever the highlight set changes (a new
    // highlight created, one removed) without disturbing the live selection.
    val annotated = remember(text, highlights) { HighlightRenderer.render(text, highlights) }
    if (fieldValue.annotatedString.text != annotated.text || fieldValue.annotatedString.spanStyles != annotated.spanStyles) {
        fieldValue = fieldValue.copy(annotatedString = annotated)
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue.copy(annotatedString = annotated)
            val selection = newValue.selection
            if (!selection.collapsed) {
                val start = minOf(selection.start, selection.end)
                val end = maxOf(selection.start, selection.end)
                onSelectionChanged(start, end, text.substring(start.coerceIn(0, text.length), end.coerceIn(0, text.length)))
            }
        },
        readOnly = true,
        textStyle = textStyle.copy(
            color = if (textStyle.color.isUnspecified) androidx.compose.material3.LocalContentColor.current else textStyle.color
        ),
        modifier = modifier
    )
}
