package com.prabhupadaconnect.vedabase.data.corpus

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [ChapterTitleDeriver]'s use of [CanonicalChapterTitles] for Śrīmad-
 * Bhāgavatam and Caitanya-caritāmṛta - the two books that used to fall back
 * to a bare "Canto C Chapter N" / "Chapter N" for every chapter before the
 * ~300-entry desktop lookup table was ported.
 */
class ChapterTitleDeriverTest {

    @Test
    fun `SB chapter with a known canonical title includes it`() {
        assertEquals(
            "Canto 1 Chapter 1: Questions by the Sages",
            ChapterTitleDeriver.derive("SB", "SB 1.1", null)
        )
        assertEquals(
            "Canto 10 Chapter 1: The Advent of Lord Kṛṣṇa: Introduction",
            ChapterTitleDeriver.derive("SB", "10.1.1", null)
        )
    }

    @Test
    fun `SB chapter intentionally absent from the source RTF falls back to a bare label`() {
        // SB 10.87 has no extractable label in the source RTF, per CanonicalChapterTitles's own docs.
        assertEquals(
            "Canto 10 Chapter 87",
            ChapterTitleDeriver.derive("SB", "SB 10.87.1", null)
        )
        assertEquals(
            "Canto 11 Chapter 28",
            ChapterTitleDeriver.derive("SB", "SB 11.28.1", null)
        )
    }

    @Test
    fun `CC Adi chapter includes its canonical title`() {
        assertEquals(
            "Chapter 1: The Spiritual Masters",
            ChapterTitleDeriver.derive("DI", "Adi 1", null)
        )
    }

    @Test
    fun `CC Madhya chapter includes its canonical title`() {
        assertEquals(
            "Chapter 24: The Sixty-One Explanations of the Ātmārāma Verse",
            ChapterTitleDeriver.derive("MADHYA", "Madhya 24", null)
        )
    }

    @Test
    fun `CC Antya chapter includes its canonical title`() {
        assertEquals(
            "Chapter 20: The Śikṣāṣṭaka Prayers",
            ChapterTitleDeriver.derive("ANTYA", "Antya 20", null)
        )
    }

    @Test
    fun `CC chapters from different lilas with the same chapter number resolve independently`() {
        assertEquals("Chapter 1: The Spiritual Masters", ChapterTitleDeriver.derive("DI", "Adi 1", null))
        assertEquals(
            "Chapter 1: The Later Pastimes of Lord Śrī Caitanya Mahāprabhu",
            ChapterTitleDeriver.derive("MADHYA", "Madhya 1", null)
        )
        assertEquals(
            "Chapter 1: Śrīla Rūpa Gosvāmī's Second Meeting With the Lord",
            ChapterTitleDeriver.derive("ANTYA", "Antya 1", null)
        )
    }

    @Test
    fun `BG canonical titles are unaffected by the SB and CC lookup wiring`() {
        assertEquals(
            "Chapter 2: Contents of the Gītā Summarized",
            ChapterTitleDeriver.derive("BG", "Bg 2.13", null)
        )
    }
}
