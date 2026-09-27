package com.prabhupadaconnect.vedabase.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [DevanagariNormalizer], ported 1:1 from the desktop app's
 * `DevanagariNormalizer` (C#) - same structural regex rules, same
 * fixed-point iteration. Each case below targets exactly one of the eight
 * documented rules using isolated, minimal input.
 */
class DevanagariNormalizerTest {

    @Test
    fun `blank and null input pass through unchanged`() {
        assertEquals("", DevanagariNormalizer.normalize(null))
        assertEquals("", DevanagariNormalizer.normalize(""))
    }

    @Test
    fun `composes a split O vowel sign from aa plus e`() {
        // क + ा (u093E) + े (u0947)  ->  क + ो (u094B)
        assertEquals("को", DevanagariNormalizer.normalize("काे"))
    }

    @Test
    fun `composes a split AU vowel sign from aa plus ai`() {
        // क + ा (u093E) + ै (u0948)  ->  क + ौ (u094C)
        assertEquals("कौ", DevanagariNormalizer.normalize("काै"))
    }

    @Test
    fun `resolves the Indevr ku ligature hyphen artifact`() {
        assertEquals("कु", DevanagariNormalizer.normalize("क्ु-"))
    }

    @Test
    fun `resolves the Indevr kr ligature hyphen artifact`() {
        assertEquals("कृ", DevanagariNormalizer.normalize("क्ृ-"))
    }

    @Test
    fun `spurious ha virama before a non-conjunct consonant is dropped`() {
        // "hat" - ha is not conjunct with "ta", so the virama is spurious.
        assertEquals("हत", DevanagariNormalizer.normalize("ह्त"))
    }

    @Test
    fun `ha virama before a true h-conjunct consonant is preserved`() {
        // "hma" - ma is one of the valid h-conjunct onsets [म य ल व ण र].
        assertEquals("ह्म", DevanagariNormalizer.normalize("ह्म"))
    }

    @Test
    fun `spurious ha virama at end of string is dropped`() {
        assertEquals("ह", DevanagariNormalizer.normalize("ह्"))
    }

    @Test
    fun `virama immediately before a vowel matra is removed`() {
        assertEquals("का", DevanagariNormalizer.normalize("क्ा"))
    }

    @Test
    fun `virama immediately before anusvara is removed`() {
        assertEquals("कं", DevanagariNormalizer.normalize("क्ं"))
    }

    @Test
    fun `duplicate viramas collapse to one`() {
        assertEquals("क्त", DevanagariNormalizer.normalize("क््त"))
    }

    @Test
    fun `double close-paren dandas become a single double-danda`() {
        assertEquals("॥", DevanagariNormalizer.normalize("))"))
    }

    @Test
    fun `verse-end dandas wrapping a number become a spaced double-danda pair`() {
        assertEquals("॥ 27 ॥", DevanagariNormalizer.normalize(")) 27 ))"))
    }

    @Test
    fun `normalization is idempotent on already-normalized text`() {
        val once = DevanagariNormalizer.normalize("क्ु-ह्तकं॥")
        val twice = DevanagariNormalizer.normalize(once)
        assertEquals(once, twice)
    }

    @Test
    fun `plain ASCII text with no Devanagari is left untouched`() {
        assertEquals("Bg 2.13", DevanagariNormalizer.normalize("Bg 2.13"))
    }
}
