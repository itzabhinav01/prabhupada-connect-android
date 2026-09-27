package com.prabhupadaconnect.vedabase.data.corpus

/**
 * Expands the Search screen's selected book filter chips into the exact set
 * of real corpus `BookKey`s a query's SQL `WHERE r.BookKey IN (...)` clause
 * should match - pulled out of [CorpusRepository.search] so this mapping
 * (and, above all, that it is applied identically to both the COUNT and the
 * row-fetch query) can be verified without a real SQLite database.
 */
object SearchBookFilter {

    /**
     * Null means "no filter selected" (search every book). A non-null list
     * means "restrict to exactly these BookKeys" - "CC" expands to its three
     * underlying corpus keys since Caitanya-caritāmṛta is one work spread
     * across three real BookKeys (DI/MADHYA/ANTYA).
     */
    fun expand(bookKeys: List<String>?): List<String>? {
        if (bookKeys == null) return null
        val expanded = mutableListOf<String>()
        bookKeys.filter { it.isNotBlank() }.distinct().forEach { bk ->
            if (bk == "CC") expanded.addAll(listOf("DI", "MADHYA", "ANTYA")) else expanded.add(bk)
        }
        return expanded
    }
}
