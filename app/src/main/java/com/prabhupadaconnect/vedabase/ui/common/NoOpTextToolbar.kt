package com.prabhupadaconnect.vedabase.ui.common

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Suppresses the OS default copy/paste bubble on text selection entirely.
 *
 * The Reading screen's highlight/note actions are driven directly by
 * [com.prabhupadaconnect.vedabase.ui.reading.ReadingViewModel]'s
 * `pendingSelection` state via a plain bottom action bar (see
 * [SelectionActionBar]), not by [androidx.compose.ui.platform.TextToolbar] -
 * a custom `Popup`-based `TextToolbar` was found to fight
 * `BasicTextField`'s own selection/focus state machine (see
 * [com.prabhupadaconnect.vedabase.ui.reading.HighlightableBlock]'s doc
 * comment). Installing this no-op instead means a text selection never
 * triggers any system toolbar UI at all, leaving the bottom action bar as
 * the only affordance - exactly the intended UX.
 */
object NoOpTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        // Intentionally does nothing.
    }

    override fun hide() {
        // Intentionally does nothing.
    }
}
