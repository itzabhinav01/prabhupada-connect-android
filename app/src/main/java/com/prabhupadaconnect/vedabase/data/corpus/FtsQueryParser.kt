package com.prabhupadaconnect.vedabase.data.corpus

/**
 * Preprocesses user search queries into safe, optimized SQLite FTS5 queries.
 * Handles quote balancing, disarms bare boolean keywords, sanitizes
 * punctuation, expands common Anglicized/IAST phonetic pairs, and normalizes
 * Devanagari script.
 *
 * Ported 1:1 from the desktop app's `FtsQueryParser` (C#) - same expansion
 * tables, same citation/proximity/token-extraction regexes, same ordering of
 * passes, so query behavior between the Android and Windows clients matches
 * exactly against the same corpus.
 */
object FtsQueryParser {

    private val phoneticExpansions: Map<String, List<String>> = buildMap {
        put("krishna", listOf("krishna", "krsna", "kṛṣṇa"))
        put("krsna", listOf("krsna", "kṛṣṇa", "krishna"))
        put("kṛṣṇa", listOf("kṛṣṇa", "krsna", "krishna"))
        put("chaitanya", listOf("chaitanya", "caitanya"))
        put("caitanya", listOf("caitanya", "chaitanya"))
        put("shiva", listOf("shiva", "siva", "śiva"))
        put("siva", listOf("siva", "śiva", "shiva"))
        put("vishnu", listOf("vishnu", "visnu", "viṣṇu"))
        put("visnu", listOf("visnu", "viṣṇu", "vishnu"))
        put("vrindavan", listOf("vrindavan", "vrindavana", "vrndavana", "vṛndāvana"))
        put("vrindavana", listOf("vrindavana", "vrndavana", "vṛndāvana", "vrindavan"))
        put("kurukshetra", listOf("kurukshetra", "kuruksetra", "kuru-kṣetra"))
        put("kuruksetra", listOf("kuruksetra", "kuru-kṣetra", "kurukshetra"))
        put("prabhupada", listOf("prabhupada", "prabhupāda", "Śrīla prabhupāda"))
        put("prabhupāda", listOf("prabhupāda", "prabhupada"))
        put("arjuna", listOf("arjuna", "arjun"))
        put("yashoda", listOf("yashoda", "yasoda", "yaśodā"))
        put("radha", listOf("radha", "rādhā", "radharani", "rādhārāṇī"))
        put("rādhā", listOf("rādhā", "radha", "radharani"))
        put("radharani", listOf("radharani", "radha", "rādhārāṇī"))
        put("sankirtan", listOf("sankirtan", "sankirtana", "saṅkīrtana"))
        put("sankirtana", listOf("sankirtana", "saṅkīrtana", "sankirtan"))
        put("saṅkīrtana", listOf("saṅkīrtana", "sankirtana", "sankirtan"))
        put("gaura", listOf("gaura", "gauranga", "gaurāṅga"))
        put("nimai", listOf("nimai", "nimāi"))
        put("haridas", listOf("haridas", "haridāsa"))
        put("haridāsa", listOf("haridāsa", "haridas"))
        put("bhaktivinoda", listOf("bhaktivinoda", "bhaktivinode"))
        put("bhaktisiddhanta", listOf("bhaktisiddhanta", "bhaktisiddhānta"))
        put("madhava", listOf("madhava", "mādhava"))
        put("govinda", listOf("govinda"))
    }

    private val devanagariExpansions: Map<String, List<String>> = mapOf(
        "अर्जुन" to listOf("अर्जुन", "अजुर्न"),
        "धर्मक्षेत्रे" to listOf(
            "धर्मक्षेत्रे",
            "धमर्क्षेत्र्आे",
            "धमर्क्षेत्रो"
        )
    )

    private val citationBg = Regex("^(?:Bg|BG|SB|Adi|Madhya|Antya|Bs|BS)\\s*[\\d.\\-]+$", RegexOption.IGNORE_CASE)
    private val noiPattern = Regex("^Noi\\s*(\\d+)$", RegexOption.IGNORE_CASE)
    private val isoPattern = Regex("^Iso\\s*(\\d+)$", RegexOption.IGNORE_CASE)
    private val ccLilaPattern = Regex("^Cc\\s+(Adi|Madhya|Antya)\\s*([\\d.\\-]+)$", RegexOption.IGNORE_CASE)
    private val proximityPattern = Regex(
        "([\\p{L}\\p{N}\"\\-]+)\\s+(?:w|near)/(\\d+)\\s+([\\p{L}\\p{N}\"\\-]+)",
        RegexOption.IGNORE_CASE
    )
    private val nearWholePattern = Regex("^NEAR\\s*\\(.+\\)$", RegexOption.IGNORE_CASE)
    private val tokenPattern = Regex(
        "(?i:NEAR\\s*\\([^)]+\\))|\"[^\"]+\"|[\\p{L}\\p{N}\\p{M}]+(?:[-.][\\p{L}\\p{N}\\p{M}]+)*|\\S+"
    )

    /**
     * Parses and sanitizes a raw user query string for FTS5 execution.
     * Returns an empty string if the query contains no valid search tokens.
     */
    fun parse(rawQuery: String?, isExactWord: Boolean = false): String {
        if (rawQuery.isNullOrBlank()) return ""

        var query = rawQuery.trim()

        // 1. Devanagari input: normalize and expand known internal cluster variants
        if (containsDevanagari(query)) {
            query = com.prabhupadaconnect.vedabase.core.util.DevanagariNormalizer.normalize(query)
            for ((key, variants) in devanagariExpansions) {
                if (query.contains(key)) {
                    val expanded = "(" + variants.joinToString(" OR ") { "\"$it\"" } + ")"
                    query = query.replace(key, expanded)
                }
            }
            return query
        }

        // 2. Citation pattern detection: "Bg 1.1", "SB 1.1.1", "Cc Adi 1.1", "Noi 1", "Iso 1"
        noiPattern.find(query)?.let { return "(noi AND \"${it.groupValues[1]}\")" }
        isoPattern.find(query)?.let { return "(iso AND \"${it.groupValues[1]}\")" }
        ccLilaPattern.find(query)?.let { return "\"${it.groupValues[1]} ${it.groupValues[2]}\"" }
        if (citationBg.matches(query)) {
            return "\"${query.replace("\"", "\"\"")}\""
        }

        // 3. Proximity search: Folio syntax "term1 w/N term2" or "term1 near/N term2"
        query = proximityPattern.replace(query) { m ->
            val t1 = m.groupValues[1].trim('"')
            val dist = m.groupValues[2]
            val t2 = m.groupValues[3].trim('"')
            "NEAR(\"$t1\" \"$t2\", $dist)"
        }

        if (nearWholePattern.matches(query)) return query

        // 4. Whole query wrapped in balanced quotes -> exact phrase search
        if (query.startsWith("\"") && query.endsWith("\"") && query.length >= 2) {
            val inner = query.substring(1, query.length - 1).replace("\"", "\"\"")
            return "\"$inner\""
        }

        // Balance quotes if user left an unclosed double quote
        if (query.count { it == '"' } % 2 != 0) {
            query += "\""
        }

        // 5. Token extraction: proximity expressions, quoted phrases, or individual words
        val resultTerms = mutableListOf<String>()
        for (match in tokenPattern.findAll(query)) {
            var token = match.value.trim()
            if (token.isEmpty()) continue

            if (token.startsWith("NEAR(", ignoreCase = true) && token.endsWith(")")) {
                resultTerms.add(token)
                continue
            }

            if (token.startsWith("\"") && token.endsWith("\"") && token.length >= 2) {
                resultTerms.add(token)
                continue
            }

            if (token == "AND" || token == "OR" || token == "NOT") {
                val last = resultTerms.lastOrNull()
                if (resultTerms.isNotEmpty() && last != "AND" && last != "OR" && last != "NOT") {
                    resultTerms.add(token)
                }
                continue
            }

            if (token.equals("and", ignoreCase = true) ||
                token.equals("or", ignoreCase = true) ||
                token.equals("not", ignoreCase = true)
            ) {
                continue
            }

            while (token.startsWith("*")) token = token.substring(1)
            if (token.isEmpty()) continue

            val cleanWord = token.replace(Regex("[^\\p{L}\\p{N}]"), "")
            val expansions = if (cleanWord.isNotEmpty()) phoneticExpansions[cleanWord.lowercase()] else null
            if (expansions != null) {
                resultTerms.add("(" + expansions.joinToString(" OR ") { "\"$it\"" } + ")")
                continue
            }

            if (isExactWord || token.contains('-') || token.contains('.') || token.contains(':') || token.contains('/')) {
                resultTerms.add("\"${token.replace("\"", "\"\"")}\"")
            } else {
                resultTerms.add(token)
            }
        }

        while (resultTerms.isNotEmpty() && resultTerms.last().let { it == "AND" || it == "OR" || it == "NOT" }) {
            resultTerms.removeAt(resultTerms.size - 1)
        }

        if (resultTerms.isEmpty()) return ""
        return resultTerms.joinToString(" ")
    }

    private fun containsDevanagari(text: String): Boolean = text.any { it.code in 0x0900..0x097F }
}
