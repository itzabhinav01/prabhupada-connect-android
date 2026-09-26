package com.prabhupadaconnect.vedabase.core.model

import java.time.Instant

/** One of a small fixed set of highlight colors - a closed set keeps the annotation system restrained. */
enum class HighlightColor { Yellow, Green, Blue }

/**
 * A precise, persistent annotation over an EXACT character range within one
 * displayed content block of one verse - anchored to (recordKey, field,
 * startOffset, length), never to screen coordinates. `field` identifies which
 * block: "Transliteration", "Synonyms", "Translation", or "Purport:{index}"
 * for an individual purport paragraph. startOffset/length are plain Kotlin
 * String (UTF-16 code unit) indices into that exact corpus field value, as
 * read from the frozen corpus - never a transformed or re-normalized copy.
 *
 * A verse may hold many highlights, including several in the same field, as
 * long as their ranges don't overlap (see [rangesOverlap]): overlap is
 * rejected, not merged or silently allowed.
 */
data class Highlight(
    val id: String,
    val recordKey: String,
    val field: String,
    val color: HighlightColor,
    val createdUtc: Instant,
    val updatedUtc: Instant,
    val deletedUtc: Instant? = null,
    val startOffset: Int = -1,
    val length: Int = -1,
    val selectedText: String? = null
) {
    val isDeleted: Boolean get() = deletedUtc != null

    /** Legacy whole-block highlights (pre-precise-offset model) have no real range. */
    val isLegacyBlockLevel: Boolean get() = startOffset < 0 || length <= 0

    fun overlapsWith(start: Int, len: Int): Boolean {
        if (isLegacyBlockLevel || len <= 0) return false
        return rangesOverlap(startOffset, length, start, len)
    }

    companion object {
        /**
         * Deterministic overlap check: [aStart, aStart + aLen) intersects
         * [bStart, bStart + bLen). Adjacent ranges (e.g. [0, 5) and [5, 10))
         * do NOT overlap.
         */
        fun rangesOverlap(aStart: Int, aLen: Int, bStart: Int, bLen: Int): Boolean {
            if (aLen <= 0 || bLen <= 0) return false
            val aEnd = aStart + aLen
            val bEnd = bStart + bLen
            return aStart < bEnd && bStart < aEnd
        }
    }
}

/**
 * A research note. Always belongs to a verse via [recordKey] may be null for
 * a General Research Note with no scripture association. The scripture-range
 * anchor (field/startOffset/length) is optional: null means "a general note
 * about this verse/topic", not "no note".
 */
data class UserNote(
    val id: String,
    val recordKey: String?,
    val title: String?,
    val content: String,
    val createdUtc: Instant,
    val updatedUtc: Instant,
    val deletedUtc: Instant? = null,
    val field: String? = null,
    val startOffset: Int = -1,
    val length: Int = -1
) {
    val isDeleted: Boolean get() = deletedUtc != null
    // `field` here means the constructor property of that name, not Kotlin's
    // contextual backing-field keyword - `this.` is required to disambiguate.
    val hasScriptureAnchor: Boolean get() = !this.field.isNullOrEmpty() && startOffset >= 0 && length > 0
}

data class UserBookmark(
    val id: String,
    val recordKey: String,
    val createdUtc: Instant,
    val updatedUtc: Instant,
    val deletedUtc: Instant? = null,
    val collectionId: String? = null,
    val title: String? = null
) {
    val isDeleted: Boolean get() = deletedUtc != null
}

data class BookmarkCollection(
    val id: String,
    val name: String,
    val createdUtc: Instant,
    val updatedUtc: Instant,
    val deletedUtc: Instant? = null,
    val sortOrder: Int = 0
) {
    val isDeleted: Boolean get() = deletedUtc != null
}

data class SyncMetadataEntry(
    val key: String,
    val value: String,
    val updatedUtc: Instant
)

data class ReadingHistoryEntry(
    val recordKey: String,
    val lastOpenedUtc: Instant,
    val openCount: Int
)

data class UserSearchResult(
    val recordKey: String?,
    val sourceType: String, // "Note", "Bookmark", or "Highlight"
    val contentSnippet: String,
    val timestampUtc: Instant,
    val field: String? = null,
    val startOffset: Int = -1,
    val length: Int = -1,
    val color: HighlightColor? = null
)

/** Set of local entities modified since a given sync checkpoint - active and tombstoned rows both. */
data class LocalChangeSet(
    val collections: List<BookmarkCollection> = emptyList(),
    val bookmarks: List<UserBookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val notes: List<UserNote> = emptyList()
) {
    val totalCount: Int get() = collections.size + bookmarks.size + highlights.size + notes.size
}

/** Set of remote entities fetched from the cloud provider. */
data class RemoteChangeSet(
    val collections: List<BookmarkCollection> = emptyList(),
    val bookmarks: List<UserBookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val notes: List<UserNote> = emptyList()
) {
    val totalCount: Int get() = collections.size + bookmarks.size + highlights.size + notes.size
}
