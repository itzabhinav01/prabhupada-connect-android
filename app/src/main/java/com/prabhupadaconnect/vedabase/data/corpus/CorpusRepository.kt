package com.prabhupadaconnect.vedabase.data.corpus

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import io.requery.android.database.sqlite.SQLiteDatabase
import com.prabhupadaconnect.vedabase.core.model.BookNode
import com.prabhupadaconnect.vedabase.core.model.ChapterNode
import com.prabhupadaconnect.vedabase.core.model.CorpusRecord
import com.prabhupadaconnect.vedabase.core.model.RecordNode
import com.prabhupadaconnect.vedabase.core.model.SearchResult
import com.prabhupadaconnect.vedabase.core.model.VocabTerm
import com.prabhupadaconnect.vedabase.core.registry.BookRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SearchOutcome(val results: List<SearchResult>, val totalCount: Int)

private const val RECORD_COLUMNS =
    "RecordKey, BookKey, Sequence, ParentKey, RecordType, Reference, ReferenceStatus, Title, " +
        "Devanagari, Transliteration, Synonyms, Translation, Purports"

/**
 * Read-only gateway onto the frozen canonical corpus (~50k+ records).
 * Ported 1:1 from the desktop app's `SqliteCorpusRepository` (C#) - same
 * canonical-edition duplicate filter, same FTS5 BM25 ranking/ordering
 * pipeline, same chapter-title derivation, so search and Library results
 * match the Windows client exactly against the same corpus file.
 */
@Singleton
class CorpusRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dbLock = Mutex()
    private var database: SQLiteDatabase? = null
    private var ftsColumnCount: Int? = null
    private var cachedHierarchy: List<BookNode>? = null

    private suspend fun db(): SQLiteDatabase = dbLock.withLock {
        database ?: run {
            val file = CorpusDatabaseAssetInstaller.ensureInstalled(context)
            CorpusDatabaseAssetInstaller.openReadOnly(file).also { database = it }
        }
    }

    private suspend fun invalidateDatabase() {
        dbLock.withLock {
            runCatching { database?.close() }
            database = null
        }
    }

    /**
     * A cached connection can go bad out from under a caller that already
     * holds a reference to it - most concretely, requery's SQLite closes the
     * whole connection pool and deletes the file the moment it reports
     * `SQLITE_CORRUPT` on any query, so every query issued against that
     * now-closed pool afterward throws `IllegalStateException`, not a
     * graceful "no rows" result. Rather than crash the app over what is, in
     * practice, a recoverable condition (the corpus is a frozen, read-only
     * asset we can always recopy from the APK), this closes out the stale
     * connection, forces a fresh copy from assets, and retries the caller's
     * operation exactly once - a second failure is a genuine, unrecoverable
     * error and is allowed to propagate.
     */
    private suspend fun <T> withRecovery(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            if (e is IllegalStateException || e is SQLiteException) {
                invalidateDatabase()
                CorpusDatabaseAssetInstaller.ensureInstalled(context, forceReinstall = true)
                block()
            } else {
                throw e
            }
        }
    }

    // The scriptural reading order of works - single source of truth shared
    // with BookRegistry.canonicalBookOrder.
    private val canonicalBookOrder: List<String> get() = BookRegistry.canonicalBookOrder

    /**
     * 42.5% of the corpus's records are duplicate-content occurrences (the
     * same verse/section appearing a second/third time from a later
     * reprint/lecture-series section). The extraction pipeline marks every
     * such duplicate with a '#N' RecordKey suffix and always keeps the
     * FIRST/primary occurrence's key bare. This filters both that and a
     * small residual of un-suffixed reprint-year duplicates out of Library/
     * Search/adjacent-navigation surfaces without deleting anything.
     */
    private fun excludeDuplicateContentSql(alias: String = ""): String {
        val p = if (alias.isEmpty()) "" else "$alias."
        return "${p}RecordKey NOT LIKE '%#%' AND NOT (${p}BookKey IN ('BG','SB','DI','MADHYA','ANTYA','BS') " +
            "AND (${p}Reference GLOB '*, 19[6-8][0-9]' OR ${p}Reference GLOB '*-19[6-8][0-9]'))"
    }

    private fun Cursor.stringOrNull(col: Int): String? = if (isNull(col)) null else getString(col)
    private fun Cursor.stringOrEmpty(col: Int): String = if (isNull(col)) "" else getString(col)

    private fun readRecord(c: Cursor): CorpusRecord = CorpusRecord(
        recordKey = c.getString(0),
        bookKey = c.getString(1),
        sequence = c.getInt(2),
        parentKey = c.stringOrNull(3),
        recordType = c.stringOrEmpty(4),
        reference = c.stringOrNull(5),
        referenceStatus = c.stringOrEmpty(6),
        title = c.stringOrNull(7),
        rawDevanagari = c.stringOrEmpty(8),
        transliteration = c.stringOrEmpty(9),
        synonyms = c.stringOrEmpty(10),
        translation = c.stringOrEmpty(11),
        purports = c.stringOrEmpty(12)
    )

    suspend fun getRecord(recordKey: String): CorpusRecord? = withRecovery { withContext(Dispatchers.IO) {
        db().rawQuery("SELECT $RECORD_COLUMNS FROM Records WHERE RecordKey = ?", arrayOf(recordKey)).use { c ->
            if (c.moveToFirst()) readRecord(c) else null
        }
    } }

    suspend fun getRecords(recordKeys: Collection<String>): List<CorpusRecord> = withRecovery { withContext(Dispatchers.IO) {
        val keys = recordKeys.distinct()
        if (keys.isEmpty()) return@withContext emptyList()

        val placeholders = keys.joinToString(",") { "?" }
        db().rawQuery(
            "SELECT $RECORD_COLUMNS FROM Records WHERE RecordKey IN ($placeholders)",
            keys.toTypedArray()
        ).use { c ->
            val out = ArrayList<CorpusRecord>(keys.size)
            while (c.moveToNext()) out.add(readRecord(c))
            out
        }
    } }

    suspend fun getAdjacentRecordKey(currentRecordKey: String, next: Boolean): String? = withRecovery { withContext(Dispatchers.IO) {
        val database = db()
        var bookKey = ""
        var seq = -1
        database.rawQuery("SELECT BookKey, Sequence FROM Records WHERE RecordKey = ?", arrayOf(currentRecordKey)).use { c ->
            if (c.moveToFirst()) {
                bookKey = c.getString(0)
                seq = c.getInt(1)
            }
        }
        if (seq == -1) return@withContext null

        val comparator = if (next) ">" else "<"
        val order = if (next) "ASC" else "DESC"
        val sql = "SELECT RecordKey FROM Records WHERE BookKey = ? AND Sequence $comparator ? " +
            "AND ${excludeDuplicateContentSql()} ORDER BY Sequence $order LIMIT 1"
        database.rawQuery(sql, arrayOf(bookKey, seq.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } }

    // ------------------------------------------------------------------
    // Library hierarchy
    // ------------------------------------------------------------------

    fun invalidateLibraryHierarchyCache() {
        cachedHierarchy = null
    }

    suspend fun getLibraryHierarchy(): List<BookNode> {
        cachedHierarchy?.let { return it }
        val books = withRecovery { loadLibraryHierarchy() }
        cachedHierarchy = books
        return books
    }

    private suspend fun loadLibraryHierarchy(): List<BookNode> {
        val database = db()
        return withContext(Dispatchers.IO) {
            val dbTitles = HashMap<String, String>()
            val dbOrder = HashMap<String, Int>()
            val dbAuthors = HashMap<String, String>()
            val dbCategories = HashMap<String, String>()

            runCatching {
                database.rawQuery("SELECT BookKey, Title, CanonicalOrder, Author, Category FROM Books", null).use { c ->
                    while (c.moveToNext()) {
                        val bk = c.getString(0)
                        if (!c.isNull(1)) c.getString(1)?.takeIf { it.isNotBlank() }?.let { dbTitles[bk] = it }
                        if (!c.isNull(2)) dbOrder[bk] = c.getInt(2)
                        if (!c.isNull(3)) c.getString(3)?.takeIf { it.isNotBlank() }?.let { dbAuthors[bk] = it }
                        if (!c.isNull(4)) c.getString(4)?.takeIf { it.isNotBlank() }?.let { dbCategories[bk] = it }
                    }
                }
            }

            val bookKeys = mutableListOf<String>()
            database.rawQuery("SELECT DISTINCT BookKey FROM Records", null).use { c ->
                while (c.moveToNext()) bookKeys.add(c.getString(0))
            }

            bookKeys.sortWith(Comparator { a, b ->
                if (a == "UNKNOWN" && b == "UNKNOWN") return@Comparator 0
                if (a == "UNKNOWN") return@Comparator 1
                if (b == "UNKNOWN") return@Comparator -1

                val indexA = dbOrder[a] ?: canonicalBookOrder.indexOf(a)
                val indexB = dbOrder[b] ?: canonicalBookOrder.indexOf(b)

                if (indexA != -1 && indexB != -1) return@Comparator indexA.compareTo(indexB)
                if (indexA != -1) return@Comparator -1
                if (indexB != -1) return@Comparator 1
                a.compareTo(b)
            })

            val bookMap = LinkedHashMap<String, BookNode>()
            val result = mutableListOf<BookNode>()
            for (key in bookKeys) {
                val node = BookNode(
                    bookKey = key,
                    title = dbTitles[key] ?: getBookTitle(key),
                    author = dbAuthors[key] ?: "His Divine Grace A.C. Bhaktivedanta Swami Prabhupāda",
                    category = dbCategories[key] ?: ""
                )
                result.add(node)
                bookMap[key] = node
            }

            val chapterMaps = HashMap<String, LinkedHashMap<String, ChapterNode>>()
            database.rawQuery(
                "SELECT BookKey, RecordKey, Reference, Title, Sequence FROM Records " +
                    "WHERE ${excludeDuplicateContentSql()} ORDER BY BookKey, Sequence",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val bk = c.getString(0)
                    val bookNode = bookMap[bk] ?: continue

                    val rk = c.getString(1)
                    if (bk == "SPS" && rk.startsWith("SPS-SEC-", ignoreCase = true)) continue

                    val refText = c.stringOrEmpty(2)
                    val recordTitle = c.stringOrNull(3)
                    val seq = c.getInt(4)

                    val chapterTitle = ChapterTitleDeriver.derive(bk, refText, recordTitle)

                    val chapterMap = chapterMaps.getOrPut(bk) { LinkedHashMap() }
                    val chNode = chapterMap.getOrPut(chapterTitle) {
                        ChapterNode(chapterTitle).also { bookNode.chapters.add(it) }
                    }

                    var displayRef = refText.ifBlank { rk }
                    if (bk == "SPS" && !recordTitle.isNullOrBlank()) {
                        displayRef = "$displayRef: $recordTitle"
                    } else if ((bk == "SVA" || bk == "TMG" || bk == "BTG") && !recordTitle.isNullOrBlank()) {
                        displayRef = "$displayRef — $recordTitle"
                    }

                    chNode.records.add(RecordNode(recordKey = rk, reference = displayRef, sequence = seq))
                }
            }

            result
        }
    }

    suspend fun getChapterRecords(recordKey: String): List<CorpusRecord> {
        val target = getRecord(recordKey) ?: return emptyList()
        val chapterTitle = ChapterTitleDeriver.derive(target.bookKey, target.reference ?: "", null)

        val hierarchy = getLibraryHierarchy()
        val book = hierarchy.firstOrNull { it.bookKey == target.bookKey } ?: return listOf(target)
        val chapter = book.chapters.firstOrNull { it.title == chapterTitle } ?: return listOf(target)

        val recordKeys = chapter.records.map { it.recordKey }
        val records = getRecords(recordKeys)
        val keyToIndex = recordKeys.withIndex().associate { (i, k) -> k to i }
        return records.sortedBy { keyToIndex[it.recordKey] ?: it.sequence }
    }

    fun getBookTitle(bookKey: String): String = BookRegistry.getBookTitle(bookKey)

    fun getCanonicalChapterHeader(bookKey: String, reference: String?): String {
        val bookTitle = getBookTitle(bookKey)
        val chapterTitle = ChapterTitleDeriver.derive(bookKey, reference ?: "", null)
        return "$bookTitle — $chapterTitle"
    }

    // ------------------------------------------------------------------
    // Vocabulary (type-ahead)
    // ------------------------------------------------------------------

    suspend fun getVocabularyTerms(prefix: String, limit: Int = 60): List<VocabTerm> = withRecovery { withContext(Dispatchers.IO) {
        val database = db()
        val cleanPrefix = prefix.trim().lowercase()
        val list = mutableListOf<VocabTerm>()

        runCatching {
            database.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS RecordsFts_vocab USING fts5vocab('RecordsFts', 'row')")
        }

        fun readVocab(c: Cursor, into: MutableList<VocabTerm>) {
            while (c.moveToNext()) into.add(VocabTerm(c.getString(0), c.getInt(1), c.getInt(2)))
        }

        if (cleanPrefix.isEmpty()) {
            database.rawQuery(
                "SELECT term, doc, cnt FROM RecordsFts_vocab ORDER BY term LIMIT ?",
                arrayOf(limit.toString())
            ).use { readVocab(it, list) }
        } else {
            val preceding = mutableListOf<VocabTerm>()
            database.rawQuery(
                "SELECT term, doc, cnt FROM RecordsFts_vocab WHERE term < ? ORDER BY term DESC LIMIT 5",
                arrayOf(cleanPrefix)
            ).use { readVocab(it, preceding) }
            preceding.reverse()
            list.addAll(preceding)

            database.rawQuery(
                "SELECT term, doc, cnt FROM RecordsFts_vocab WHERE term >= ? ORDER BY term ASC LIMIT ?",
                arrayOf(cleanPrefix, maxOf(20, limit - preceding.size).toString())
            ).use { readVocab(it, list) }
        }

        list
    } }

    // ------------------------------------------------------------------
    // Full-text search (FTS5) - see FtsQueryParser / IastSearchHelper for
    // the query-sanitization and diacritic-tolerant matching passes this
    // builds on.
    //
    // Note on SearchResult.preview: the desktop app's equivalent query calls
    // FTS5's `snippet()` auxiliary function to produce a highlighted excerpt.
    // On-device testing found that requery:sqlite-android's bundled SQLite
    // reports SQLITE_CORRUPT_VTAB (267) - and then deletes the corpus file -
    // the moment `snippet()` is evaluated against this corpus's external-
    // content FTS5 table (`content='Records', content_rowid='rowid'`),
    // reproducibly, regardless of query shape. `PRAGMA integrity_check` and
    // an FTS5-capable byte-identical copy both confirm the file itself is
    // fine - this is a genuine incompatibility between this SQLite build and
    // `snippet()` on this table shape, not corruption. Search itself
    // (matching, ranking, and opening the right verse) never depended on it,
    // so `preview` is instead built by [SnippetGenerator] - a pure-Kotlin
    // pass over just the returned rows' Translation/Purports text (never the
    // full 50k+-record corpus), with no FTS5 involvement at all.
    // ------------------------------------------------------------------

    private fun getFtsColumnCount(database: SQLiteDatabase): Int {
        ftsColumnCount?.let { return it }
        var count = 0
        database.rawQuery("PRAGMA table_info(RecordsFts)", null).use { c ->
            while (c.moveToNext()) count++
        }
        ftsColumnCount = count
        return count
    }

    suspend fun search(
        query: String,
        bookKey: String? = null,
        limit: Int = 50,
        offset: Int = 0,
        fieldScope: String? = null,
        bookKeys: List<String>? = null,
        isExactWord: Boolean = false,
        sortOrder: String = "relevance",
        isExactCase: Boolean = false
    ): SearchOutcome = withRecovery { withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchOutcome(emptyList(), 0)

        var ftsQuery = FtsQueryParser.parse(query, isExactWord)
        if (ftsQuery.isBlank()) return@withContext SearchOutcome(emptyList(), 0)

        if (!fieldScope.isNullOrBlank() && !fieldScope.equals("All", true) && !fieldScope.equals("All Fields", true)) {
            val targetCol = when (fieldScope.trim().lowercase()) {
                "purports", "purports only", "purport" -> "Purports"
                "translation", "translations", "translations only" -> "Translation"
                "synonyms", "synonym", "synonyms only" -> "Synonyms"
                "devanagari", "devanagari only" -> "Devanagari"
                "verse / transliteration only", "transliteration only", "transliteration" -> "Transliteration"
                "verse & synonyms" -> "{Transliteration Synonyms}"
                else -> fieldScope.trim()
            }
            ftsQuery = "$targetCol : ($ftsQuery)"
        }

        val expandedMultiKeys = mutableListOf<String>()
        bookKeys?.filter { it.isNotBlank() }?.distinct()?.forEach { bk ->
            if (bk == "CC") expandedMultiKeys.addAll(listOf("DI", "MADHYA", "ANTYA")) else expandedMultiKeys.add(bk)
        }

        val database = db()
        var totalCount = 0
        val results = mutableListOf<SearchResult>()

        try {
            val colCount = getFtsColumnCount(database)
            val rankExpression = when {
                colCount >= 8 -> "bm25(RecordsFts, 25.0, 25.0, 5.0, 4.0, 15.0, 10.0, 5.0, 1.0)"
                colCount == 6 -> "bm25(RecordsFts, 5.0, 4.0, 4.0, 2.0, 5.0, 1.0)"
                else -> "rank"
            }

            // -------------------- COUNT --------------------
            val countArgs = mutableListOf<String>()
            val countSql = StringBuilder(
                "SELECT COUNT(*) FROM RecordsFts fts JOIN Records r ON r.rowid = fts.rowid " +
                    "WHERE RecordsFts MATCH ? AND ${excludeDuplicateContentSql("r")}"
            )
            countArgs.add(ftsQuery)

            when {
                bookKeys != null && bookKeys.isEmpty() -> countSql.append(" AND 1=0")
                expandedMultiKeys.isNotEmpty() -> {
                    countSql.append(" AND r.BookKey IN (${expandedMultiKeys.joinToString(",") { "?" }})")
                    countArgs.addAll(expandedMultiKeys)
                }
                !bookKey.isNullOrEmpty() -> {
                    if (bookKey == "CC") {
                        countSql.append(" AND r.BookKey IN ('DI', 'MADHYA', 'ANTYA')")
                    } else {
                        countSql.append(" AND r.BookKey = ?")
                        countArgs.add(bookKey)
                    }
                }
            }

            if (isExactCase) {
                val (clause, args) = IastSearchHelper.buildCaseGlobSqlClause(query)
                countSql.append(clause)
                countArgs.addAll(args)
            }

            database.rawQuery(countSql.toString(), countArgs.toTypedArray()).use { c ->
                totalCount = if (c.moveToFirst()) c.getInt(0) else 0
            }

            if (totalCount == 0) return@withContext SearchOutcome(emptyList(), 0)

            // -------------------- FETCH --------------------
            val leadWordRaw = query.trim().split(Regex("[\\s\\t\\r\\n]+")).firstOrNull() ?: query.trim()
            val leadWord = leadWordRaw.replace(Regex("[^\\p{L}\\p{N}\\-]"), "")
            val cleanQuery = query.trim()

            val args = mutableListOf<String>()
            val sql = StringBuilder()
            sql.append(
                "SELECT r.RecordKey, r.BookKey, r.Reference, r.Sequence, " +
                    "(CASE " +
                    "WHEN r.Reference = ? OR r.RecordKey = ? THEN 1 "
            )
            args.add(cleanQuery); args.add(cleanQuery)
            sql.append("WHEN r.Reference LIKE ? || '%' OR r.Reference LIKE '% ' || ? || '%' THEN 2 ")
            args.add(cleanQuery); args.add(cleanQuery)
            sql.append("WHEN r.RecordKey LIKE '%' || ? || '%' THEN 3 ")
            args.add(cleanQuery)
            sql.append(
                "ELSE 4 END) AS ExactCitationPriority, r.Translation, r.Purports " +
                    "FROM RecordsFts fts JOIN Records r ON r.rowid = fts.rowid " +
                    "LEFT JOIN Books b ON b.BookKey = r.BookKey " +
                    "WHERE RecordsFts MATCH ? AND ${excludeDuplicateContentSql("r")} "
            )
            args.add(ftsQuery)

            when {
                bookKeys != null && bookKeys.isEmpty() -> sql.append(" AND 1=0 ")
                expandedMultiKeys.isNotEmpty() -> {
                    sql.append(" AND r.BookKey IN (${expandedMultiKeys.joinToString(",") { "?" }}) ")
                    args.addAll(expandedMultiKeys)
                }
                !bookKey.isNullOrEmpty() -> {
                    if (bookKey == "CC") {
                        sql.append(" AND r.BookKey IN ('DI', 'MADHYA', 'ANTYA') ")
                    } else {
                        sql.append(" AND r.BookKey = ? ")
                        args.add(bookKey)
                    }
                }
            }

            if (isExactCase) {
                val (clause, caseArgs) = IastSearchHelper.buildCaseGlobSqlClause(query)
                sql.append(clause)
                args.addAll(caseArgs)
            }

            val orderExpression = if (sortOrder.equals("canonical", true)) {
                "ExactCitationPriority, COALESCE(b.CanonicalOrder, 9999), r.Sequence"
            } else {
                val expr = StringBuilder("(CASE ")
                expr.append("WHEN r.Reference = ? OR r.RecordKey = ? THEN -2500.0 ")
                args.add(cleanQuery); args.add(cleanQuery)
                expr.append("WHEN r.Reference LIKE ? || '%' OR r.Reference LIKE '% ' || ? || '%' THEN -2000.0 ")
                args.add(cleanQuery); args.add(cleanQuery)
                expr.append("WHEN r.Reference LIKE '%' || ? || '%' THEN -1000.0 ")
                args.add(cleanQuery)
                expr.append(
                    "WHEN (r.Transliteration LIKE ? || ' %' OR r.Transliteration LIKE ? || CHAR(10) || '%') " +
                        "AND (r.Synonyms LIKE ? || '—%' OR r.Synonyms LIKE ? || '-%') THEN -600.0 "
                )
                args.add(leadWord); args.add(leadWord); args.add(leadWord); args.add(leadWord)
                expr.append("WHEN (r.Transliteration LIKE ? || ' %' OR r.Transliteration LIKE ? || CHAR(10) || '%') THEN -400.0 ")
                args.add(leadWord); args.add(leadWord)
                expr.append("WHEN (r.Synonyms LIKE ? || '—%' OR r.Synonyms LIKE ? || '-%') THEN -200.0 ")
                args.add(leadWord); args.add(leadWord)
                expr.append(
                    "ELSE 0.0 END " +
                        "+ CASE WHEN length(r.Purports) > 100 THEN -20.0 ELSE 0.0 END " +
                        "+ $rankExpression)"
                )
                expr.toString()
            }

            sql.append(" ORDER BY $orderExpression LIMIT ? OFFSET ?")
            args.add(limit.toString())
            args.add(offset.toString())

            val cleanQueryUpper = cleanQuery.replace(Regex("[^\\w]"), "").uppercase()

            database.rawQuery(sql.toString(), args.toTypedArray()).use { c ->
                while (c.moveToNext()) {
                    val rk = c.getString(0)
                    val bk = c.stringOrEmpty(1)
                    val refText = c.stringOrEmpty(2)
                    val seq = c.getInt(3)
                    val exactPriority = if (c.isNull(4)) 4 else c.getInt(4)
                    val translation = c.stringOrEmpty(5)
                    val purports = c.stringOrEmpty(6)
                    val preview = SnippetGenerator.generate(listOf(translation, purports), query)

                    val title = BookRegistry.getBookTitle(bk)
                    var isExact = exactPriority <= 2

                    if (!isExact && cleanQueryUpper.length >= 2) {
                        val cr = refText.replace(Regex("[^\\w]"), "").uppercase()
                        val ck = rk.replace(Regex("[^\\w]"), "").uppercase()
                        if (cr == cleanQueryUpper || ck == cleanQueryUpper ||
                            cr.endsWith(cleanQueryUpper) || ck.endsWith(cleanQueryUpper) ||
                            refText.equals(cleanQuery, true) || rk.equals(cleanQuery, true) ||
                            (bookKey != null && (bookKey.uppercase() + cleanQueryUpper == cr || bookKey.uppercase() + cleanQueryUpper == ck)) ||
                            (bk.isNotEmpty() && (bk.uppercase() + cleanQueryUpper == cr || bk.uppercase() + cleanQueryUpper == ck))
                        ) {
                            isExact = true
                        }
                    }

                    results.add(
                        SearchResult(
                            recordKey = rk,
                            bookKey = bk,
                            reference = refText.ifBlank { bk },
                            bookTitle = title,
                            preview = preview,
                            category = "Scripture",
                            sequence = seq,
                            isExactMatch = isExact
                        )
                    )
                }
            }
        } catch (_: SQLiteException) {
            // Syntax error in FTS5 query or schema mismatch: safe fallback to 0 results.
            return@withContext SearchOutcome(emptyList(), 0)
        }

        SearchOutcome(results, totalCount)
    } }
}
