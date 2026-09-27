package com.prabhupadaconnect.vedabase.core.registry

private const val PRABHUPADA_AUTHOR = "His Divine Grace A.C. Bhaktivedanta Swami Prabhupāda"

data class BookDescriptor(
    val bookKey: String,
    val title: String,
    val author: String = PRABHUPADA_AUTHOR,
    val abbreviation: String,
    val category: String,
    val canonicalOrder: Int
)

/**
 * Canonical Prabhupāda literary metadata - ported 1:1 from the desktop
 * app's `BookRegistry` (C#). Used to order the Library hierarchy and to sort
 * multi-book search results into scriptural order.
 */
object BookRegistry {

    val canonicalBooks: List<BookDescriptor> = listOf(
        BookDescriptor("BG", "Bhagavad-gītā As It Is", abbreviation = "Bg", category = "Scripture", canonicalOrder = 1),
        BookDescriptor("SB", "Śrīmad-Bhāgavatam", abbreviation = "SB", category = "Scripture", canonicalOrder = 2),
        BookDescriptor("DI", "Śrī Caitanya-caritāmṛta — Ādi-līlā", abbreviation = "CC Ādi", category = "Scripture", canonicalOrder = 3),
        BookDescriptor("MADHYA", "Śrī Caitanya-caritāmṛta — Madhya-līlā", abbreviation = "CC Madhya", category = "Scripture", canonicalOrder = 4),
        BookDescriptor("ANTYA", "Śrī Caitanya-caritāmṛta — Antya-līlā", abbreviation = "CC Antya", category = "Scripture", canonicalOrder = 5),
        BookDescriptor("NOD", "The Nectar of Devotion", abbreviation = "NoD", category = "Books", canonicalOrder = 6),
        BookDescriptor("TLC", "Teachings of Lord Caitanya", abbreviation = "TLC", category = "Books", canonicalOrder = 7),
        BookDescriptor("KB", "Kṛṣṇa, the Supreme Personality of Godhead", abbreviation = "KB", category = "Books", canonicalOrder = 8),
        BookDescriptor("ISO", "Śrī Īśopaniṣad", abbreviation = "Iso", category = "Scripture", canonicalOrder = 9),
        BookDescriptor("NOI", "The Nectar of Instruction", abbreviation = "NoI", category = "Scripture", canonicalOrder = 10),
        BookDescriptor("TLK", "Teachings of Lord Kapila", abbreviation = "TLK", category = "Scripture", canonicalOrder = 11),
        BookDescriptor("TQK", "Teachings of Queen Kuntī", abbreviation = "TQK", category = "Books", canonicalOrder = 12),
        BookDescriptor("BS", "Śrī Brahma-saṃhitā", abbreviation = "Bs", category = "Scripture", canonicalOrder = 13),
        BookDescriptor("MM", "Mukunda-mālā-stotra", abbreviation = "MM", category = "Scripture", canonicalOrder = 14),
        BookDescriptor("NBS", "Nārada-bhakti-sūtra", abbreviation = "NBS", category = "Scripture", canonicalOrder = 15),
        BookDescriptor("BB", "Bṛhad-bhāgavatāmṛta", abbreviation = "BB", category = "Scripture", canonicalOrder = 16),
        BookDescriptor("SPS", "Śrīla Prabhupāda Ślokas", abbreviation = "SPS", category = "Other Works", canonicalOrder = 17),
        BookDescriptor("DS", "Dialectical Spiritualism", abbreviation = "DS", category = "Philosophy", canonicalOrder = 18),
        BookDescriptor("BBD", "Beyond Birth and Death", abbreviation = "BBD", category = "Books", canonicalOrder = 18),
        BookDescriptor("POY", "The Perfection of Yoga", abbreviation = "PoY", category = "Books", canonicalOrder = 19),
        BookDescriptor("RV", "Rāja-Vidyā: The King of Knowledge", abbreviation = "RV", category = "Books", canonicalOrder = 20),
        BookDescriptor("EKC", "Elevation to Kṛṣṇa Consciousness", abbreviation = "EKC", category = "Books", canonicalOrder = 21),
        BookDescriptor("KCTYS", "Kṛṣṇa Consciousness: The Topmost Yoga System", abbreviation = "KCTYS", category = "Books", canonicalOrder = 22),
        BookDescriptor("MOG", "Message of Godhead", abbreviation = "MoG", category = "Books", canonicalOrder = 23),
        BookDescriptor("LOB", "Light of the Bhāgavata", abbreviation = "LoB", category = "Scripture", canonicalOrder = 24),
        BookDescriptor("PQPA", "Perfect Questions, Perfect Answers", abbreviation = "PQPA", category = "Conversations", canonicalOrder = 25),
        BookDescriptor("SSR", "The Science of Self-Realization", abbreviation = "SSR", category = "Essays & Articles", canonicalOrder = 26),
        BookDescriptor("JSD", "The Journey of Self-Discovery", abbreviation = "JSD", category = "Essays & Articles", canonicalOrder = 27),
        BookDescriptor("LCFL", "Life Comes From Life", abbreviation = "LCFL", category = "Conversations", canonicalOrder = 28),
        BookDescriptor("CB", "Coming Back: The Science of Reincarnation", abbreviation = "CB", category = "Books", canonicalOrder = 29),
        BookDescriptor("CAT", "Civilization and Transcendence", abbreviation = "CAT", category = "Books", canonicalOrder = 30),
        BookDescriptor("OWK", "On the Way to Kṛṣṇa", abbreviation = "OWK", category = "Books", canonicalOrder = 31),
        BookDescriptor("SFL", "The Search for Liberation", abbreviation = "SFL", category = "Conversations", canonicalOrder = 32),
        BookDescriptor("TT", "Transcendental Teachings of Prahlāda Mahārāja", abbreviation = "TT", category = "Books", canonicalOrder = 33),
        BookDescriptor("EJ", "Easy Journey to Other Planets", abbreviation = "EJ", category = "Books", canonicalOrder = 34),
        BookDescriptor("SC", "A Second Chance", abbreviation = "SC", category = "Books", canonicalOrder = 35),
        BookDescriptor("DWT", "Dharma: The Way of Transcendence", abbreviation = "DWT", category = "Books", canonicalOrder = 38),
        BookDescriptor("POP", "Path of Perfection", abbreviation = "PoP", category = "Books", canonicalOrder = 39),
        BookDescriptor("QFE", "Quest for Enlightenment", abbreviation = "QFE", category = "Books", canonicalOrder = 40),
        BookDescriptor("RTW", "Renunciation Through Wisdom", abbreviation = "RTW", category = "Books", canonicalOrder = 41),
        BookDescriptor("LON", "The Laws of Nature: An Infallible Justice", abbreviation = "LoN", category = "Books", canonicalOrder = 42),
        BookDescriptor("MG", "Matchless Gift", abbreviation = "MG", category = "Books", canonicalOrder = 43),
        BookDescriptor("ROP", "Reservoir of Pleasure", abbreviation = "RoP", category = "Books", canonicalOrder = 44),
        BookDescriptor("GG", "Gītār Gāna", abbreviation = "GG", category = "Scripture", canonicalOrder = 45),
        BookDescriptor("SPL", "Śrīla Prabhupāda-līlāmṛta", author = "Satsvarūpa dāsa Goswami", abbreviation = "SPL", category = "Biographies", canonicalOrder = 46),
        BookDescriptor("BTG", "Back to Godhead (1944–1960)", abbreviation = "BTG", category = "Essays & Articles", canonicalOrder = 47),
        BookDescriptor("SVA", "Songs of the Vaiṣṇava Ācāryas", author = "$PRABHUPADA_AUTHOR / Vaiṣṇava Ācāryas", abbreviation = "SVA", category = "Books", canonicalOrder = 48),
        BookDescriptor("TMG", "Temple Mantra Guide", abbreviation = "TMG", category = "Books", canonicalOrder = 49),
        // Two legitimate books that shipped in the corpus (see its own Books
        // table) but were never registered here, so they fell back to their
        // raw BookKey as a display title ("BROKENNAMES", "JAPA") and were
        // invisible to the Search screen's book-filter chips, which only
        // ever lists BookRegistry entries.
        BookDescriptor(
            "BROKENNAMES", "Broken Names: A Story of Transformations",
            author = "His Holiness Sacinandana Swami", abbreviation = "Broken Names",
            category = "Other Works", canonicalOrder = 50
        ),
        BookDescriptor(
            "JAPA", "Japa: Nine Keys from the Śikṣāṣṭaka",
            author = "His Grace Bhūrijana Dāsa", abbreviation = "Japa",
            category = "Other Works", canonicalOrder = 51
        )
    )

    private val byKey: Map<String, BookDescriptor> = canonicalBooks.associateBy { it.bookKey.uppercase() }
    private val titleFallback: Map<String, String> = canonicalBooks.associateBy({ it.bookKey.uppercase() }, { it.title })

    /** Single source of truth for the scriptural reading order of works. */
    val canonicalBookOrder: List<String> = canonicalBooks
        .sortedBy { it.canonicalOrder }
        .map { it.bookKey }
        .distinct()

    fun getBook(bookKey: String): BookDescriptor? = byKey[bookKey.uppercase()]

    fun getBookTitle(bookKey: String): String = titleFallback[bookKey.uppercase()] ?: bookKey

    fun canonicalIndexOf(bookKey: String): Int {
        val idx = canonicalBookOrder.indexOf(bookKey)
        return if (idx == -1) canonicalBookOrder.size else idx
    }
}
