package com.prabhupadaconnect.vedabase.core.model

import com.prabhupadaconnect.vedabase.core.util.DevanagariNormalizer
import com.prabhupadaconnect.vedabase.core.util.ProseFormatter
import com.prabhupadaconnect.vedabase.core.util.SongPayloadParser

/**
 * One row of the frozen, read-only canonical corpus. Mirrors the desktop
 * app's `CorpusRecord` 1:1 - never mutated, never written back to the
 * corpus database.
 */
data class CorpusRecord(
    val recordKey: String,
    val bookKey: String,
    val sequence: Int,
    val parentKey: String?,
    val recordType: String,
    val reference: String?,
    val referenceStatus: String,
    val title: String?,
    val rawDevanagari: String,
    val transliteration: String,
    val synonyms: String,
    val translation: String,
    val purports: String
) {
    /** Devanagari normalized for display (memoized on first access). */
    val devanagari: String by lazy { DevanagariNormalizer.normalize(rawDevanagari) }

    val hasDevanagari: Boolean get() = devanagari.isNotBlank()
    val hasTransliteration: Boolean get() = transliteration.isNotBlank()
    val hasSynonyms: Boolean get() = synonyms.isNotBlank()
    val hasTranslation: Boolean get() = translation.isNotBlank()
    val hasPurports: Boolean get() = purports.isNotBlank()

    /** Continuous flowing prose translation with hard-wrap artifacts removed. */
    val cleanTranslation: String by lazy { ProseFormatter.cleanProse(translation) }

    /** UI-friendly split purports; FTS stores them as one string. */
    val purportParagraphs: List<String> by lazy { ProseFormatter.getCleanParagraphs(purports) }

    /**
     * Same paragraphs as [purportParagraphs], but each carries its original,
     * uncleaned line breaks too - needed to detect an inline-quoted verse
     * stanza (see [com.prabhupadaconnect.vedabase.core.util.PurportBlockDetector]),
     * a signal [ProseFormatter.cleanProse] otherwise erases entirely.
     */
    val purportParagraphPairs: List<ProseFormatter.Paragraph> by lazy { ProseFormatter.getParagraphs(purports) }

    /**
     * Structured song/mantra payload, for the ~105 SVA/TMG records that
     * embed one as JSON instead of plain prose (see [SongPayload]). Checked
     * across every text field that could plausibly carry it, though in
     * practice the corpus only ever puts it in `Purports`.
     */
    val songPayload: com.prabhupadaconnect.vedabase.core.model.SongPayload? by lazy {
        SongPayloadParser.tryParse(rawDevanagari)
            ?: SongPayloadParser.tryParse(translation)
            ?: SongPayloadParser.tryParse(purports)
    }
}

/**
 * One work in the Library tree. Most books go straight to [chapters]; the
 * two multi-tier scriptures (Śrīmad-Bhāgavatam's 12 Cantos, Caitanya-
 * caritāmṛta's 3 līlās) instead populate [groups], one level up from their
 * own chapters, so a ~335-chapter scripture doesn't dump every chapter into
 * one flat scrolling list. [underlyingBookKeys] is every real corpus
 * `BookKey` this node represents - for the merged Caitanya-caritāmṛta node
 * that's {"DI","MADHYA","ANTYA"}; for everything else, just its own [bookKey].
 */
data class BookNode(
    val bookKey: String,
    val title: String,
    val author: String = "His Divine Grace A.C. Bhaktivedanta Swami Prabhupāda",
    val category: String = "",
    val isPdf: Boolean = false,
    val pdfPath: String? = null,
    val chapters: MutableList<ChapterNode> = mutableListOf(),
    val groups: MutableList<BookGroupNode> = mutableListOf(),
    val underlyingBookKeys: Set<String> = setOf(bookKey)
) {
    val isOtherAuthor: Boolean
        get() = author.isNotBlank() &&
            !author.contains("Prabhupāda", ignoreCase = true) &&
            !author.contains("Bhaktivedanta Swami", ignoreCase = true)

    val isImported: Boolean
        get() = isPdf || bookKey.startsWith("PDF_", ignoreCase = true) || isOtherAuthor

    val hasGroups: Boolean get() = groups.isNotEmpty()

    val verseCount: Int
        get() = if (hasGroups) groups.sumOf { g -> g.chapters.sumOf { it.records.size } }
        else chapters.sumOf { it.records.size }
}

/** An intermediate grouping level between a [BookNode] and its [ChapterNode]s - a Canto or a līlā. */
data class BookGroupNode(
    val title: String,
    val chapters: MutableList<ChapterNode> = mutableListOf()
)

data class ChapterNode(
    val title: String,
    val records: MutableList<RecordNode> = mutableListOf()
) {
    val hasMultipleRecords: Boolean get() = records.size > 1
}

data class RecordNode(
    val recordKey: String,
    val reference: String,
    val sequence: Int
)

data class SearchResult(
    val recordKey: String,
    val bookKey: String,
    val reference: String,
    val bookTitle: String,
    var preview: String = "",
    val category: String = "Scripture",
    val sequence: Int = 0,
    val isExactMatch: Boolean = false,
    val matchTier: Int = 6,
    val field: String? = null,
    val startOffset: Int = -1,
    val length: Int = -1,
    val highlightColor: HighlightColor? = null
)

data class VocabTerm(
    val term: String,
    val docCount: Int,
    val occurrenceCount: Int
)
