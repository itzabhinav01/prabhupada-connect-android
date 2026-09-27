package com.prabhupadaconnect.vedabase.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp

/**
 * A subtly shaded card with a left primary-colored border, for a stanza
 * quoted inline in a purport (see [com.prabhupadaconnect.vedabase.core.util.PurportBlockDetector]) -
 * matching vedabase.io's own visual convention for setting a quoted verse
 * apart from the surrounding prose.
 */
@Composable
fun QuoteBlockCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    val borderWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .drawWithContent {
                drawContent()
                drawRect(color = borderColor, size = size.copy(width = borderWidthPx))
            }
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp)
    ) {
        content()
    }
}
