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
 * Pure retention-decision logic for [UserDataBackupService]'s snapshots,
 * factored out so the "which files survive" rule is unit-testable without
 * touching real files. Snapshot filenames encode their creation instant as
 * `yyyyMMdd_HHmmss` (see [UserDataBackupService.timestampFormat]), a
 * zero-padded format whose lexicographic order matches chronological order,
 * so a plain name sort is sufficient without parsing timestamps back out.
 */
object BackupRetention {
    /** Of [snapshots], the ones beyond the newest [keep] that should be deleted. */
    fun filesToDelete(snapshots: List<File>, keep: Int): List<File> =
        if (snapshots.size <= keep) emptyList() else snapshots.sortedByDescending { it.name }.drop(keep)
}

/**
 * Creates a local safety-snapshot copy of `user.db` before a sync merge
 * mutates it, mirroring the desktop app's `ResearchDataBackupService`. A
 * plain file copy (SQLite's WAL is checkpointed first via a passive
 * checkpoint) - never a transform, so restoring it is just copying it back.
 *
 * Only the newest [MAX_SNAPSHOTS] are kept - pruning is best-effort and
 * never allowed to fail the snapshot itself, since the whole point of this
 * service is to make sync strictly safer, never a new way for it to break.
 */
@Singleton
class UserDataBackupService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private fun backupDir(): File = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun createLocalSnapshot(): String = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(UserDatabase.DATABASE_NAME)
        val backupDir = backupDir()
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
            runCatching { pruneOldSnapshots(backupDir) }
        }

        snapshotFile.path
    }

    private fun pruneOldSnapshots(backupDir: File) {
        val snapshots = backupDir.listFiles { f -> f.isFile && f.name.startsWith("user_") && f.name.endsWith(".db.bak") }
            ?.toList() ?: return
        BackupRetention.filesToDelete(snapshots, MAX_SNAPSHOTS).forEach { it.delete() }
    }

    companion object {
        private const val MAX_SNAPSHOTS = 5
    }
}
