package com.prabhupadaconnect.vedabase.core.util

/**
 * Deterministic display-time normalizer for legacy Indevr-decoded Devanagari
 * text. Transforms systemic 8-bit glyph encoding artifacts into valid,
 * canonical Unicode Devanagari. Operates purely on structural script
 * patterns without hardcoded lexical dictionaries. Fully idempotent and
 * non-destructive to already-valid Unicode Sanskrit.
 *
 * Ported 1:1 from the desktop app's `DevanagariNormalizer` (C#) - same
 * regexes, same fixed-point iteration (max 3 passes), same ordering.
 */
object DevanagariNormalizer {

    // 1. Compose split vowel signs O (aa + e -> o) and AU (aa + ai -> au)
    private val splitO = Regex("ाे")
    private val splitAu = Regex("ाै")

    // 2. Known Indevr ligature artifacts in ku and kr
    private val kuHyphen = Regex("क्ु-")
    private val krHyphen = Regex("क्ृ-")

    // 3. Indevr 'ha' spurious virama defect: unmodified 'ha' was mapped to
    // ह् (ह्). In Sanskrit, 'ha' only forms valid onsets in true
    // h-conjuncts with [म य ल व ण र]. Elsewhere the virama is spurious.
    private val spuriousHaVirama = Regex(
        "ह्(?=[^मयलवणर्ृ]|[ा-ौंः]|\\s|$)"
    )

    // 4. Pre-consonantal short-i (only when not already preceded by a consonant)
    private val preConsonantalShortI = Regex(
        "(?<![क-हक़-य़]़?)ि((?:[क-हक़-य़]़?्)*[क-हक़-य़]़?)"
    )

    // 5. Conflicting vowel signs: short-i immediately before vowel sign aa
    private val conflictingShortIAa = Regex("िा")

    // 6. Spurious virama before matras/anusvara, and duplicate virama collapse
    private val viramaBeforeMatra = Regex("्([ा-ौॢॣ])")
    private val viramaBeforeAnusvara = Regex("्ं")
    private val duplicateVirama = Regex("्{2,}")

    // 7. Conservative repha inversion - only at unambiguous word boundaries
    private val rephaBeforeBoundary = Regex(
        "(?<!्)([क-हक़-य़]़?[ा-ौ]?)र्(?=[\\s)(।॥,;:.\\-\$ंः])"
    )

    // 8. Legacy punctuation & word-terminal halant
    private val verseEndDandasNumber = Regex("\\)\\)\\s*(\\d+)\\s*\\)\\)")
    private val doubleDandas = Regex("\\)\\)")
    private val lineEndDanda = Regex("(?<=[ऀ-ॿ])\\s*\\)\\s*$", RegexOption.MULTILINE)
    private val midLineDanda = Regex("(?<=[ऀ-ॿ])\\s*\\)\\s*(?=[ऀ-ॿ]|$)")
    private val terminalHalantParen = Regex("([क-हक़-य़]़?)\\((?=[\\s)॥।.]|$)")

    fun normalize(raw: String?): String {
        if (raw.isNullOrEmpty()) return raw ?: ""

        var current: String = raw
        repeat(3) {
            val prev = current

            current = splitO.replace(current, "ो")
            current = splitAu.replace(current, "ौ")

            current = kuHyphen.replace(current, "कु")
            current = krHyphen.replace(current, "कृ")

            current = spuriousHaVirama.replace(current, "ह")

            current = preConsonantalShortI.replace(current) { m -> "${m.groupValues[1]}ि" }

            current = conflictingShortIAa.replace(current, "ा")

            current = viramaBeforeMatra.replace(current) { m -> m.groupValues[1] }
            current = viramaBeforeAnusvara.replace(current, "ं")
            current = duplicateVirama.replace(current, "्")

            current = rephaBeforeBoundary.replace(current) { m -> "र्${m.groupValues[1]}" }

            current = verseEndDandasNumber.replace(current) { m -> "॥ ${m.groupValues[1]} ॥" }
            current = doubleDandas.replace(current, "॥")
            current = lineEndDanda.replace(current, " ।")
            current = midLineDanda.replace(current, " । ")
            current = terminalHalantParen.replace(current) { m -> "${m.groupValues[1]}्" }

            if (current == prev) return@repeat
        }

        return current
    }
}
