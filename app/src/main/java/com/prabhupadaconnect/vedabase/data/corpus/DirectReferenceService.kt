package com.prabhupadaconnect.vedabase.data.corpus

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One work's grammar definition for "@" direct-reference navigation. Adding
 * a new work means adding one entry here - no parser rewrite.
 */
data class ReferenceWorkDefinition(
    val bookKey: String,
    val displayTitle: String,
    /** Alias tokens a user might type; the BookKey itself is always implicitly included. */
    val aliases: Set<String>,
    /** How many dot-separated numeric parts this work's verse references have - 2 for "Bg 1.1", 3 for "SB 1.4.6". */
    val numericLevels: Int,
    /** Human labels for each numeric level, outermost first (e.g. ["Canto","Chapter","Verse"] for SB). */
    val levelNames: List<String>
)

/** A single navigable node in the reference index - either an intermediate level (a chapter, a canto) or a leaf verse. */
data class ReferenceSuggestion(
    val displayText: String,
    val subText: String = "",
    /** Set only for an exact, navigable leaf verse. */
    val recordKey: String? = null,
    /** What tapping/completing this suggestion fills the query box with, e.g. "@BG 1" or "@BG 1.1". */
    val queryToComplete: String,
    val isWork: Boolean = false
)

/**
 * Parses "@..." direct-reference queries and resolves them against an
 * in-memory index built once from the corpus hierarchy (never re-queries
 * SQLite per keystroke). Ported 1:1 from the desktop app's
 * `DirectReferenceService` (C#), adapted to this corpus's actual book set
 * (no HKC/HKH - not present here; adds BROKENNAMES/JAPA - present here but
 * not in the desktop corpus this was ported from) and to
 * [com.prabhupadaconnect.vedabase.core.model.BookNode]'s Canto/līlā grouping.
 */
@Singleton
class DirectReferenceService @Inject constructor(
    private val corpusRepository: CorpusRepository
) {
    companion object {
        private val WORKS: List<ReferenceWorkDefinition> = listOf(
            ReferenceWorkDefinition("BG", "Bhagavad-gītā As It Is", setOf("BG", "BHAGAVADGITA", "GITA"), 2, listOf("Chapter", "Verse")),
            ReferenceWorkDefinition("SB", "Śrīmad-Bhāgavatam", setOf("SB", "BHAGAVATAM", "SRIMADBHAGAVATAM"), 3, listOf("Canto", "Chapter", "Verse")),
            ReferenceWorkDefinition("DI", "Śrī Caitanya-caritāmṛta — Ādi-līlā", setOf("ADI", "CCADI", "ADILILA"), 2, listOf("Chapter", "Verse")),
            ReferenceWorkDefinition("MADHYA", "Śrī Caitanya-caritāmṛta — Madhya-līlā", setOf("MADHYA", "CCMADHYA", "MADHYALILA"), 2, listOf("Chapter", "Verse")),
            ReferenceWorkDefinition("ANTYA", "Śrī Caitanya-caritāmṛta — Antya-līlā", setOf("ANTYA", "CCANTYA", "ANTYALILA"), 2, listOf("Chapter", "Verse")),
            ReferenceWorkDefinition("ISO", "Śrī Īśopaniṣad", setOf("ISO", "ISOPANISAD"), 1, listOf("Mantra")),
            ReferenceWorkDefinition("NOI", "The Nectar of Instruction", setOf("NOI", "NECTAROFINSTRUCTION"), 1, listOf("Verse")),
            ReferenceWorkDefinition("BS", "Śrī Brahma-saṁhitā", setOf("BS", "BRAHMASAMHITA"), 2, listOf("Chapter", "Verse")),
            ReferenceWorkDefinition("TLK", "Teachings of Lord Kapila", setOf("TLK"), 1, listOf("Verse")),
            ReferenceWorkDefinition("MM", "Mukunda-mālā-stotra", setOf("MM"), 1, listOf("Verse")),
            ReferenceWorkDefinition("NBS", "Nārada-bhakti-sūtra", setOf("NBS"), 1, listOf("Sūtra")),
            ReferenceWorkDefinition("SSR", "The Science of Self-Realization", setOf("SSR"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("EJ", "Easy Journey to Other Planets", setOf("EJ"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("NOD", "The Nectar of Devotion", setOf("NOD", "NECTAROFDEVOTION"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("TLC", "Teachings of Lord Caitanya", setOf("TLC", "TEACHINGSOFLORDCAITANYA"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("KB", "Kṛṣṇa, the Supreme Personality of Godhead", setOf("KB", "KRSNA", "KRISHNA", "KRSNABOOK"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("TQK", "Teachings of Queen Kuntī", setOf("TQK", "QUEENKUNTI", "TEACHINGSOFQUEENKUNTI"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("BB", "Bṛhad-bhāgavatāmṛta", setOf("BB", "BRHADBHAGAVATAMRTA"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("DS", "Dialectical Spiritualism", setOf("DS", "DIALECTICALSPIRITUALISM"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("BBD", "Beyond Birth and Death", setOf("BBD", "BEYONDBIRTHANDDEATH"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("POY", "The Perfection of Yoga", setOf("POY", "PERFECTIONOFYOGA"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("RV", "Rāja-Vidyā: The King of Knowledge", setOf("RV", "RAJAVIDYA"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("EKC", "Elevation to Kṛṣṇa Consciousness", setOf("EKC", "ELEVATIONTOKRSNACONSCIOUSNESS"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("KCTYS", "Kṛṣṇa Consciousness: The Topmost Yoga System", setOf("KCTYS", "TOPMOSTYOGASYSTEM"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("MOG", "Message of Godhead", setOf("MOG", "MESSAGEOFGODHEAD"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("LOB", "Light of the Bhāgavata", setOf("LOB", "LIGHTOFTHEBHAGAVATA"), 1, listOf("Verse")),
            ReferenceWorkDefinition("PQPA", "Perfect Questions, Perfect Answers", setOf("PQPA", "PERFECTQUESTIONSPERFECTANSWERS"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("JSD", "The Journey of Self-Discovery", setOf("JSD", "JOURNEYOFSELFDISCOVERY"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("LCFL", "Life Comes From Life", setOf("LCFL", "LIFECOMESFROMLIFE"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("CB", "Coming Back: The Science of Reincarnation", setOf("CB", "COMINGBACK"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("CAT", "Civilization and Transcendence", setOf("CAT", "CIVILIZATIONANDTRANSCENDENCE"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("OWK", "On the Way to Kṛṣṇa", setOf("OWK", "ONTHEWAYTOKRSNA"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("SFL", "The Search for Liberation", setOf("SFL", "SEARCHFORLIBERATION"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("TT", "Transcendental Teachings of Prahlāda Mahārāja", setOf("TT"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("SC", "A Second Chance", setOf("SC", "SECONDCHANCE"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("DWT", "Dharma: The Way of Transcendence", setOf("DWT", "DHARMA"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("POP", "Path of Perfection", setOf("POP", "PATHOFPERFECTION"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("QFE", "Quest for Enlightenment", setOf("QFE", "QUESTFORENLIGHTENMENT"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("RTW", "Renunciation Through Wisdom", setOf("RTW", "RENUNCIATIONTHROUGHWISDOM"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("LON", "The Laws of Nature: An Infallible Justice", setOf("LON", "LAWSOFNATURE"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("MG", "Matchless Gift", setOf("MG", "MATCHLESSGIFT"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("ROP", "Reservoir of Pleasure", setOf("ROP", "RESERVOIROFPLEASURE"), 1, listOf("Chapter")),
            ReferenceWorkDefinition("GG", "Gītār Gāna", setOf("GG", "GITARGANA"), 1, listOf("Chapter"))
        )

        private val ALIAS_LOOKUP: Map<String, ReferenceWorkDefinition> = buildMap {
            for (work in WORKS) {
                put(work.bookKey.uppercase(), work)
                for (alias in work.aliases) put(alias.uppercase(), work)
            }
        }

        private val CC_LILA_TITLES = mapOf("DI" to "Ādi-līlā", "MADHYA" to "Madhya-līlā", "ANTYA" to "Antya-līlā")

        private val WORK_TEXT_PATTERN = Regex("^([A-Za-z]+(?:\\s+[A-Za-z]+)*)\\s*([\\d][\\d.\\s]*)?$")
        private val NON_ALPHA = Regex("[^A-Za-z]")

        /** True if the raw search-box text should be treated as direct-reference mode (starts with '@'). */
        fun isReferenceQuery(rawText: String): Boolean = rawText.trimStart().startsWith("@")

        /**
         * Parses the portion after '@' into (work, raw work text typed, numeric
         * parts typed so far). Tolerant of case, optional spaces, and missing
         * separators ("@bg 1.1", "@BG1.1", "@Bg 1.1" all parse identically).
         */
        fun parse(rawText: String): ParsedReference {
            var text = rawText.trimStart()
            if (text.startsWith("@")) text = text.substring(1)
            text = text.trim()

            if (text.isEmpty()) return ParsedReference(null, "", emptyList())

            val m = WORK_TEXT_PATTERN.find(text) ?: return ParsedReference(null, text, emptyList())
            val workText = m.groupValues[1]
            val numericText = m.groupValues[2]

            val normalized = NON_ALPHA.replace(workText, "").uppercase()
            var work = ALIAS_LOOKUP[normalized]

            // If the full concatenation didn't match (e.g. "CC Adi" typed but
            // only "ADI" is registered), retry with just the LAST word - lets
            // "@CC Adi 1.1" and "@Adi 1.1" resolve identically.
            if (work == null) {
                val lastWord = workText.trim().split(" ").lastOrNull()
                if (!lastWord.isNullOrEmpty()) work = ALIAS_LOOKUP[lastWord.uppercase()]
            }

            val parts = numericText
                .split('.', ' ')
                .filter { it.isNotEmpty() }
                .mapNotNull { it.toIntOrNull() }

            return ParsedReference(work, workText, parts)
        }
    }

    data class ParsedReference(val work: ReferenceWorkDefinition?, val workTextTyped: String, val parts: List<Int>)

    private data class IndexEntry(val parts: IntArray, val recordKey: String, val reference: String)

    private var index: Map<String, List<IndexEntry>>? = null
    private val indexMutex = Mutex()

    private fun chaptersForWork(work: ReferenceWorkDefinition, hierarchy: List<com.prabhupadaconnect.vedabase.core.model.BookNode>) =
        hierarchy.firstOrNull { work.bookKey in it.underlyingBookKeys }?.let { book ->
            when {
                !book.hasGroups -> book.chapters
                work.bookKey in CC_LILA_TITLES -> {
                    val lilaTitle = CC_LILA_TITLES.getValue(work.bookKey)
                    book.groups.firstOrNull { it.title == lilaTitle }?.chapters.orEmpty()
                }
                else -> book.groups.flatMap { it.chapters } // SB: flatten every Canto for numeric indexing.
            }
        }.orEmpty()

    private suspend fun ensureIndex() {
        if (index != null) return
        indexMutex.withLock {
            if (index != null) return
            val hierarchy = corpusRepository.getLibraryHierarchy()

            val built = HashMap<String, List<IndexEntry>>()
            for (work in WORKS) {
                val chapters = chaptersForWork(work, hierarchy)
                if (chapters.isEmpty()) continue

                val entries = mutableListOf<IndexEntry>()
                for (chapter in chapters) {
                    for (rec in chapter.records) {
                        val numbers = lastNIntegers(rec.reference, work.numericLevels) ?: continue
                        entries.add(IndexEntry(numbers, rec.recordKey, rec.reference))
                    }
                }
                entries.sortWith(compareBy(IntArrayComparator) { it.parts })
                built[work.bookKey] = entries
            }
            index = built
        }
    }

    /** Extracts the last [n] dot-separated integers from a Reference string, e.g. "SB 1.4.6" -> [1,4,6] for n=3. */
    private fun lastNIntegers(reference: String, n: Int): IntArray? {
        val pattern = when (n) {
            3 -> Regex("(\\d+)\\.(\\d+)\\.(\\d+)")
            2 -> Regex("(\\d+)\\.(\\d+)")
            else -> Regex("(\\d+)")
        }
        val m = pattern.find(reference) ?: return null
        return IntArray(n) { i -> m.groupValues[i + 1].toIntOrNull() ?: return null }
    }

    private object IntArrayComparator : Comparator<IntArray> {
        override fun compare(a: IntArray, b: IntArray): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val c = a[i].compareTo(b[i])
                if (c != 0) return c
            }
            return a.size.compareTo(b.size)
        }
    }

    /** Resolves a fully-specified reference to an exact RecordKey, or null if no such verse exists. */
    suspend fun tryResolveExact(rawText: String): String? {
        ensureIndex()
        val (work, _, parts) = parse(rawText)
        if (work == null || parts.size != work.numericLevels) return null
        val entries = index?.get(work.bookKey) ?: return null
        return entries.firstOrNull { it.parts.contentEquals(parts.toIntArray()) }?.recordKey
    }

    /** Progressive autocomplete suggestions for the current "@..." text - pure in-memory index lookup, no SQLite per keystroke. */
    suspend fun getSuggestions(rawText: String, maxResults: Int = 20): List<ReferenceSuggestion> {
        ensureIndex()
        val (work, workText, parts) = parse(rawText)
        val builtIndex = index ?: emptyMap()

        // Stage 0: bare "@" or unrecognized work prefix so far -> list works.
        if (work == null) {
            val filter = NON_ALPHA.replace(workText, "").uppercase()

            if (filter == "CC" || filter.startsWith("CAITANYA")) {
                return listOf(
                    ReferenceSuggestion("Śrī Caitanya-caritāmṛta — Ādi-līlā", "ADI", queryToComplete = "@CC Adi ", isWork = true),
                    ReferenceSuggestion("Śrī Caitanya-caritāmṛta — Madhya-līlā", "MADHYA", queryToComplete = "@CC Madhya ", isWork = true),
                    ReferenceSuggestion("Śrī Caitanya-caritāmṛta — Antya-līlā", "ANTYA", queryToComplete = "@CC Antya ", isWork = true)
                )
            }

            return WORKS
                .filter { builtIndex.containsKey(it.bookKey) } // only ever suggest a work this corpus actually has verses for
                .filter {
                    filter.isEmpty() ||
                        it.bookKey.startsWith(filter, ignoreCase = true) ||
                        it.displayTitle.uppercase().contains(filter) ||
                        it.aliases.any { a -> a.startsWith(filter, ignoreCase = true) }
                }
                .take(maxResults)
                .map { ReferenceSuggestion(it.displayTitle, it.bookKey, queryToComplete = "@${it.bookKey} ", isWork = true) }
        }

        val entries = builtIndex[work.bookKey]
        if (entries.isNullOrEmpty()) return emptyList()

        // Stage: fully-specified reference -> the exact verse (single result).
        if (parts.size >= work.numericLevels) {
            val exact = entries.firstOrNull { it.parts.toList().take(work.numericLevels) == parts.take(work.numericLevels) }
                ?: return emptyList()
            return listOf(
                ReferenceSuggestion(
                    displayText = exact.reference,
                    subText = work.displayTitle,
                    recordKey = exact.recordKey,
                    queryToComplete = "@${work.bookKey} ${exact.parts.joinToString(".")}"
                )
            )
        }

        // Stage: partial numeric prefix -> list matching next-level entries.
        val matches = entries.filter { parts.isEmpty() || it.parts.toList().take(parts.size) == parts }
        val nextLevelDepth = parts.size + 1

        if (nextLevelDepth >= work.numericLevels) {
            return matches.take(maxResults).map {
                ReferenceSuggestion(
                    displayText = it.reference,
                    subText = work.displayTitle,
                    recordKey = it.recordKey,
                    queryToComplete = "@${work.bookKey} ${it.parts.joinToString(".")}"
                )
            }
        }

        val seen = HashSet<String>()
        val results = mutableListOf<ReferenceSuggestion>()
        for (e in matches) {
            val prefixParts = e.parts.take(nextLevelDepth)
            val key = prefixParts.joinToString(".")
            if (!seen.add(key)) continue
            results.add(
                ReferenceSuggestion(
                    displayText = "${work.bookKey} $key — ${work.levelNames[nextLevelDepth - 1]} ${prefixParts.last()}",
                    subText = work.displayTitle,
                    queryToComplete = "@${work.bookKey} $key."
                )
            )
            if (results.size >= maxResults) break
        }
        return results
    }
}
