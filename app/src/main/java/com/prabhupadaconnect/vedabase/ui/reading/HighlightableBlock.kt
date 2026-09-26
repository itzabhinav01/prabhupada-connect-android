package com.prabhupadaconnect.vedabase.ui.reading

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val annotated = remember(text, highlights) { HighlightRenderer.render(text, highlights) }
    val focusRequester = remember { FocusRequester() }

    // Re-apply highlight spans whenever the highlight set changes (a new
    // highlight created, one removed) - but ONLY while there's no active
    // selection. Overwriting `fieldValue` with a freshly-built
    // AnnotatedString mid-gesture (e.g. right as a long-press is resolving
    // into a word selection) was observed to make BasicTextField's
    // selection/toolbar state machine treat it as an external value change
    // and immediately hide the just-shown selection toolbar in a tight
    // show/hide loop, since it never got a settled frame to react to a
    // stable value.
    if (fieldValue.selection.collapsed &&
        (fieldValue.annotatedString.text != annotated.text || fieldValue.annotatedString.spanStyles != annotated.spanStyles)
    ) {
        fieldValue = fieldValue.copy(annotatedString = annotated)
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue
            val selection = newValue.selection
            if (!selection.collapsed) {
                // The selection toolbar's show/hide lifecycle is driven by
                // TextFieldSelectionManager, which treats an unfocused field
                // as a transient selection and hides the toolbar almost as
                // soon as it appears - explicitly claiming focus here is
                // what makes it stay open the way a real focused, selectable
                // text field's does.
                focusRequester.requestFocus()
                val start = minOf(selection.start, selection.end)
                val end = maxOf(selection.start, selection.end)
                onSelectionChanged(start, end, text.substring(start.coerceIn(0, text.length), end.coerceIn(0, text.length)))
            }
        },
        readOnly = true,
        textStyle = textStyle.copy(
            color = if (textStyle.color.isUnspecified) androidx.compose.material3.LocalContentColor.current else textStyle.color
        ),
        modifier = modifier.focusRequester(focusRequester)
    )
}
