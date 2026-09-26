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
 * to the caller.
 *
 * Deliberately does NOT drive the contextual highlight/note action menu via
 * [androidx.compose.ui.platform.TextToolbar] - on-device testing found that
 * a custom `Popup`-based `TextToolbar` fights `BasicTextField`'s own
 * selection/focus state machine (mounting the popup steals focus, which the
 * field reads as "selection dismissed" and hides the popup again, sometimes
 * in a tight loop). Instead, [onSelectionChanged] just reports the raw
 * selection up to [ReadingViewModel]'s `pendingSelection`, and the actual
 * action menu is a plain bottom action bar in [ReadingScreen] driven by that
 * state directly - no toolbar lifecycle to fight.
 */
@Composable
fun HighlightableBlock(
    text: String,
    highlights: List<Highlight>,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    onSelectionChanged: (start: Int, end: Int, selectedText: String) -> Unit
) {
    // Re-keyed on `highlights` (not just `text`): whenever the highlight set
    // actually changes (one created, one removed), the field value resets
    // fresh with a collapsed selection and freshly-rendered spans. This only
    // fires on a real content change - Kotlin's List.equals is structural,
    // so a new list instance with identical elements (a harmless Flow
    // re-emission) does not reset an in-progress selection.
    var fieldValue by remember(text, highlights) {
        mutableStateOf(TextFieldValue(HighlightRenderer.render(text, highlights)))
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue
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
