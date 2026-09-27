package com.prabhupadaconnect.vedabase.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongPayloadParserTest {

    // A trimmed-down shape matching a real TMG record (TMG-1, "Putting on Tilaka").
    private val realSongJson = """
        {"type": "song", "bannerTitle": "Putting on Tilaka", "subtitle": "", "intro": "Tilaka is sometimes called the ornamentation of the spirit soul.",
        "stanzas": [{"label": "", "lines": ["lalāṭe keśavaṁ dhyāyen nārāyaṇam athodare", "vakṣaḥ-sthale mādhavaṁ tu govindaṁ kaṇṭha-kūpake"], "synonyms": "", "translation": "When one marks the forehead with tilaka, he must remember Keśava."}],
        "notes": "In accordance with the above mentioned mantra..."}
    """.trimIndent()

    @Test
    fun `parses a real song payload into structured stanzas`() {
        val payload = SongPayloadParser.tryParse(realSongJson)

        requireNotNull(payload)
        assertEquals("song", payload.type)
        assertEquals("Putting on Tilaka", payload.bannerTitle)
        assertEquals(1, payload.stanzas.size)
        assertEquals(2, payload.stanzas[0].lines.size)
        assertTrue(payload.stanzas[0].translation.startsWith("When one marks the forehead"))
        assertTrue(payload.notes.startsWith("In accordance"))
    }

    @Test
    fun `parses a multi-stanza payload with labeled texts`() {
        val json = """
            {"type": "song", "bannerTitle": "Śrī Śrī Gurv-aṣṭaka", "subtitle": "Eight Prayers to the Guru",
            "intro": "", "stanzas": [
              {"label": "Text One", "lines": ["saṁsāra-dāvānala-līḍha-loka-"], "synonyms": "saṁsāra—material existence", "translation": "The spiritual master is receiving benediction."},
              {"label": "Text Two", "lines": ["mahāprabhoḥ kīrtana-nṛtya-gīta-"], "synonyms": "", "translation": "Chanting the holy name."}
            ], "notes": ""}
        """.trimIndent()

        val payload = SongPayloadParser.tryParse(json)

        requireNotNull(payload)
        assertEquals(2, payload.stanzas.size)
        assertEquals("Text One", payload.stanzas[0].label)
        assertEquals("Text Two", payload.stanzas[1].label)
    }

    @Test
    fun `plain prose text is not mistaken for a song payload`() {
        assertNull(SongPayloadParser.tryParse("sañjaya uvāca\ndṛṣṭvā tu pāṇḍavānīkaṁ"))
    }

    @Test
    fun `blank and null input return null`() {
        assertNull(SongPayloadParser.tryParse(null))
        assertNull(SongPayloadParser.tryParse(""))
        assertNull(SongPayloadParser.tryParse("   "))
    }

    @Test
    fun `JSON of an unrelated shape returns null rather than throwing`() {
        assertNull(SongPayloadParser.tryParse("""{"type": "somethingElse", "foo": "bar"}"""))
        assertNull(SongPayloadParser.tryParse("""{"notype": true}"""))
        assertNull(SongPayloadParser.tryParse("""{ not valid json at all"""))
    }

    @Test
    fun `a mantra-typed payload is also recognized`() {
        val json = """{"type": "mantra", "bannerTitle": "Hare Kṛṣṇa Mahā-mantra", "subtitle": "", "intro": "", "stanzas": [], "notes": ""}"""
        val payload = SongPayloadParser.tryParse(json)
        requireNotNull(payload)
        assertEquals("mantra", payload.type)
    }
}
