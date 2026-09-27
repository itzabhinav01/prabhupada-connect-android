package com.prabhupadaconnect.vedabase.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.prabhupadaconnect.vedabase.core.model.HighlightColor
import com.prabhupadaconnect.vedabase.highlight.HighlightRenderer

/**
 * The Reading screen's contextual action bar for a live text selection:
 * Highlight (yellow/green/blue), Add Note, Copy, Share, Dismiss - per the
 * "Native Contextual Floating Action Menu" requirement.
 *
 * A plain bottom bar driven directly by whether a selection is currently
 * pending (`visible`), not a `Popup`/`TextToolbar` - see
 * [com.prabhupadaconnect.vedabase.ui.reading.HighlightableBlock]'s doc
 * comment for why: a Popup mounted in response to `TextToolbar.showMenu`
 * fights `BasicTextField`'s own focus/selection state machine and can loop
 * showing and hiding itself without ever rendering a visible frame. A
 * normal Composable anchored to the screen's own state has no such lifecycle
 * to fight.
 */
@Composable
fun SelectionActionBar(
    visible: Boolean,
    onHighlight: (HighlightColor) -> Unit,
    onAddNote: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                ColorDot(HighlightColor.Yellow, onClick = { onHighlight(HighlightColor.Yellow) })
                ColorDot(HighlightColor.Green, onClick = { onHighlight(HighlightColor.Green) })
                ColorDot(HighlightColor.Blue, onClick = { onHighlight(HighlightColor.Blue) })
                IconButton(onClick = onAddNote) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = "Add note", tint = MaterialTheme.colorScheme.inverseOnSurface)
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy reference & text", tint = MaterialTheme.colorScheme.inverseOnSurface)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.inverseOnSurface)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss selection", tint = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
    }
}

@Composable
private fun ColorDot(color: HighlightColor, onClick: () -> Unit) {
    val desc = when (color) {
        HighlightColor.Yellow -> "Highlight with Color 1 (Yellow)"
        HighlightColor.Green -> "Highlight with Color 2 (Green)"
        HighlightColor.Blue -> "Highlight with Color 3 (Blue)"
    }
    IconButton(onClick = onClick) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(HighlightRenderer.swatchColorFor(color))
        )
    }
}
