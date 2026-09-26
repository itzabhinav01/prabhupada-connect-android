package com.prabhupadaconnect.vedabase.data.user

import androidx.room.Database
import androidx.room.RoomDatabase
import com.prabhupadaconnect.vedabase.data.user.dao.BookmarkCollectionDao
import com.prabhupadaconnect.vedabase.data.user.dao.BookmarkDao
import com.prabhupadaconnect.vedabase.data.user.dao.HighlightDao
import com.prabhupadaconnect.vedabase.data.user.dao.NoteDao
import com.prabhupadaconnect.vedabase.data.user.dao.ReadingHistoryDao
import com.prabhupadaconnect.vedabase.data.user.dao.SyncMetadataDao
import com.prabhupadaconnect.vedabase.data.user.entity.BookmarkCollectionEntity
import com.prabhupadaconnect.vedabase.data.user.entity.BookmarkEntity
import com.prabhupadaconnect.vedabase.data.user.entity.HighlightEntity
import com.prabhupadaconnect.vedabase.data.user.entity.NoteEntity
import com.prabhupadaconnect.vedabase.data.user.entity.ReadingHistoryEntity
import com.prabhupadaconnect.vedabase.data.user.entity.SyncMetadataEntity

/**
 * The user's own read/write research database ("user.db" on the desktop
 * app) - bookmarks, collections, highlights, notes, reading history, and
 * sync bookkeeping. Entirely separate from the frozen, read-only corpus
 * database (see [com.prabhupadaconnect.vedabase.data.corpus.CorpusRepository]).
 */
@Database(
    entities = [
        BookmarkCollectionEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        ReadingHistoryEntity::class,
        SyncMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun bookmarkCollectionDao(): BookmarkCollectionDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        const val DATABASE_NAME = "user.db"
    }
}
