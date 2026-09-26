package com.prabhupadaconnect.vedabase.data.user.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema mirrors Migration 11 of the desktop WinUI app's `user.db`
 * 1:1 (see UserDataModels.cs) - same column names/types, same soft-delete
 * (DeletedUtc tombstone) convention, same "at most one active bookmark per
 * verse" invariant, so a Supabase-synced device pair (desktop + Android)
 * never disagrees about what a row means.
 */
@Entity(
    tableName = "bookmark_collections"
)
data class BookmarkCollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val createdUtc: String,
    val updatedUtc: String,
    val deletedUtc: String?
)

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["recordKey"]), Index(value = ["collectionId"])]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val recordKey: String,
    val collectionId: String?,
    val title: String?,
    val createdUtc: String,
    val updatedUtc: String,
    val deletedUtc: String?
)

@Entity(
    tableName = "highlights",
    indices = [Index(value = ["recordKey"])]
)
data class HighlightEntity(
    @PrimaryKey val id: String,
    val recordKey: String,
    val field: String,
    val color: String,
    val startOffset: Int,
    val length: Int,
    val selectedText: String?,
    val createdUtc: String,
    val updatedUtc: String,
    val deletedUtc: String?
)

@Entity(
    tableName = "notes",
    indices = [Index(value = ["recordKey"])]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val recordKey: String?,
    val title: String?,
    val content: String,
    val field: String?,
    val startOffset: Int?,
    val length: Int?,
    val createdUtc: String,
    val updatedUtc: String,
    val deletedUtc: String?
)

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey val recordKey: String,
    val lastOpenedUtc: String,
    val openCount: Int
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedUtc: String
)
