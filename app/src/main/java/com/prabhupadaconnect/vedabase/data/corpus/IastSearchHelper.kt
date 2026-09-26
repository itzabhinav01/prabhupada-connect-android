package com.prabhupadaconnect.vedabase.data.corpus

/**
 * Bridges IAST (International Alphabet of Sanskrit Transliteration)
 * diacritics with plain English queries across SQLite GLOB matching, so
 * queries like "bhunjana" and "bhuñjāna" match identically.
 *
 * Ported 1:1 from the desktop app's `IastSearchHelper` (C#).
 */
object IastSearchHelper {

    private val iastCharToGlobClass: Map<Char, String> = buildMap {
        put('a', "[aā]"); put('A', "[AĀ]"); put('ā', "[aā]"); put('Ā', "[AĀ]")
        put('i', "[iī]"); put('I', "[IĪ]"); put('ī', "[iī]"); put('Ī', "[IĪ]")
        put('u', "[uū]"); put('U', "[UŪ]"); put('ū', "[uū]"); put('Ū', "[UŪ]")
        put('r', "[rṛṝ]"); put('R', "[RṚṜ]")
        put('ṛ', "[rṛṝ]"); put('Ṛ', "[RṚṜ]")
        put('ṝ', "[rṛṝ]"); put('Ṝ', "[RṚṜ]")
        put('l', "[lḷḹ]"); put('L', "[LḶḸ]")
        put('ḷ', "[lḷḹ]"); put('Ḷ', "[LḶḸ]")
        put('ḹ', "[lḷḹ]"); put('Ḹ', "[LḶḸ]")
        put('e', "[eē]"); put('E', "[EĒ]"); put('ē', "[eē]"); put('Ē', "[EĒ]")
        put('o', "[oō]"); put('O', "[OŌ]"); put('ō', "[oō]"); put('Ō', "[OŌ]")
        put('m', "[mṃṁ]"); put('M', "[MṂṀ]")
        put('ṃ', "[mṃṁ]"); put('Ṃ', "[MṂṀ]")
        put('ṁ', "[mṃṁ]"); put('Ṁ', "[MṂṀ]")
        put('h', "[hḥ]"); put('H', "[HḤ]"); put('ḥ', "[hḥ]"); put('Ḥ', "[HḤ]")
        put('n', "[nñṅṇ]"); put('N', "[NÑṄṆ]")
        put('ñ', "[nñṅṇ]"); put('Ñ', "[NÑṄṆ]")
        put('ṅ', "[nñṅṇ]"); put('Ṅ', "[NÑṄṆ]")
        put('ṇ', "[nñṅṇ]"); put('Ṇ', "[NÑṄṆ]")
        put('t', "[tṭ]"); put('T', "[TṬ]"); put('ṭ', "[tṭ]"); put('Ṭ', "[TṬ]")
        put('d', "[dḍ]"); put('D', "[DḌ]"); put('ḍ', "[dḍ]"); put('Ḍ', "[DḌ]")
        put('s', "[sśṣ]"); put('S', "[SŚṢ]")
        put('ś', "[sśṣ]"); put('Ś', "[SŚṢ]")
        put('ṣ', "[sśṣ]"); put('Ṣ', "[SŚṢ]")
    }

    private val commonPhoneticVariants: Map<String, List<String>> = mapOf(
        "krishna" to listOf("krishna", "krsna"),
        "krsna" to listOf("krsna", "krishna"),
        "kṛṣṇa" to listOf("kṛṣṇa", "krsna", "krishna"),
        "chaitanya" to listOf("chaitanya", "caitanya"),
        "caitanya" to listOf("caitanya", "chaitanya"),
        "shiva" to listOf("shiva", "siva"),
        "siva" to listOf("siva", "shiva"),
        "vishnu" to listOf("vishnu", "visnu"),
        "visnu" to listOf("visnu", "vishnu"),
        "vrindavan" to listOf("vrindavan", "vrindavana", "vrndavana"),
        "sankirtan" to listOf("sankirtan", "sankirtana")
    )

    /**
     * Converts an input word/phrase into a case-preserving SQLite GLOB
     * pattern that transparently matches both plain English and IAST
     * diacritical characters.
     */
    fun toIastGlobPattern(text: String): String {
        if (text.isBlank()) return "*"
        val sb = StringBuilder("*")
        for (ch in text) {
            val globClass = iastCharToGlobClass[ch]
            when {
                globClass != null -> sb.append(globClass)
                ch == '*' || ch == '?' || ch == '[' || ch == ']' -> sb.append('[').append(ch).append(']')
                else -> sb.append(ch)
            }
        }
        sb.append('*')
        return sb.toString()
    }

    /**
     * Case-matching GLOB predicate builder against Records columns r.*.
     * Android's SQLite binds only positional "?" placeholders (no named
     * parameters), so this returns the SQL fragment with "?" in argument
     * order alongside the exact list of GLOB pattern values to bind at
     * those positions - the caller appends both directly after whatever it
     * already built.
     */
    fun buildCaseGlobSqlClause(query: String): Pair<String, List<String>> {
        if (query.isBlank()) return "" to emptyList()

        val terms = extractTerms(query)
        if (terms.isEmpty()) return "" to emptyList()

        val andClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        for (term in terms) {
            val variants = getVariants(term)
            val columnChecks = mutableListOf<String>()

            for (variant in variants) {
                val pattern = toIastGlobPattern(variant)
                // One "?" per column check, repeating the same pattern value
                // for each - Android/SQLite has no reusable named parameter.
                repeat(5) { args.add(pattern) }
                columnChecks.add("r.Transliteration GLOB ?")
                columnChecks.add("r.Synonyms GLOB ?")
                columnChecks.add("r.Translation GLOB ?")
                columnChecks.add("r.Purports GLOB ?")
                columnChecks.add("r.Reference GLOB ?")
            }

            andClauses.add("(" + columnChecks.joinToString(" OR ") + ")")
        }

        val sql = if (andClauses.isNotEmpty()) " AND " + andClauses.joinToString(" AND ") else ""
        return sql to args
    }

    private fun extractTerms(query: String): List<String> {
        val terms = mutableListOf<String>()
        val trimmed = query.trim()

        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            if (inner.isNotEmpty()) {
                terms.add(inner)
                return terms
            }
        }

        val matches = Regex("[\\p{L}\\p{N}\\-]+").findAll(trimmed)
        for (m in matches) {
            val word = m.value.trim()
            if (word.isEmpty()) continue
            if (word == "AND" || word == "OR" || word == "NOT" || word.equals("NEAR", ignoreCase = true)) continue
            terms.add(word)
        }

        if (terms.isEmpty() && trimmed.isNotBlank()) terms.add(trimmed)
        return terms
    }

    private fun getVariants(term: String): List<String> {
        val variants = mutableListOf(term)
        val clean = term.replace(Regex("[^\\p{L}]"), "")
        val phonetics = if (clean.isNotEmpty()) commonPhoneticVariants[clean.lowercase()] else null

        if (phonetics != null) {
            val isAllUpper = term.all { !it.isLetter() || it.isUpperCase() }
            val isTitle = term.isNotEmpty() && term[0].isUpperCase() && term.drop(1).all { !it.isLetter() || it.isLowerCase() }

            for (ph in phonetics) {
                val formatted = when {
                    isAllUpper -> ph.uppercase()
                    isTitle -> ph.replaceFirstChar { it.uppercase() }
                    else -> ph
                }
                if (variants.none { it == formatted }) variants.add(formatted)
            }
        }

        return variants
    }
}
