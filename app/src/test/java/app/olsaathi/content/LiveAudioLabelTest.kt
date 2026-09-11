package app.olsaathi.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A live synthesised voice must never be labelled as anything sturdier than
 * it is. These are the assertions that stop that drifting.
 */
class LiveAudioLabelTest {

    private fun translation(
        audio: AudioProvenance,
        service: String = "",
    ) = Translation(
        source = "नमस्ते",
        target = "ᱡᱚᱦᱟᱨ",
        en = "hello",
        provenance = Provenance.ONLINE_MACHINE,
        serviceName = service,
        audioProvenance = audio,
    )

    @Test
    fun liveVoiceNamesTheServiceThatSpokeIt() {
        assertEquals(
            "Machine voice, live · Bhashini",
            translation(AudioProvenance.LIVE_TTS, "Bhashini").audioProvenanceLabel
        )
    }

    @Test
    fun liveVoiceWithNoNamedServiceStillSaysItIsLive() {
        assertEquals(
            "Machine voice, live",
            translation(AudioProvenance.LIVE_TTS).audioProvenanceLabel
        )
    }

    @Test
    fun buildTimeLabelsAreNotGivenASecondServiceName() {
        // These already name their service in the enum. Appending would either
        // repeat a name or, for a human recording, attach a vendor to a person.
        assertEquals(
            AudioProvenance.PARLER_TTS.label,
            translation(AudioProvenance.PARLER_TTS, "Bhashini").audioProvenanceLabel
        )
        assertEquals(
            AudioProvenance.SPOKEN_BY_NATIVE.label,
            translation(AudioProvenance.SPOKEN_BY_NATIVE, "Bhashini").audioProvenanceLabel
        )
    }

    @Test
    fun liveVoiceIsNeverConfusedWithShippedSynthesis() {
        // Separate states on purpose: one was generated under conditions
        // somebody chose at build time, the other appeared seconds ago from a
        // translation that had no build-time script checking at all.
        assertNotEquals(AudioProvenance.LIVE_TTS.label, AudioProvenance.BHASHINI_TTS.label)
        assertNotEquals(AudioProvenance.LIVE_TTS.label, AudioProvenance.PARLER_TTS.label)
    }

    @Test
    fun aLiveLineNeverClaimsToBeVerified() {
        val t = translation(AudioProvenance.LIVE_TTS, "Bhashini")
        assert(!t.audioProvenanceLabel.contains("verified", ignoreCase = true))
        assert(!t.provenanceLabel.contains("verified", ignoreCase = true))
        assert(!t.isTrustworthy)
    }
}
