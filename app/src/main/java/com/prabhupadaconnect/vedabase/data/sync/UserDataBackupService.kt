package com.prabhupadaconnect.vedabase.data.sync

import android.content.Context
import com.prabhupadaconnect.vedabase.data.user.UserDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Creates a local safety-snapshot copy of `user.db` before a sync merge
 * mutates it, mirroring the desktop app's `ResearchDataBackupService`. A
 * plain file copy (SQLite's WAL is checkpointed first via a passive
 * checkpoint) - never a transform, so restoring it is just copying it back.
 */
@Singleton
class UserDataBackupService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun createLocalSnapshot(): String = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(UserDatabase.DATABASE_NAME)
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val snapshotFile = File(backupDir, "user_${timestampFormat.format(Date())}.db.bak")

        if (dbFile.exists()) {
            // Passive WAL checkpoint so the snapshot reflects everything
            // committed so far, without blocking concurrent readers.
            runCatching {
                android.database.sqlite.SQLiteDatabase
                    .openDatabase(dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
                    .use { it.rawQuery("PRAGMA wal_checkpoint(PASSIVE)", null).use { c -> c.moveToFirst() } }
            }
            dbFile.copyTo(snapshotFile, overwrite = true)
        }

        snapshotFile.path
    }
}
