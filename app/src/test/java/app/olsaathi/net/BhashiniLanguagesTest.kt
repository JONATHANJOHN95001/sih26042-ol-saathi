package app.olsaathi.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The language table behind every live call. A wrong code or service id does
 * not crash anything: the call just fails and the app quietly falls back to
 * the pack, which looks exactly like "no network". So it is pinned here.
 */
class BhashiniLanguagesTest {

    @Test
    fun everyShippedPackMapsToACodeBhashiniConfirmed() {
        listOf(
            "asm", "ben", "brx", "doi", "gom", "guj", "kan", "mai", "mal",
            "mar", "mni", "npi", "ory", "pan", "sat", "tam", "tel",
        ).forEach { assertNotNull("no Bhashini code for $it", BhashiniClient.bhashiniCode(it)) }
    }

    @Test
    fun theFourLanguagesTheTeamCanCheckMapAsProbed() {
        assertEquals("ta", BhashiniClient.bhashiniCode("tam"))
        assertEquals("ml", BhashiniClient.bhashiniCode("mal"))
        assertEquals("te", BhashiniClient.bhashiniCode("tel"))
        assertEquals("bn", BhashiniClient.bhashiniCode("ben"))
    }

    @Test
    fun santaliSpeaksThroughTheIitMadrasVoice() {
        // The MeitY handshake said Santali had no speech model. It does, on the
        // IIT Madras pipeline, given Devanagari input (see OlChikiBridge).
        assertEquals("sat", BhashiniClient.bhashiniCode("sat"))
        assertEquals("Bhashini/IITM/TTS", BhashiniClient.ttsServiceFor("sat"))
    }

    @Test
    fun speechOnlyWhereARealRequestReturnedAudio() {
        val dravidian = "ai4bharat/indic-tts-coqui-dravidian-gpu--t4"
        assertEquals(dravidian, BhashiniClient.ttsServiceFor("ta"))
        assertEquals(dravidian, BhashiniClient.ttsServiceFor("ml"))
        assertEquals(dravidian, BhashiniClient.ttsServiceFor("te"))
        assertEquals("ai4bharat/indic-tts-coqui-indo_aryan-gpu--t4", BhashiniClient.ttsServiceFor("bn"))
        assertNull(BhashiniClient.ttsServiceFor("gu"))
    }

    @Test
    fun bodoIsTheTribalLanguageWithASpeechModel() {
        // The problem statement asks for audio in a tribal language. Santali
        // has no Bhashini voice; Bodo does, and this pins it so a refactor of
        // the table cannot quietly drop the one that answers that requirement.
        assertEquals("brx", BhashiniClient.bhashiniCode("brx"))
        assertEquals("ai4bharat/indic-tts-coqui-misc-gpu--t4", BhashiniClient.ttsServiceFor("brx"))
    }

    @Test
    fun anUnknownCodeIsNullRatherThanAGuess() {
        assertNull(BhashiniClient.bhashiniCode("zzz"))
        assertNull(BhashiniClient.bhashiniCode(""))
    }

    @Test
    fun caseDoesNotMatter() {
        assertEquals("ta", BhashiniClient.bhashiniCode("TAM"))
    }
}
