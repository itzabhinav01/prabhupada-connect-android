package com.prabhupadaconnect.vedabase.ui.library

import androidx.compose.ui.graphics.Color
import com.prabhupadaconnect.vedabase.core.model.BookNode

/**
 * Visual styling metadata for books, modeled after modern scripture apps
 * with vibrant initial badges, category tags, and subtitles.
 */
data class BookCardVisual(
    val initial: String,
    val badgeColor: Color,
    val categoryTag: String,
    val subtitle: String
)

object BookVisualRegistry {
    private val visualMap = mapOf(
        "BG" to BookCardVisual(
            initial = "B",
            badgeColor = Color(0xFF2E7D32), // Green
            categoryTag = "SCRIPTURE",
            subtitle = "The Song of God"
        ),
        "SB" to BookCardVisual(
            initial = "Ś",
            badgeColor = Color(0xFF1976D2), // Blue
            categoryTag = "SCRIPTURE",
            subtitle = "The Beautiful Story of the Personality of Godhead"
        ),
        "CC" to BookCardVisual(
            initial = "Ś",
            badgeColor = Color(0xFF7B1FA2), // Purple
            categoryTag = "SCRIPTURE",
            subtitle = "The Nectar of the Activities of Lord Caitanya"
        ),
        "NOI" to BookCardVisual(
            initial = "N",
            badgeColor = Color(0xFFE65100), // Orange
            categoryTag = "SCRIPTURE",
            subtitle = "Śrī Upadeśāmṛta"
        ),
        "ISO" to BookCardVisual(
            initial = "Ś",
            badgeColor = Color(0xFFC2185B), // Deep Pink/Red
            categoryTag = "SCRIPTURE",
            subtitle = "Knowledge of the Absolute"
        ),
        "BS" to BookCardVisual(
            initial = "B",
            badgeColor = Color(0xFF303F9F), // Indigo
            categoryTag = "SCRIPTURE",
            subtitle = "Prayers of Lord Brahmā"
        ),
        "NOD" to BookCardVisual(
            initial = "N",
            badgeColor = Color(0xFFD84315), // Deep Orange
            categoryTag = "SCRIPTURE",
            subtitle = "The Complete Science of Bhakti-yoga"
        ),
        "KB" to BookCardVisual(
            initial = "K",
            badgeColor = Color(0xFF0097A7), // Cyan/Teal
            categoryTag = "SCRIPTURE",
            subtitle = "The Supreme Personality of Godhead"
        ),
        "TLC" to BookCardVisual(
            initial = "T",
            badgeColor = Color(0xFF5D4037), // Brown
            categoryTag = "SCRIPTURE",
            subtitle = "The Precepts of Lord Caitanya Mahāprabhu"
        ),
        "TLK" to BookCardVisual(
            initial = "T",
            badgeColor = Color(0xFF00796B),
            categoryTag = "SCRIPTURE",
            subtitle = "Teachings of Lord Kapila"
        ),
        "TQK" to BookCardVisual(
            initial = "T",
            badgeColor = Color(0xFF7B1FA2),
            categoryTag = "SCRIPTURE",
            subtitle = "Teachings of Queen Kuntī"
        ),
        "BB" to BookCardVisual(
            initial = "B",
            badgeColor = Color(0xFF2E7D32),
            categoryTag = "SCRIPTURE",
            subtitle = "By Sanātana Gosvāmī with commentary"
        ),
        "MM" to BookCardVisual(
            initial = "M",
            badgeColor = Color(0xFF303F9F),
            categoryTag = "SCRIPTURE",
            subtitle = "Prayers of King Kulaśekhara"
        ),
        "NBS" to BookCardVisual(
            initial = "N",
            badgeColor = Color(0xFFE65100),
            categoryTag = "SCRIPTURE",
            subtitle = "Aphorisms on Divine Love"
        ),
        "GG" to BookCardVisual(
            initial = "G",
            badgeColor = Color(0xFF1976D2),
            categoryTag = "SCRIPTURE",
            subtitle = "Bengali Poetic Translation of Bhagavad-gītā"
        ),
        "SSR" to BookCardVisual(
            initial = "S",
            badgeColor = Color(0xFF455A64), // Blue Grey
            categoryTag = "ESSAYS",
            subtitle = "Articles and Conversations"
        ),
        "PQPA" to BookCardVisual(
            initial = "P",
            badgeColor = Color(0xFF00796B), // Teal
            categoryTag = "CONVERSATIONS",
            subtitle = "A Dialogue with Bob Cohen"
        ),
        "SPL" to BookCardVisual(
            initial = "Ś",
            badgeColor = Color(0xFF512DA8), // Deep Purple
            categoryTag = "BIOGRAPHY",
            subtitle = "Biography by Satsvarūpa dāsa Goswami"
        ),
        "SVA" to BookCardVisual(
            initial = "S",
            badgeColor = Color(0xFFF57C00), // Amber
            categoryTag = "SONGS",
            subtitle = "Songs of the Vaiṣṇava Masters"
        ),
        "TMG" to BookCardVisual(
            initial = "T",
            badgeColor = Color(0xFFE64A19), // Orange
            categoryTag = "MANTRAS",
            subtitle = "Daily ISKCON Temple Prayers & Mantras"
        )
    )

    private val defaultPalette = listOf(
        Color(0xFF2E7D32),
        Color(0xFF1976D2),
        Color(0xFF7B1FA2),
        Color(0xFFE65100),
        Color(0xFF0097A7),
        Color(0xFFC2185B)
    )

    fun getCanonicalVerseCount(bookKey: String, actualCount: Int): Int {
        return when (bookKey.uppercase()) {
            "BG" -> 700 // Bhagavad-gītā canonical verse count (standard BBT edition groups combined verses e.g. 1.16-18)
            else -> actualCount
        }
    }

    fun getVisual(book: BookNode): BookCardVisual {
        visualMap[book.bookKey.uppercase()]?.let { return it }

        // Fallback for other books
        val initial = book.title.firstOrNull { it.isLetter() }?.uppercase() ?: "B"
        val colorIndex = Math.abs(book.bookKey.hashCode()) % defaultPalette.size
        val isScripture = book.category.contains("Scripture", ignoreCase = true)
        val categoryTag = if (isScripture) "SCRIPTURE" else book.category.ifBlank { "BOOK" }.uppercase()
        val subtitle = book.author.ifBlank { "His Divine Grace A.C. Bhaktivedanta Swami Prabhupāda" }

        return BookCardVisual(
            initial = initial,
            badgeColor = defaultPalette[colorIndex],
            categoryTag = categoryTag,
            subtitle = subtitle
        )
    }
}
