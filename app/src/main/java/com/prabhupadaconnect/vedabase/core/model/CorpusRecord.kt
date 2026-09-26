package com.prabhupadaconnect.vedabase.core.model

import com.prabhupadaconnect.vedabase.core.util.DevanagariNormalizer
import com.prabhupadaconnect.vedabase.core.util.ProseFormatter

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
}

data class BookNode(
    val bookKey: String,
    val title: String,
    val author: String = "His Divine Grace A.C. Bhaktivedanta Swami Prabhupāda",
    val category: String = "",
    val isPdf: Boolean = false,
    val pdfPath: String? = null,
    val chapters: MutableList<ChapterNode> = mutableListOf()
) {
    val isOtherAuthor: Boolean
        get() = author.isNotBlank() &&
            !author.contains("Prabhupāda", ignoreCase = true) &&
            !author.contains("Bhaktivedanta Swami", ignoreCase = true)

    val isImported: Boolean
        get() = isPdf || bookKey.startsWith("PDF_", ignoreCase = true) || isOtherAuthor
}

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
