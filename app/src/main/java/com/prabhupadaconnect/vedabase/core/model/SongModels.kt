package com.prabhupadaconnect.vedabase.core.model

import kotlinx.serialization.Serializable

/**
 * Structured payload for a song/mantra record (Songs of the Vaiṣṇava
 * Ācāryas, Temple Mantra Guide). ~105 records in the corpus store this as a
 * JSON blob in their `Purports` column instead of plain prose - matches the
 * shape actually found in the corpus (verified directly against
 * prabhupada_corpus.db), which additionally carries a free-form `notes`
 * field beyond the stanza list itself.
 */
@Serializable
data class SongPayload(
    val type: String = "",
    val bannerTitle: String = "",
    val subtitle: String = "",
    val intro: String = "",
    val stanzas: List<SongStanza> = emptyList(),
    val notes: String = ""
) {
    companion object {
        /** The only `type` values the corpus actually uses for this payload shape. */
        val KNOWN_TYPES = setOf("song", "mantra")
    }
}

@Serializable
data class SongStanza(
    val label: String = "",
    val lines: List<String> = emptyList(),
    val synonyms: String = "",
    val translation: String = ""
)
