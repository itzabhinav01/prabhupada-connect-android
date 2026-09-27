package com.prabhupadaconnect.vedabase.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prabhupadaconnect.vedabase.core.model.AppSettings
import com.prabhupadaconnect.vedabase.core.model.SongPayload
import com.prabhupadaconnect.vedabase.core.model.SongStanza
import com.prabhupadaconnect.vedabase.core.util.SynonymsFormatter
import com.prabhupadaconnect.vedabase.ui.theme.SanskritChantingColor
import com.prabhupadaconnect.vedabase.ui.theme.bodyTextStyle
import com.prabhupadaconnect.vedabase.ui.theme.transliterationTextStyle
import com.prabhupadaconnect.vedabase.ui.theme.translationTextStyle

/**
 * Renders a [SongPayload] - the structured shape ~105 SVA/TMG records embed
 * as JSON instead of plain prose - as a proper song/mantra layout instead of
 * the raw `{"type": "song", ...}` string vedabase.io's own corpus export
 * otherwise leaks straight onto the screen.
 */
@Composable
fun SongView(payload: SongPayload, settings: AppSettings) {
    Column {
        Text(payload.bannerTitle, style = MaterialTheme.typography.titleLarge)

        if (payload.subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            payload.subtitle.split("\n").forEach { line ->
                if (line.isNotBlank()) {
                    Text(
                        line,
                        style = MaterialTheme.typography.titleSmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (payload.intro.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            payload.intro.split("\n\n").forEach { para ->
                if (para.isNotBlank()) {
                    Text(para, style = bodyTextStyle(settings.fontSize, settings.lineSpacing))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        payload.stanzas.forEachIndexed { index, stanza ->
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            StanzaView(stanza, index, settings)
        }

        if (payload.notes.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Notes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            MarkdownLiteText(payload.notes, settings)
        }
    }
}

@Composable
private fun StanzaView(stanza: SongStanza, index: Int, settings: AppSettings) {
    val label = stanza.label.ifBlank { "Stanza ${index + 1}" }
    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))

    if (stanza.lines.isNotEmpty()) {
        Text(
            stanza.lines.joinToString("\n"),
            style = transliterationTextStyle(settings.fontSize, settings.lineSpacing),
            color = SanskritChantingColor
        )
        Spacer(Modifier.height(10.dp))
    }

    if (stanza.synonyms.isNotBlank()) {
        Text(
            SynonymsFormatter.format(
                stanza.synonyms,
                lemmaColor = MaterialTheme.colorScheme.primary,
                delimiterColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            style = bodyTextStyle(settings.fontSize, settings.lineSpacing)
        )
        Spacer(Modifier.height(10.dp))
    }

    if (stanza.translation.isNotBlank()) {
        Text(stanza.translation, style = translationTextStyle(settings.fontSize, settings.lineSpacing))
    }
}

/**
 * A deliberately tiny markdown-lite renderer for the freeform `notes` field:
 * paragraphs separated by a blank line, a "### " line as a small heading, a
 * "• " line as a bullet row. Never a general markdown/HTML renderer - this
 * corpus field only ever uses these three shapes.
 */
@Composable
private fun MarkdownLiteText(text: String, settings: AppSettings) {
    val bodyStyle = bodyTextStyle(settings.fontSize, settings.lineSpacing)
    text.split("\n\n").forEach { block ->
        val trimmed = block.trim()
        if (trimmed.isEmpty()) return@forEach
        when {
            trimmed.startsWith("### ") -> {
                Text(
                    trimmed.removePrefix("### "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
            }
            trimmed.startsWith("• ") -> {
                Row {
                    Text("•", style = bodyStyle)
                    Spacer(Modifier.width(8.dp))
                    Text(trimmed.removePrefix("• "), style = bodyStyle)
                }
                Spacer(Modifier.height(8.dp))
            }
            else -> {
                Text(trimmed, style = bodyStyle)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
