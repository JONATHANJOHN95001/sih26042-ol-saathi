package app.olsaathi.content

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/**
 * Unit tests over the shipped pack, not over mocks.
 *
 * These run against the real pack.sat.json that ships inside the APK.
 * Every assertion validates a non-negotiable constraint.
 */
class VerifiedContentPackTest {

    companion object {
        private lateinit var pack: VerifiedContentPack
        private lateinit var rawJson: String

        @BeforeClass
        @JvmStatic
        fun loadPack() {
            val path = "app/src/main/assets/pack/pack.sat.json"
            val file = java.io.File(path)
            assertTrue("pack.sat.json must exist at $path", file.exists())
            rawJson = file.readText(Charsets.UTF_8)
            pack = VerifiedContentPack.loadFromString(rawJson)
            assertTrue("Pack must have entries", pack.size > 0)
        }
    }

    // ── N1: normalise strips danda and collapses whitespace ──────────

    @Test
    fun normalise_strips_trailing_danda() {
        val result = VerifiedContentPack.normalise("नमस्ते बच्चों।")
        assertEquals("नमस्ते बच्चों", result)
    }

    @Test
    fun normalise_collapses_whitespace() {
        val result = VerifiedContentPack.normalise("  नमस्ते   बच्चों  ")
        assertEquals("नमस्ते बच्चों", result)
    }

    @Test
    fun normalise_strips_trailing_question_mark() {
        val result = VerifiedContentPack.normalise("क्या तुम ठीक हो?")
        assertEquals("क्या तुम ठीक हो", result)
    }

    @Test
    fun normalise_strips_trailing_exclamation() {
        val result = VerifiedContentPack.normalise("बहुत अच्छा!")
        assertEquals("बहुत अच्छा", result)
    }

    @Test
    fun normalise_strips_trailing_period() {
        val result = VerifiedContentPack.normalise("सही जवाब।")
        assertEquals("सही जवाब", result)
    }

    // ── N1: lookup miss returns UNAVAILABLE with empty text ──────────

    @Test
    fun lookup_miss_returns_unavailable() {
        val result = pack.lookup("यह बिल्कुल अजीब वाक्य है जो पैक में नहीं है")
        assertEquals(Provenance.UNAVAILABLE, result.provenance)
        assertEquals("", result.target)
        assertEquals("यह बिल्कुल अजीब वाक्य है जो पैक में नहीं है", result.source)
    }

    @Test
    fun lookup_empty_returns_unavailable() {
        val result = pack.lookup("")
        assertEquals(Provenance.UNAVAILABLE, result.provenance)
        assertEquals("", result.target)
    }

    @Test
    fun lookup_whitespace_only_returns_unavailable() {
        val result = pack.lookup("   ")
        assertEquals(Provenance.UNAVAILABLE, result.provenance)
        assertEquals("", result.target)
    }

    // ── N1: lookup hit returns VERIFIED ──────────────────────────────

    @Test
    fun lookup_exact_match_returns_a_hit() {
        val result = pack.lookup("नमस्ते बच्चों।")
        // VERIFIED once a real Bhashini pack is built, SAMPLE until then.
        // What must never happen is a hit reporting UNAVAILABLE, or sample
        // content reporting VERIFIED.
        val expected = if (pack.isSample) Provenance.SAMPLE else Provenance.VERIFIED
        assertEquals(expected, result.provenance)
        assertTrue("Target must not be empty", result.target.isNotEmpty())
    }

    @Test
    fun lookup_normalised_match_returns_a_hit() {
        // Input without trailing danda, should still match via normalised index
        val result = pack.lookup("नमस्ते बच्चों")
        val expected = if (pack.isSample) Provenance.SAMPLE else Provenance.VERIFIED
        assertEquals(expected, result.provenance)
        assertTrue("Target must not be empty", result.target.isNotEmpty())
    }

    // ── Ol Chiki validation ──────────────────────────────────────────

    @Test
    fun no_target_contains_ol_chiki_digits() {
        // U+1C50..U+1C59 are Ol Chiki digits. The old dictionary emitted them mid-word.
        val digitRange = 0x1C50..0x1C59
        for ((id, entry) in pack.entries) {
            for (cp in entry.target.codePoints()) {
                assertFalse(
                    "Entry $id target contains Ol Chiki digit U+${cp.toString(16).uppercase()}: '${entry.target}'",
                    cp in digitRange
                )
            }
        }
    }

    @Test
    fun every_target_contains_ol_chiki_letter() {
        // U+1C5A..U+1C77 are Ol Chiki letters. Every target must have at least one.
        val letterRange = 0x1C5A..0x1C77
        for ((id, entry) in pack.entries) {
            assertTrue(
                "Entry $id target has no Ol Chiki letters: '${entry.target}'",
                entry.target.codePoints().anyMatch { it in letterRange }
            )
        }
    }

    @Test
    fun no_target_contains_devanagari() {
        // Devanagari range: U+0900..U+097F. Target must not contain Devanagari.
        val devaRange = 0x0900..0x097F
        for ((id, entry) in pack.entries) {
            val hasDeva = entry.target.codePoints().anyMatch { it in devaRange }
            assertFalse(
                "Entry $id target contains Devanagari: '${entry.target}'",
                hasDeva
            )
        }
    }

    // ── Collision detection ──────────────────────────────────────────

    @Test
    fun no_two_distinct_sources_map_to_same_target() {
        val seen = mutableMapOf<String, String>() // target -> source
        for ((id, entry) in pack.entries) {
            if (entry.target.isEmpty()) continue
            val prev = seen[entry.target]
            if (prev != null && prev != entry.source) {
                fail(
                    "Collision: '$prev' and '${entry.source}' both map to '${entry.target}'"
                )
            }
            seen[entry.target] = entry.source
        }
    }

    // ── Provenance ───────────────────────────────────────────────────

    @Test
    fun provenance_translation_service_is_non_empty() {
        // Every VERIFIED entry must have a non-empty service ID
        for ((id, entry) in pack.entries) {
            if (entry.target.isNotEmpty()) {
                assertTrue(
                    "Entry $id has empty translation service",
                    entry.service.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun pack_has_provenance_metadata() {
        assertTrue("translationService must be non-empty", pack.translationService.isNotEmpty())
        assertTrue("platform must be non-empty", pack.platform.isNotEmpty())
    }

    // ── Latency ──────────────────────────────────────────────────────

    @Test
    fun one_thousand_lookups_under_100ms() {
        val queries = listOf(
            "नमस्ते बच्चों।", "सब बैठ जाओ।", "आज हम क्या सीखेंगे?",
            "यह क्या है?", "एक से दस तक गिनो।", "किताब खोलो।",
            "बहुत अच्छा!", "ध्यान से सुनो।", "हाथ उठाओ।",
            "घर पर अभ्यास करना।"
        )

        val startMs = System.currentTimeMillis()
        for (i in 1..1000) {
            pack.lookup(queries[i % queries.size])
        }
        val elapsed = System.currentTimeMillis() - startMs

        assertTrue(
            "1000 lookups took ${elapsed}ms, must be under 100ms",
            elapsed < 100
        )
    }

    // ── Pack integrity ───────────────────────────────────────────────

    @Test
    fun pack_has_entries() {
        assertTrue("Pack must have entries", pack.size > 0)
    }

    @Test
    fun lesson_entries_have_lesson_field() {
        for ((id, entry) in pack.entries) {
            if (entry.kind == "lesson" || entry.kind == "check") {
                assertNotNull("Entry $id (kind=${entry.kind}) must have lesson field", entry.lesson)
                assertTrue("Entry $id lesson field must not be empty", entry.lesson!!.isNotEmpty())
            }
        }
    }

    @Test
    fun every_entry_has_nipun() {
        for ((id, entry) in pack.entries) {
            assertTrue("Entry $id must have nipun", entry.nipun.isNotEmpty())
        }
    }

    // ── The sample-data guard ────────────────────────────────────────

    @Test
    fun sample_pack_never_reports_verified() {
        val hit = pack.lookup("नमस्ते बच्चों।")
        if (pack.isSample) {
            assertEquals(
                "A sample pack must never claim VERIFIED. Invented Santali that is " +
                    "labelled verified is the worst output this app can produce.",
                Provenance.SAMPLE, hit.provenance
            )
            assertFalse("Sample content is not trustworthy", hit.isTrustworthy)
        } else {
            assertEquals(Provenance.VERIFIED, hit.provenance)
            assertTrue(hit.isTrustworthy)
        }
    }

    @Test
    fun placeholder_service_id_is_treated_as_sample() {
        if (!pack.isSample) {
            assertTrue(
                "translationService '${pack.translationService}' looks like a placeholder. " +
                    "Run tools/build_pack.mjs with real credentials.",
                pack.translationService.count { it == '/' } >= 1 &&
                    pack.translationService != "ai4bharat/translation"
            )
        }
    }
}
