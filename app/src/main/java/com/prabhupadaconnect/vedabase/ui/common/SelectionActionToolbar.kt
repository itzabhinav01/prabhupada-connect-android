package com.prabhupadaconnect.vedabase.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.prabhupadaconnect.vedabase.core.model.HighlightColor
import com.prabhupadaconnect.vedabase.highlight.HighlightRenderer

/**
 * The Reading screen's contextual floating action menu, shown in place of
 * the OS default copy/paste toolbar whenever the user selects text inside a
 * verse block: Highlight (yellow/green/blue), Add Note, Copy, Share - per
 * the "Native Contextual Floating Action Menu" requirement.
 */
class SelectionActionToolbar(
    private val onHighlight: (HighlightColor) -> Unit,
    private val onAddNote: () -> Unit,
    private val onCopy: () -> Unit,
    private val onShare: () -> Unit
) : TextToolbar {

    private var statusState by mutableStateOf(TextToolbarStatus.Hidden)
    override val status: TextToolbarStatus get() = statusState

    var rect: Rect? = null
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        this.rect = rect
        statusState = TextToolbarStatus.Shown
    }

    override fun hide() {
        statusState = TextToolbarStatus.Hidden
        rect = null
    }

    @Composable
    fun Content() {
        val currentRect = rect ?: return
        if (statusState != TextToolbarStatus.Shown) return

        Popup(popupPositionProvider = rectPositionProvider(currentRect)) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp
            ) {
                Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    ColorDot(HighlightColor.Yellow) { onHighlight(HighlightColor.Yellow); hide() }
                    ColorDot(HighlightColor.Green) { onHighlight(HighlightColor.Green); hide() }
                    ColorDot(HighlightColor.Blue) { onHighlight(HighlightColor.Blue); hide() }
                    IconButton(onClick = { onAddNote(); hide() }) {
                        Icon(Icons.Filled.NoteAdd, contentDescription = "Add note", tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    IconButton(onClick = { onCopy(); hide() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy reference & text", tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    IconButton(onClick = { onShare(); hide() }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
        }
    }

    @Composable
    private fun ColorDot(color: HighlightColor, onClick: () -> Unit) {
        IconButton(onClick = onClick) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(HighlightRenderer.colorFor(color).copy(alpha = 1f), CircleShape)
            )
        }
    }

    private fun rectPositionProvider(rect: Rect) = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: androidx.compose.ui.unit.IntRect,
            windowSize: androidx.compose.ui.unit.IntSize,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            popupContentSize: androidx.compose.ui.unit.IntSize
        ): androidx.compose.ui.unit.IntOffset {
            val x = (rect.left + rect.right) / 2 - popupContentSize.width / 2
            val y = rect.top - popupContentSize.height - 16
            return androidx.compose.ui.unit.IntOffset(x.toInt(), y.coerceAtLeast(0f).toInt())
        }
    }
}
