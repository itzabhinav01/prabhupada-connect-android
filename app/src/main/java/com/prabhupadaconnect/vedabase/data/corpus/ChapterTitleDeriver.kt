package com.prabhupadaconnect.vedabase.data.corpus

/**
 * Derives a human-readable chapter/section title for a corpus record's
 * Reference string, grouping verses into the same Library chapter bucket.
 * Ported from the desktop app's `SqliteCorpusRepository.DeriveChapterTitle`
 * (C#) - same book-specific parsing branches and fallbacks.
 *
 * Canonical chapter titles (matching vedabase.io's published chapter naming)
 * are ported for Bhagavad-gītā's 18 chapters; Śrīmad-Bhāgavatam and
 * Caitanya-caritāmṛta fall back to a bare "Canto C Chapter N" / "Chapter N"
 * (exactly what the desktop app itself falls back to whenever its much
 * larger canonical-title lookup table misses an entry).
 */
object ChapterTitleDeriver {

    private val bgTitles: Map<Int, String> = mapOf(
        1 to "Observing the Armies on the Battlefield of Kurukṣetra",
        2 to "Contents of the Gītā Summarized",
        3 to "Karma-yoga",
        4 to "Transcendental Knowledge",
        5 to "Karma-yoga—Action in Kṛṣṇa Consciousness",
        6 to "Dhyāna-yoga",
        7 to "Knowledge of the Absolute",
        8 to "Attaining the Supreme",
        9 to "The Most Confidential Knowledge",
        10 to "The Opulence of the Absolute",
        11 to "The Universal Form",
        12 to "Devotional Service",
        13 to "Nature, the Enjoyer, and Consciousness",
        14 to "The Three Modes of Material Nature",
        15 to "The Yoga of the Supreme Person",
        16 to "The Divine and Demoniac Natures",
        17 to "The Divisions of Faith",
        18 to "Conclusion—The Perfection of Renunciation"
    )

    private val earlyFrontMatter = Regex("(Setting the Scene|Dedication|Preface|Introduction|Foreword)\\s*$", RegexOption.IGNORE_CASE)
    private val proseNumberedChapter = Regex("^[A-Za-z0-9]+\\s+(\\d+):\\s*(.*)$")
    private val proseFrontMatter = Regex(
        "^[A-Za-z0-9]+(?::\\s*|\\s+)(Introduction|Preface|Foreword|Dedication|Words from Apple|Conclusion|Epilogue|Prologue)$",
        RegexOption.IGNORE_CASE
    )
    private val proseGgChapter = Regex("^GG:?\\s*Chapter\\s+(\\d+)$|^GG\\s+(\\d+)$", RegexOption.IGNORE_CASE)
    private val proseAnyChapterNumber = Regex("^[A-Za-z0-9]+\\s+(\\d+)")

    private val spsSectionNames = mapOf(
        1 to "1. Auspicious Invocation Mantras", 2 to "2. Śrī Śrī Gurv-aṣṭaka",
        3 to "3. Śrī Śrī Ṣaḍ-gosvāmy-aṣṭaka", 4 to "4. Śrī Śrī Śikṣāṣṭaka",
        5 to "5. Bhagavad-gītā", 6 to "6. Śrīmad-Bhāgavatam", 7 to "7. Caitanya-caritāmṛta",
        8 to "8. Śrī Brahma-saṃhitā", 9 to "9. Vedānta-sūtra", 10 to "10. The Upaniṣads",
        11 to "11. Caitanya Bhāgavata", 12 to "12. Six Gosvāmīs & Others", 13 to "13. Purāṇas",
        14 to "14. Mahābhārata", 15 to "15. Other Vedic Literatures", 16 to "16. Previous Ācāryas",
        17 to "17. Bhaktivinoda Ṫhākura", 18 to "18. Narottama dāsa Ṫhākura", 19 to "19. Jayadeva Gosvāmī",
        20 to "20. Nīti-śāstra", 21 to "21. Non Devotees", 22 to "22. Quotes from Other Sources"
    )

    fun derive(bookKey: String, reference: String, recordTitle: String?): String {
        if (reference.isBlank()) return recordTitle?.takeIf { it.isNotBlank() } ?: "Verses"

        val firstRef = reference.split(",")[0].trim()

        earlyFrontMatter.find(firstRef)?.let { return it.groupValues[1] }

        when (bookKey) {
            "BG" -> {
                Regex("^Bg\\s+(\\d+)", RegexOption.IGNORE_CASE).find(firstRef)?.let { m ->
                    val n = m.groupValues[1].toInt()
                    val title = bgTitles[n]
                    return if (title != null) "Chapter $n: $title" else "Chapter $n"
                }
            }
            "SB" -> {
                Regex("^(?:SB\\s+)?(\\d+)\\.(\\d+)", RegexOption.IGNORE_CASE).find(firstRef)?.let { m ->
                    val canto = m.groupValues[1].toInt()
                    val ch = m.groupValues[2].toInt()
                    return "Canto $canto Chapter $ch"
                }
            }
            "DI", "MADHYA", "ANTYA" -> {
                if (firstRef.contains("Concluding Words", ignoreCase = true)) return "Concluding Words"
                Regex("^(?:Ādi|Adi|Madhya|Antya)\\s+(\\d+)", RegexOption.IGNORE_CASE).find(firstRef)?.let { m ->
                    val n = m.groupValues[1].toInt()
                    return "Chapter $n"
                }
            }
            "BS" -> {
                Regex("^Bs\\s+(\\d+)", RegexOption.IGNORE_CASE).find(firstRef)?.let { return "Chapter ${it.groupValues[1]}" }
                return "Chapter 5"
            }
            "BB" -> {
                Regex("^BB\\s+(\\d+)\\.(\\d+)", RegexOption.IGNORE_CASE).find(firstRef)?.let {
                    return "Part ${it.groupValues[1]} Chapter ${it.groupValues[2]}"
                }
            }
            "ISO" -> return "Mantras"
            "NOI" -> return "Texts 1–11"
            "MM" -> return "Verses"
            "UNKNOWN" -> return "Morning Walk Conversations"
            "SPS" -> {
                Regex("^SPS\\s+(?:Section\\s+)?(\\d+)", RegexOption.IGNORE_CASE).find(firstRef)?.let { m ->
                    val sec = m.groupValues[1].toInt()
                    return spsSectionNames[sec] ?: "Section $sec"
                }
                return "Verses"
            }
            "SVA" -> {
                if (recordTitle != null && (
                        recordTitle.equals("Foreword", true) ||
                            recordTitle.equals("Introduction", true) ||
                            recordTitle.contains("Glimpse", true)
                        )
                ) {
                    return "Foreword & Introduction"
                }
                Regex("^SVA\\s+(\\d+)", RegexOption.IGNORE_CASE).find(firstRef)?.let { m ->
                    val sec = m.groupValues[1].toInt()
                    return when (sec) {
                        1 -> "1: Standard Prayers"
                        2 -> "2: Songs of Śrīla Bhaktivinoda Ṫhākura"
                        3 -> "3: Songs of Śrīla Narottama dāsa Ṫhākura"
                        4 -> "4: Songs of Other Vaiṣṇava Ācāryas"
                        else -> "Section $sec"
                    }
                }
                return "Foreword & Introduction"
            }
            "TMG" -> return "Temple Mantras"
        }

        // Prose/anthology books: each record is its own chapter/section.
        proseGgChapter.find(firstRef)?.let { m ->
            val ggNum = m.groupValues[1].ifEmpty { m.groupValues[2] }
            return "Chapter $ggNum"
        }

        proseNumberedChapter.find(firstRef)?.let { return "Chapter ${it.groupValues[1]}: ${it.groupValues[2]}" }
        proseFrontMatter.find(firstRef)?.let { return it.groupValues[1] }

        if (!recordTitle.isNullOrBlank()) {
            val chapterNumMatch = proseAnyChapterNumber.find(firstRef)
            return if (chapterNumMatch != null) "Chapter ${chapterNumMatch.groupValues[1]}: $recordTitle" else recordTitle
        }

        return "Verses"
    }
}
