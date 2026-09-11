package app.olsaathi.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * The pack uses three-letter codes and Android wants the two-letter tag for
 * most of these. Getting one wrong does not crash: the engine simply reports
 * the language unavailable, the play button stays dark, and it looks exactly
 * like a device with no voice installed. That is a bug that hides itself, so
 * it is pinned here instead.
 */
class TargetVoiceLocaleTest {

    @Test
    fun theFourLanguagesWeCanActuallyCheckAreMapped() {
        // These four are the ones the team can verify by reading, so they are
        // the ones a demo will use.
        assertEquals(Locale("ta", "IN"), TargetVoice.localeFor("tam"))
        assertEquals(Locale("ml", "IN"), TargetVoice.localeFor("mal"))
        assertEquals(Locale("te", "IN"), TargetVoice.localeFor("tel"))
        assertEquals(Locale("bn", "IN"), TargetVoice.localeFor("ben"))
    }

    @Test
    fun everyShippedPackCodeHasALocaleToAskAbout() {
        val shipped = listOf(
            "asm", "ben", "brx", "doi", "gom", "guj", "kan", "mai",
            "mal", "mar", "mni", "npi", "ory", "pan", "sat", "tam", "tel",
        )
        shipped.forEach { code ->
            assertNotNull("no locale mapped for pack code $code", TargetVoice.localeFor(code))
        }
    }

    @Test
    fun santaliIsMappedEvenThoughNoDeviceSpeaksIt() {
        // Mapping is not a claim that a voice exists. The device is asked and
        // will say no, which is the case the whole app is built around.
        assertEquals(Locale("sat", "IN"), TargetVoice.localeFor("sat"))
    }

    @Test
    fun caseDoesNotMatter() {
        assertEquals(TargetVoice.localeFor("tam"), TargetVoice.localeFor("TAM"))
    }

    @Test
    fun anUnknownCodeIsNullRatherThanAGuess() {
        assertNull(TargetVoice.localeFor("zzz"))
        assertNull(TargetVoice.localeFor(""))
    }
}
