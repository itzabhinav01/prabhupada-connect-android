package com.prabhupadaconnect.vedabase.data.user

import androidx.room.withTransaction
import com.prabhupadaconnect.vedabase.core.model.BookmarkCollection
import com.prabhupadaconnect.vedabase.core.model.Highlight
import com.prabhupadaconnect.vedabase.core.model.HighlightColor
import com.prabhupadaconnect.vedabase.core.model.LocalChangeSet
import com.prabhupadaconnect.vedabase.core.model.ReadingHistoryEntry
import com.prabhupadaconnect.vedabase.core.model.RemoteChangeSet
import com.prabhupadaconnect.vedabase.core.model.UserBookmark
import com.prabhupadaconnect.vedabase.core.model.UserNote
import com.prabhupadaconnect.vedabase.core.util.IsoTime
import com.prabhupadaconnect.vedabase.data.user.entity.BookmarkCollectionEntity
import com.prabhupadaconnect.vedabase.data.user.entity.BookmarkEntity
import com.prabhupadaconnect.vedabase.data.user.entity.HighlightEntity
import com.prabhupadaconnect.vedabase.data.user.entity.NoteEntity
import com.prabhupadaconnect.vedabase.data.user.entity.ReadingHistoryEntity
import com.prabhupadaconnect.vedabase.data.user.entity.SyncMetadataEntity
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Read/write gateway onto the user's own research data (bookmarks,
 * collections, highlights, notes, reading history, sync bookkeeping).
 * Ported 1:1 from the desktop app's `SqliteUserRepository` (C#) - same
 * soft-delete (tombstone) convention, same "undelete before insert"
 * bookmark semantics, same incremental-change / LWW-merge sync pipeline.
 */
@Singleton
class UserRepository @Inject constructor(
    private val database: UserDatabase
) {
    private val collectionDao = database.bookmarkCollectionDao()
    private val bookmarkDao = database.bookmarkDao()
    private val highlightDao = database.highlightDao()
    private val noteDao = database.noteDao()
    private val historyDao = database.readingHistoryDao()
    private val syncMetadataDao = database.syncMetadataDao()

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private fun BookmarkCollectionEntity.toDomain() = BookmarkCollection(
        id = id, name = name, sortOrder = sortOrder,
        createdUtc = IsoTime.parse(createdUtc), updatedUtc = IsoTime.parse(updatedUtc),
        deletedUtc = deletedUtc?.let(IsoTime::parse)
    )

    private fun BookmarkEntity.toDomain() = UserBookmark(
        id = id, recordKey = recordKey, collectionId = collectionId, title = title,
        createdUtc = IsoTime.parse(createdUtc), updatedUtc = IsoTime.parse(updatedUtc),
        deletedUtc = deletedUtc?.let(IsoTime::parse)
    )

    private fun HighlightEntity.toDomain() = Highlight(
        id = id, recordKey = recordKey, field = field,
        color = HighlightColor.valueOf(color), startOffset = startOffset, length = length,
        selectedText = selectedText,
        createdUtc = IsoTime.parse(createdUtc), updatedUtc = IsoTime.parse(updatedUtc),
        deletedUtc = deletedUtc?.let(IsoTime::parse)
    )

    private fun NoteEntity.toDomain() = UserNote(
        id = id, recordKey = recordKey, title = title, content = content,
        field = field, startOffset = startOffset ?: -1, length = length ?: -1,
        createdUtc = IsoTime.parse(createdUtc), updatedUtc = IsoTime.parse(updatedUtc),
        deletedUtc = deletedUtc?.let(IsoTime::parse)
    )

    private fun ReadingHistoryEntity.toDomain() = ReadingHistoryEntry(
        recordKey = recordKey, lastOpenedUtc = IsoTime.parse(lastOpenedUtc), openCount = openCount
    )

    // ------------------------------------------------------------------
    // Bookmarks
    // ------------------------------------------------------------------

    fun observeActiveBookmarks(): Flow<List<UserBookmark>> =
        bookmarkDao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeBookmarksInCollection(collectionId: String?): Flow<List<UserBookmark>> =
        bookmarkDao.observeByCollection(collectionId).map { list -> list.map { it.toDomain() } }

    suspend fun isBookmarked(recordKey: String): Boolean = withContext(Dispatchers.IO) {
        bookmarkDao.getActiveByRecordKey(recordKey) != null
    }

    /**
     * Idempotent: if already actively bookmarked, does nothing. If a
     * previously-removed bookmark exists for this verse, undeletes the most
     * recently touched one instead of inserting a duplicate row.
     */
    suspend fun addBookmark(recordKey: String): Unit = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (bookmarkDao.getActiveByRecordKey(recordKey) != null) return@withTransaction

            val nowIso = IsoTime.now()
            val mostRecentlyTouched = bookmarkDao.getMostRecentlyTombstoned(recordKey)

            if (mostRecentlyTouched != null) {
                bookmarkDao.upsert(mostRecentlyTouched.copy(deletedUtc = null, updatedUtc = nowIso))
            } else {
                bookmarkDao.upsert(
                    BookmarkEntity(
                        id = UUID.randomUUID().toString(), recordKey = recordKey,
                        collectionId = null, title = null,
                        createdUtc = nowIso, updatedUtc = nowIso, deletedUtc = null
                    )
                )
            }
        }
    }

    suspend fun removeBookmark(recordKey: String): Unit = withContext(Dispatchers.IO) {
        val active = bookmarkDao.getActiveByRecordKey(recordKey) ?: return@withContext
        bookmarkDao.tombstone(active.id, IsoTime.now())
    }

    suspend fun setBookmarkCollection(recordKey: String, collectionId: String?): Unit = withContext(Dispatchers.IO) {
        val active = bookmarkDao.getActiveByRecordKey(recordKey) ?: return@withContext
        bookmarkDao.upsert(active.copy(collectionId = collectionId, updatedUtc = IsoTime.now()))
    }

    // ------------------------------------------------------------------
    // Collections
    // ------------------------------------------------------------------

    fun observeCollections(): Flow<List<BookmarkCollection>> =
        collectionDao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun createCollection(name: String, sortOrder: Int = 0): BookmarkCollection = withContext(Dispatchers.IO) {
        val nowIso = IsoTime.now()
        val entity = BookmarkCollectionEntity(
            id = UUID.randomUUID().toString(), name = name, sortOrder = sortOrder,
            createdUtc = nowIso, updatedUtc = nowIso, deletedUtc = null
        )
        collectionDao.upsert(entity)
        entity.toDomain()
    }

    // ------------------------------------------------------------------
    // Highlights - overlap rejection is enforced by the caller (the Reading
    // screen's ViewModel, via Highlight.rangesOverlap against the field's
    // currently active highlights) before this is ever invoked, exactly as
    // on the desktop app.
    // ------------------------------------------------------------------

    fun observeHighlightsForRecord(recordKey: String): Flow<List<Highlight>> =
        highlightDao.observeForRecord(recordKey).map { list -> list.map { it.toDomain() } }

    suspend fun getActiveHighlightsForField(recordKey: String, field: String): List<Highlight> =
        withContext(Dispatchers.IO) { highlightDao.getActiveForField(recordKey, field).map { it.toDomain() } }

    suspend fun addHighlight(
        recordKey: String,
        field: String,
        startOffset: Int,
        length: Int,
        selectedText: String,
        color: HighlightColor
    ): Highlight = withContext(Dispatchers.IO) {
        val nowIso = IsoTime.now()
        val entity = HighlightEntity(
            id = UUID.randomUUID().toString(), recordKey = recordKey, field = field,
            color = color.name, startOffset = startOffset, length = length, selectedText = selectedText,
            createdUtc = nowIso, updatedUtc = nowIso, deletedUtc = null
        )
        highlightDao.upsert(entity)
        entity.toDomain()
    }

    suspend fun removeHighlight(highlightId: String): Unit = withContext(Dispatchers.IO) {
        highlightDao.tombstone(highlightId, IsoTime.now())
    }

    suspend fun updateHighlightColor(highlightId: String, color: HighlightColor): Unit = withContext(Dispatchers.IO) {
        val existing = highlightDao.getById(highlightId) ?: return@withContext
        highlightDao.upsert(existing.copy(color = color.name, updatedUtc = IsoTime.now()))
    }

    // ------------------------------------------------------------------
    // Notes
    // ------------------------------------------------------------------

    fun observeNotesForRecord(recordKey: String): Flow<List<UserNote>> =
        noteDao.observeForRecord(recordKey).map { list -> list.map { it.toDomain() } }

    fun observeGeneralNotes(): Flow<List<UserNote>> =
        noteDao.observeGeneral().map { list -> list.map { it.toDomain() } }

    fun observeAllNotes(): Flow<List<UserNote>> =
        noteDao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun createNote(
        recordKey: String?,
        content: String,
        title: String? = null,
        field: String? = null,
        startOffset: Int = -1,
        length: Int = -1
    ): UserNote = withContext(Dispatchers.IO) {
        val nowIso = IsoTime.now()
        val entity = NoteEntity(
            id = UUID.randomUUID().toString(), recordKey = recordKey, title = title, content = content,
            field = field, startOffset = if (field != null) startOffset else null, length = if (field != null) length else null,
            createdUtc = nowIso, updatedUtc = nowIso, deletedUtc = null
        )
        noteDao.upsert(entity)
        entity.toDomain()
    }

    suspend fun updateNote(id: String, content: String, title: String?): Unit = withContext(Dispatchers.IO) {
        val existing = noteDao.getById(id) ?: return@withContext
        noteDao.upsert(existing.copy(content = content, title = title, updatedUtc = IsoTime.now()))
    }

    suspend fun deleteNote(id: String): Unit = withContext(Dispatchers.IO) {
        noteDao.tombstone(id, IsoTime.now())
    }

    // ------------------------------------------------------------------
    // Reading history
    // ------------------------------------------------------------------

    fun observeRecentlyRead(limit: Int = 50): Flow<List<ReadingHistoryEntry>> =
        historyDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun recordOpened(recordKey: String): Unit = withContext(Dispatchers.IO) {
        val existing = historyDao.get(recordKey)
        historyDao.upsert(
            ReadingHistoryEntity(
                recordKey = recordKey,
                lastOpenedUtc = IsoTime.now(),
                openCount = (existing?.openCount ?: 0) + 1
            )
        )
    }

    // ------------------------------------------------------------------
    // Sync metadata & device identity
    // ------------------------------------------------------------------

    suspend fun getSyncMetadata(key: String): String? = withContext(Dispatchers.IO) {
        syncMetadataDao.get(key)?.value
    }

    suspend fun setSyncMetadata(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        syncMetadataDao.upsert(SyncMetadataEntity(key, value, IsoTime.now()))
    }

    suspend fun getDeviceId(): String = withContext(Dispatchers.IO) {
        getSyncMetadata("DeviceId")?.takeIf { it.isNotBlank() } ?: run {
            val newId = UUID.randomUUID().toString()
            setSyncMetadata("DeviceId", newId)
            newId
        }
    }

    // ------------------------------------------------------------------
    // Sync: local change enumeration
    // ------------------------------------------------------------------

    suspend fun getLocalChanges(sinceUtc: Instant?): LocalChangeSet = withContext(Dispatchers.IO) {
        val sinceIso = sinceUtc?.let(IsoTime::format)
        LocalChangeSet(
            collections = collectionDao.getChangedSince(sinceIso).map { it.toDomain() },
            bookmarks = bookmarkDao.getChangedSince(sinceIso).map { it.toDomain() },
            highlights = highlightDao.getChangedSince(sinceIso).map { it.toDomain() },
            notes = noteDao.getChangedSince(sinceIso).map { it.toDomain() }
        )
    }

    // ------------------------------------------------------------------
    // Sync: Last-Write-Wins merge of a pulled RemoteChangeSet into local
    // storage. Ported 1:1 from the desktop app's
    // `SqliteUserRepository.MergeSyncChangesAsync` (C#):
    //   - Existing row (by Id) not found        -> insert as-is.
    //   - Existing row found, incoming newer     -> remote wins, overwrite.
    //   - Existing row found, incoming older     -> local wins, conflict++.
    //   - Existing row found, exact tie          -> deterministic tie-break:
    //     the row whose Id sorts first ordinally against localDeviceId wins
    //     (`entity.id < localDeviceId` -> remote wins). Never a coin flip,
    //     never "last writer arbitrarily" - the same two devices reprocessing
    //     the same pulled batch always resolve every tie identically.
    //   - Bookmarks additionally enforce "at most one active bookmark per
    //     RecordKey": an incoming active bookmark that wins tombstones any
    //     other active bookmark already on that verse (either an existing
    //     row being updated, or a sibling row when the incoming Id is new).
    // Returns the number of conflicts (local-wins cases) encountered.
    // ------------------------------------------------------------------

    suspend fun mergeSyncChanges(remoteChanges: RemoteChangeSet, localDeviceId: String): Int =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                var conflicts = 0
                val nowIso = IsoTime.now()

                // 1. Collections: LWW by Id
                for (c in remoteChanges.collections) {
                    val existing = collectionDao.getById(c.id)
                    if (existing == null) {
                        collectionDao.upsert(
                            BookmarkCollectionEntity(
                                id = c.id, name = c.name, sortOrder = c.sortOrder,
                                createdUtc = IsoTime.format(c.createdUtc), updatedUtc = IsoTime.format(c.updatedUtc),
                                deletedUtc = c.deletedUtc?.let(IsoTime::format)
                            )
                        )
                        continue
                    }

                    val existingUpdated = IsoTime.parse(existing.updatedUtc)
                    val remoteWins = when {
                        c.updatedUtc > existingUpdated -> true
                        c.updatedUtc < existingUpdated -> { conflicts++; false }
                        else -> c.id < localDeviceId
                    }

                    if (remoteWins) {
                        collectionDao.upsert(
                            existing.copy(
                                name = c.name, sortOrder = c.sortOrder,
                                createdUtc = IsoTime.format(c.createdUtc), updatedUtc = IsoTime.format(c.updatedUtc),
                                deletedUtc = c.deletedUtc?.let(IsoTime::format)
                            )
                        )
                    }
                }

                // 2. Bookmarks: LWW with verse uniqueness guard
                for (b in remoteChanges.bookmarks) {
                    val incomingUpdatedIso = IsoTime.format(b.updatedUtc)
                    val existing = bookmarkDao.getById(b.id)

                    if (existing != null) {
                        val existingUpdated = IsoTime.parse(existing.updatedUtc)
                        val remoteWins = when {
                            b.updatedUtc > existingUpdated -> true
                            b.updatedUtc < existingUpdated -> { conflicts++; false }
                            else -> b.id < localDeviceId
                        }

                        if (remoteWins) {
                            if (b.deletedUtc == null) {
                                bookmarkDao.tombstoneOtherActive(b.recordKey, b.id, nowIso)
                            }
                            bookmarkDao.upsert(
                                existing.copy(
                                    recordKey = b.recordKey, collectionId = b.collectionId, title = b.title,
                                    createdUtc = IsoTime.format(b.createdUtc), updatedUtc = incomingUpdatedIso,
                                    deletedUtc = b.deletedUtc?.let(IsoTime::format)
                                )
                            )
                        }
                    } else {
                        if (b.deletedUtc == null) {
                            val activeSibling = bookmarkDao.getActiveByRecordKey(b.recordKey)
                            when {
                                activeSibling == null -> insertBookmark(b)
                                b.updatedUtc >= IsoTime.parse(activeSibling.updatedUtc) -> {
                                    bookmarkDao.tombstone(activeSibling.id, nowIso)
                                    insertBookmark(b)
                                }
                                else -> {
                                    conflicts++
                                    // Local wins: insert the incoming row, but as tombstoned -
                                    // its identity is preserved for future sync passes without
                                    // disturbing the verse's currently-active local bookmark.
                                    insertBookmark(b.copy(deletedUtc = b.updatedUtc))
                                }
                            }
                        } else {
                            insertBookmark(b)
                        }
                    }
                }

                // 3. Highlights: LWW
                for (h in remoteChanges.highlights) {
                    val existing = highlightDao.getById(h.id)
                    if (existing == null) {
                        highlightDao.upsert(h.toEntity())
                        continue
                    }

                    val existingUpdated = IsoTime.parse(existing.updatedUtc)
                    val remoteWins = when {
                        h.updatedUtc > existingUpdated -> true
                        h.updatedUtc < existingUpdated -> { conflicts++; false }
                        else -> h.id < localDeviceId
                    }
                    if (remoteWins) highlightDao.upsert(h.toEntity())
                }

                // 4. Notes: LWW
                for (n in remoteChanges.notes) {
                    val existing = noteDao.getById(n.id)
                    if (existing == null) {
                        noteDao.upsert(n.toEntity())
                        continue
                    }

                    val existingUpdated = IsoTime.parse(existing.updatedUtc)
                    val remoteWins = when {
                        n.updatedUtc > existingUpdated -> true
                        n.updatedUtc < existingUpdated -> { conflicts++; false }
                        else -> n.id < localDeviceId
                    }
                    if (remoteWins) noteDao.upsert(n.toEntity())
                }

                conflicts
            }
        }

    private suspend fun insertBookmark(b: UserBookmark) {
        bookmarkDao.upsert(
            BookmarkEntity(
                id = b.id, recordKey = b.recordKey, collectionId = b.collectionId, title = b.title,
                createdUtc = IsoTime.format(b.createdUtc), updatedUtc = IsoTime.format(b.updatedUtc),
                deletedUtc = b.deletedUtc?.let(IsoTime::format)
            )
        )
    }

    private fun Highlight.toEntity() = HighlightEntity(
        id = id, recordKey = recordKey, field = field, color = color.name,
        startOffset = startOffset, length = length, selectedText = selectedText,
        createdUtc = IsoTime.format(createdUtc), updatedUtc = IsoTime.format(updatedUtc),
        deletedUtc = deletedUtc?.let(IsoTime::format)
    )

    private fun UserNote.toEntity() = NoteEntity(
        id = id, recordKey = recordKey, title = title, content = content,
        field = field, startOffset = if (field != null) startOffset else null, length = if (field != null) length else null,
        createdUtc = IsoTime.format(createdUtc), updatedUtc = IsoTime.format(updatedUtc),
        deletedUtc = deletedUtc?.let(IsoTime::format)
    )
}
