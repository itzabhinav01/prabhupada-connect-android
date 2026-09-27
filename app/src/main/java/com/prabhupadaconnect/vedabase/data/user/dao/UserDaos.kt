package com.prabhupadaconnect.vedabase.data.user.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.prabhupadaconnect.vedabase.data.user.entity.BookmarkCollectionEntity
import com.prabhupadaconnect.vedabase.data.user.entity.BookmarkEntity
import com.prabhupadaconnect.vedabase.data.user.entity.HighlightEntity
import com.prabhupadaconnect.vedabase.data.user.entity.NoteEntity
import com.prabhupadaconnect.vedabase.data.user.entity.ReadingHistoryEntity
import com.prabhupadaconnect.vedabase.data.user.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkCollectionDao {
    @Query("SELECT * FROM bookmark_collections WHERE deletedUtc IS NULL ORDER BY sortOrder")
    fun observeActive(): Flow<List<BookmarkCollectionEntity>>

    @Query("SELECT * FROM bookmark_collections WHERE id = :id")
    suspend fun getById(id: String): BookmarkCollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookmarkCollectionEntity)

    @Query("SELECT * FROM bookmark_collections WHERE :sinceUtc IS NULL OR updatedUtc > :sinceUtc")
    suspend fun getChangedSince(sinceUtc: String?): List<BookmarkCollectionEntity>
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE deletedUtc IS NULL ORDER BY createdUtc DESC")
    fun observeActive(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE collectionId = :collectionId AND deletedUtc IS NULL ORDER BY createdUtc DESC")
    fun observeByCollection(collectionId: String?): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE recordKey = :recordKey AND deletedUtc IS NULL LIMIT 1")
    suspend fun getActiveByRecordKey(recordKey: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE recordKey = :recordKey AND deletedUtc IS NOT NULL ORDER BY updatedUtc DESC LIMIT 1")
    suspend fun getMostRecentlyTombstoned(recordKey: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getById(id: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookmarkEntity)

    @Query("UPDATE bookmarks SET deletedUtc = :nowIso, updatedUtc = :nowIso WHERE id = :id")
    suspend fun tombstone(id: String, nowIso: String)

    @Query("UPDATE bookmarks SET deletedUtc = :nowIso, updatedUtc = :nowIso WHERE recordKey = :recordKey AND id != :excludeId AND deletedUtc IS NULL")
    suspend fun tombstoneOtherActive(recordKey: String, excludeId: String, nowIso: String)

    @Query("SELECT * FROM bookmarks WHERE :sinceUtc IS NULL OR updatedUtc > :sinceUtc")
    suspend fun getChangedSince(sinceUtc: String?): List<BookmarkEntity>
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE recordKey = :recordKey AND deletedUtc IS NULL")
    fun observeForRecord(recordKey: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE deletedUtc IS NULL ORDER BY createdUtc DESC")
    fun observeAll(): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE recordKey = :recordKey AND field = :field AND deletedUtc IS NULL")
    suspend fun getActiveForField(recordKey: String, field: String): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun getById(id: String): HighlightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HighlightEntity)

    @Query("UPDATE highlights SET deletedUtc = :nowIso, updatedUtc = :nowIso WHERE id = :id")
    suspend fun tombstone(id: String, nowIso: String)

    @Query("SELECT * FROM highlights WHERE :sinceUtc IS NULL OR updatedUtc > :sinceUtc")
    suspend fun getChangedSince(sinceUtc: String?): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE deletedUtc IS NULL AND selectedText LIKE '%' || :query || '%'")
    suspend fun searchSelectedText(query: String): List<HighlightEntity>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedUtc IS NULL ORDER BY updatedUtc DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE recordKey = :recordKey AND deletedUtc IS NULL ORDER BY updatedUtc DESC")
    fun observeForRecord(recordKey: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE recordKey IS NULL AND deletedUtc IS NULL ORDER BY updatedUtc DESC")
    fun observeGeneral(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NoteEntity)

    @Query("UPDATE notes SET deletedUtc = :nowIso, updatedUtc = :nowIso WHERE id = :id")
    suspend fun tombstone(id: String, nowIso: String)

    @Query("SELECT * FROM notes WHERE :sinceUtc IS NULL OR updatedUtc > :sinceUtc")
    suspend fun getChangedSince(sinceUtc: String?): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedUtc IS NULL AND content LIKE '%' || :query || '%'")
    suspend fun searchContent(query: String): List<NoteEntity>
}

@Dao
interface ReadingHistoryDao {
    @Query("SELECT * FROM reading_history WHERE recordKey = :recordKey")
    suspend fun get(recordKey: String): ReadingHistoryEntity?

    @Query("SELECT * FROM reading_history ORDER BY lastOpenedUtc DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<ReadingHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history")
    suspend fun clearAll()
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    suspend fun get(key: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncMetadataEntity)
}
