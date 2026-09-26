package com.prabhupadaconnect.vedabase.data.corpus

import android.content.Context
import io.requery.android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Installs the pre-packaged, read-only canonical corpus database from
 * `assets/prabhupada_corpus.db` into the app's private storage on first
 * launch. SQLite cannot open a database file living inside a compressed
 * APK/AAB directly, so this performs a one-time byte-for-byte copy (never a
 * transform, never a rebuild) into `filesDir/databases/`.
 *
 * The corpus is versioned by [CORPUS_ASSET_VERSION]: bumping it forces a
 * fresh copy on the next launch (used when the bundled corpus asset itself
 * is replaced by a newer canonical build), while every ordinary launch is a
 * cheap existence + version check.
 */
object CorpusDatabaseAssetInstaller {

    const val CORPUS_ASSET_NAME = "prabhupada_corpus.db"
    private const val CORPUS_ASSET_VERSION = 1
    private const val VERSION_MARKER_NAME = "prabhupada_corpus.version"

    fun corpusDbFile(context: Context): File =
        File(context.getDatabasePath(CORPUS_ASSET_NAME).path)

    suspend fun ensureInstalled(context: Context): File = withContext(Dispatchers.IO) {
        val dbFile = corpusDbFile(context)
        val versionFile = File(dbFile.parentFile, VERSION_MARKER_NAME)
        val installedVersion = versionFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()

        if (dbFile.exists() && installedVersion == CORPUS_ASSET_VERSION) {
            return@withContext dbFile
        }

        dbFile.parentFile?.mkdirs()
        val tmpFile = File(dbFile.parentFile, "$CORPUS_ASSET_NAME.tmp")

        context.assets.open(CORPUS_ASSET_NAME).use { input ->
            tmpFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 1 shl 20)
            }
        }

        if (dbFile.exists()) dbFile.delete()
        tmpFile.renameTo(dbFile)
        versionFile.writeText(CORPUS_ASSET_VERSION.toString())

        dbFile
    }

    /** Opens the installed corpus read-only, with the same mmap/cache pragmas as the desktop app. */
    fun openReadOnly(dbFile: File): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        // Pure runtime perf tuning (never persisted, never touches the file
        // itself) - safe to ignore if the platform's SQLite build rejects a
        // pragma on a read-only connection.
        runCatching { db.rawQuery("PRAGMA mmap_size = 268435456", null).use { it.moveToFirst() } }
        runCatching { db.rawQuery("PRAGMA cache_size = -64000", null).use { it.moveToFirst() } }
        return db
    }
}
