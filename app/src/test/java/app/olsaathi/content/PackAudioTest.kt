package app.olsaathi.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for audio lookup and its provenance.
 *
 * These exist because of a specific bug. `audioPath()` looked an entry up by
 * its id, but the entry map is keyed by source text, so it returned null for
 * every entry that had audio. The play buttons in the Teach screen and the
 * lesson player were both wired correctly and could never have enabled. It
 * would have looked exactly like "we have no audio yet", which happened to be
 * true, so nothing contradicted it.
 *
 * The lesson generalises: a lookup keyed differently from how it is queried
 * fails silently and looks like missing data. These tests query by id and by
 * source text and assert both agree.
 */
class PackAudioTest {

    private val packJson = """
        {
          "generated": "2026-08-26T00:00:00Z",
          "provenance": {
            "translationService": "prajdabre/rotary-indictrans2-en-indic-1B",
            "platform": "AI4Bharat IndicTrans2"
          },
          "entries": {
            "p01": {
              "source": "नमस्ते बच्चों।",
              "target": "ᱦᱚᱞᱳ ᱜᱤᱫᱽᱨᱟᱹᱠᱚ ᱾",
              "en": "Hello children.",
              "nipun": "EC-CR-G1",
              "nipunGoal": "Children become effective communicators",
              "nipunDomain": "Classroom Routine",
              "kind": "phrase",
              "service": "prajdabre/rotary-indictrans2-en-indic-1B",
              "audio": "pack/audio/p01.wav",
              "audioProvenance": "native"
            },
            "p02": {
              "source": "सब बैठ जाओ।",
              "target": "ᱡᱚᱛᱚ ᱦᱚᱲ ᱠᱚ ᱫᱩᱲᱩᱵ ᱮᱱᱟ ᱾",
              "en": "Everyone sit down.",
              "nipun": "EC-CR-G1",
              "nipunDomain": "Classroom Routine",
              "kind": "phrase",
              "service": "prajdabre/rotary-indictrans2-en-indic-1B"
            },
            "p03": {
              "source": "किताब खोलो।",
              "target": "ᱯᱩᱛᱷᱤ ᱡᱷᱤᱡ ᱢᱮ ᱾",
              "en": "Open your book.",
              "nipun": "EC-CR-G1",
              "nipunDomain": "Classroom Routine",
              "kind": "phrase",
              "service": "prajdabre/rotary-indictrans2-en-indic-1B",
              "audioProvenance": "native"
            }
          }
        }
    """.trimIndent()

    private val pack = VerifiedContentPack.loadFromString(packJson)

    @Test
    fun `audioPath resolves for an entry that has audio`() {
        val t = pack.lookup("नमस्ते बच्चों।")
        assertEquals("p01", t.entryId)
        // The regression: this returned null because the id was looked up
        // against a map keyed by source text.
        assertEquals("pack/audio/p01.wav", pack.audioPath(t))
    }

    @Test
    fun `audioPath is null for an entry with no audio`() {
        val t = pack.lookup("सब बैठ जाओ।")
        assertNull(pack.audioPath(t))
    }

    @Test
    fun `a native recording is labelled as spoken by a person`() {
        val t = pack.lookup("नमस्ते बच्चों।")
        assertEquals(AudioProvenance.SPOKEN_BY_NATIVE, t.audioProvenance)
        assertTrue(t.hasAudio)
    }

    @Test
    fun `an entry without audio reports none and disables the button`() {
        val t = pack.lookup("सब बैठ जाओ।")
        assertEquals(AudioProvenance.NONE, t.audioProvenance)
        assertFalse(t.hasAudio)
    }

    /**
     * A provenance label with no file behind it must not enable playback.
     *
     * This is the audio version of the rule the whole project runs on: the app
     * never claims something it cannot produce. A stale "native" label left on
     * an entry whose file was removed would otherwise light up a play button
     * that does nothing.
     */
    @Test
    fun `a stale provenance label with no file still reports none`() {
        val t = pack.lookup("किताब खोलो।")
        assertEquals(AudioProvenance.NONE, t.audioProvenance)
        assertFalse(t.hasAudio)
        assertNull(pack.audioPath(t))
    }

    /**
     * A pack written with explicit JSON nulls must behave like one that omits
     * the keys.
     *
     * This is the bug this test file exists for the second time. org.json's
     * optString returns the string "null" for a JSON null rather than the
     * default, so {"audio": null} produced audio == "null", the proof screen
     * reported 53 of 53 entries had a WAV when none did, and playback would
     * have been handed an asset named "null". The first version of these tests
     * missed it because the fixture omitted the key instead of nulling it,
     * which is the tidier thing to write and the wrong thing to test.
     */
    @Test
    fun `explicit JSON nulls are treated as absent`() {
        val withNulls = VerifiedContentPack.loadFromString("""
            {
              "generated": "2026-08-26T00:00:00Z",
              "provenance": {
                "translationService": "prajdabre/rotary-indictrans2-en-indic-1B",
                "platform": "AI4Bharat IndicTrans2"
              },
              "entries": {
                "n01": {
                  "source": "नमस्ते बच्चों।",
                  "target": "ᱦᱚᱞᱳ ᱜᱤᱫᱽᱨᱟᱹᱠᱚ ᱾",
                  "en": "Hello children.",
                  "nipun": "EC-CR-G1",
                  "kind": "phrase",
                  "service": "prajdabre/rotary-indictrans2-en-indic-1B",
                  "audio": null,
                  "audioProvenance": null,
                  "lesson": null
                }
              }
            }
        """.trimIndent())

        val t = withNulls.lookup("नमस्ते बच्चों।")
        assertNull(withNulls.audioPath(t))
        assertEquals(AudioProvenance.NONE, t.audioProvenance)
        assertFalse(t.hasAudio)
        assertEquals(0, withNulls.audioCount)
    }

    @Test
    fun `audioCount counts only entries with a file`() {
        assertEquals(1, pack.audioCount)
    }

    @Test
    fun `NIPUN goal and domain reach the translation`() {
        val t = pack.lookup("नमस्ते बच्चों।")
        assertEquals("EC-CR-G1", t.nipun)
        assertEquals("Classroom Routine", t.nipunDomain)
        assertEquals("Children become effective communicators", t.nipunGoal)
    }

    /** A miss must stay a miss. No audio, no text, no guess. */
    @Test
    fun `an unknown phrase has no audio and no target`() {
        val t = pack.lookup("यह वाक्य पैक में नहीं है।")
        assertEquals(Provenance.UNAVAILABLE, t.provenance)
        assertEquals("", t.target)
        assertFalse(t.hasAudio)
        assertNull(pack.audioPath(t))
    }
}
