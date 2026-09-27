package com.prabhupadaconnect.vedabase.core.util

import com.prabhupadaconnect.vedabase.core.model.SongPayload
import kotlinx.serialization.json.Json

/**
 * Detects and parses the JSON song/mantra payload some corpus records embed
 * in a plain-text field (see [SongPayload]'s doc comment) - so the raw
 * `{"type": "song", ...}` string is never shown to the user as text.
 */
object SongPayloadParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Returns the parsed payload if [raw] is a recognized song/mantra JSON
     * blob, or null if it's blank, not JSON, or JSON of some other shape -
     * callers fall back to normal prose rendering in every null case.
     */
    fun tryParse(raw: String?): SongPayload? {
        val trimmed = raw?.trimStart() ?: return null
        if (!trimmed.startsWith("{")) return null
        // Cheap pre-check before paying for a full parse attempt.
        if (!trimmed.contains("\"type\"")) return null

        val payload = try {
            json.decodeFromString(SongPayload.serializer(), trimmed)
        } catch (_: Exception) {
            return null
        }

        return payload.takeIf { it.type in SongPayload.KNOWN_TYPES }
    }
}
