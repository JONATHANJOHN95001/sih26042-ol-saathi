package app.olsaathi.content

/**
 * Every piece of tribal-language text the app displays is wrapped in a
 * Translation that carries a Provenance. The UI always shows the
 * provenance label so the user (and a judge) can tell at a glance
 * whether the output is a real translation or not.
 *
 * This is N1: never invent output. A lookup miss returns UNAVAILABLE
 * with an empty string. There is no fallback phrase, no guess, no
 * "close enough" match.
 */
/**
 * Where a piece of Santali audio came from.
 *
 * Audio needs its own provenance because the two can disagree: a line can be
 * machine translated and then read aloud by a native speaker, which makes the
 * audio the more trustworthy of the two. The UI shows whichever label applies
 * to the thing the user is about to hear.
 */
enum class AudioProvenance(val label: String) {
    /** Read aloud by a Santali speaker and recorded. The strongest claim. */
    SPOKEN_BY_NATIVE("Recorded by a native speaker"),

    /** Synthesised by Bhashini's Santali TTS. */
    BHASHINI_TTS("Synthesised speech · Bhashini"),

    /**
     * Synthesised by AI4Bharat's Indic Parler-TTS, run locally at build time.
     *
     * The label says "unchecked" because no Santali speaker has heard these
     * clips. Santali is one of twenty-one languages in a 0.9B model, and a
     * fluent-sounding voice reading an unverified line is the most convincing
     * way to break the rule this app is built on. Once a speaker confirms a
     * clip it should be re-labelled, not quietly promoted.
     */
    PARLER_TTS("Synthesised speech · Indic Parler-TTS, unchecked"),

    /**
     * Synthesised over the network, just now, for a sentence that was not in
     * the pack.
     *
     * Deliberately its own state rather than reusing [BHASHINI_TTS]. That one
     * describes a clip generated at build time and shipped inside the APK,
     * which was at least produced under conditions somebody chose. This is a
     * clip that appeared seconds ago, from a translation that itself has had
     * none of the build-time script checking. Collapsing the two would let a
     * label claim more than it can support.
     *
     * The service name is appended at display time by
     * [Translation.audioProvenanceLabel] rather than baked in here.
     */
    LIVE_TTS("Machine voice, live"),

    /**
     * Spoken by the device's own text-to-speech engine, on device and with no
     * network.
     *
     * The best-available source for the languages Android happens to cover,
     * and the reason voice to voice can be demonstrated at all before any key
     * arrives. It is still a machine reading a machine translation that no
     * speaker has checked, and it sounds fluent, which makes it more
     * persuasive than it has earned. Hence "unchecked" in the label: fluency
     * is not accuracy.
     */
    DEVICE_TTS("Device voice, unchecked"),

    /** No audio for this entry. The play button is disabled. */
    NONE("No audio yet")
}

enum class Provenance(val label: String) {
    /**
     * Checked by a Santali speaker. The strongest claim we make.
     *
     * Per-entry only: one reviewer checking forty entries does not verify
     * the other thirteen. The reviewer's name and date appear on screen.
     */
    HUMAN_VERIFIED("Checked by a native speaker"),

    /**
     * From the shipped pack, traceable to the model that produced it.
     *
     * The label deliberately does NOT say "verified". Nothing in this pack has
     * been checked by a Santali speaker, and a teacher who cannot read Ol Chiki
     * would take the word "verified" at face value. What we can honestly claim
     * is the source, so that is what the label states.
     *
     * It names no service, because an enum constant cannot know which one
     * produced the pack it is describing. This read "Machine translation ·
     * IndicTrans2" until the service name moved into the data, and it would
     * have gone on saying IndicTrans2 after a Bhashini run replaced every
     * string in the pack. The name is appended at display time from the pack's
     * own provenance block: see [Translation.provenanceLabel].
     */
    VERIFIED("Machine translation"),

    /**
     * Translated live over the network, not from the shipped pack.
     *
     * Kept apart from [VERIFIED] because the difference is real. Pack strings
     * went through the per-script contamination guard at build time; this one
     * arrived seconds ago and has had none of that. Both are machine
     * translation, but only one has been checked for the failure modes that
     * guard exists to catch, so a teacher should be able to see which is which.
     */
    ONLINE_MACHINE("Machine translation, live"),

    /** Hindi text respelled in the target script — not a translation. */
    TRANSLITERATED("Transliterated, not a translation"),

    /** No match in the pack. Empty target string. */
    UNAVAILABLE("Not in the offline pack"),

    /**
     * The pack is placeholder data, not Bhashini output.
     *
     * Sample text is the most dangerous thing this app can hold, because it
     * is valid Ol Chiki, it passes every content test, and it looks exactly
     * like a real translation to anyone who cannot read Santali. It must
     * never be shown under the VERIFIED label.
     */
    SAMPLE("SAMPLE DATA — not a real translation")
}

data class Translation(
    val source: String,
    val target: String,
    val en: String,
    val provenance: Provenance,
    val serviceId: String = "",
    /**
     * Display name of whoever produced this line, read out of the pack's
     * provenance block rather than compiled in. Empty when the pack does not
     * say, in which case the label omits it rather than guessing.
     */
    val serviceName: String = "",
    val entryId: String = "",
    val nipun: String = "",
    /**
     * Verbatim text of the NIPUN Bharat outcome named by [nipun], so a printed
     * sheet can carry the outcome itself rather than a bare code a reader has
     * no way to look up. Empty when the pack does not supply one.
     */
    val nipunOutcome: String = "",
    /** NIPUN Bharat developmental goal, e.g. "Children become effective communicators". */
    val nipunGoal: String = "",
    /** NIPUN Bharat foundational domain, e.g. "Oral Language Development". */
    val nipunDomain: String = "",
    val kind: String = "",
    /** Asset path of the spoken form, null when the pack has no audio for it. */
    val audioAsset: String? = null,
    val audioProvenance: AudioProvenance = AudioProvenance.NONE,
    /** Reviewer name, set only when provenance is HUMAN_VERIFIED. */
    val reviewerName: String = "",
    /** Review date (ISO), set only when provenance is HUMAN_VERIFIED. */
    val reviewedOn: String = "",
    val bridge: String = "",
    val bridgeProvenance: String = "",
    /**
     * Who produced the audio, when that differs from who produced the text.
     * A pack line translated by IndicTrans2 and spoken live by Bhashini has
     * two sources, and the audio label must name the second.
     */
    val audioServiceName: String = "",
) {
    /** True when the pack had an entry for this source. */
    val isAvailable: Boolean get() =
        provenance == Provenance.VERIFIED ||
            provenance == Provenance.SAMPLE ||
            provenance == Provenance.HUMAN_VERIFIED

    /** True for real Bhashini output or human-reviewed content. */
    val isTrustworthy: Boolean get() =
        provenance == Provenance.VERIFIED || provenance == Provenance.HUMAN_VERIFIED

    /** True when there is something to play. Drives the play button's enabled state. */
    val hasAudio: Boolean get() =
        audioAsset != null && audioProvenance != AudioProvenance.NONE

    /**
     * The provenance string actually put on screen.
     *
     * Only VERIFIED takes a service name. HUMAN_VERIFIED is a claim about a
     * person rather than a model, and SAMPLE and UNAVAILABLE must keep saying
     * exactly what they say now.
     */
    val provenanceLabel: String get() =
        if ((provenance == Provenance.VERIFIED ||
                provenance == Provenance.ONLINE_MACHINE) && serviceName.isNotEmpty())
            "${provenance.label} · $serviceName"
        else
            provenance.label

    /**
     * The audio provenance string actually put on screen, beside the play
     * button.
     *
     * Only [AudioProvenance.LIVE_TTS] takes a service name, and only when the
     * pack or the router supplied one. The build-time labels already name
     * their service in the enum, and a person's recording is not a service at
     * all. Appending to those would either repeat a name or attach a vendor to
     * a human being.
     */
    val audioProvenanceLabel: String get() {
        val voice = audioServiceName.ifEmpty { serviceName }
        return if (audioProvenance == AudioProvenance.LIVE_TTS && voice.isNotEmpty())
            "${audioProvenance.label} · $voice"
        else
            audioProvenance.label
    }
}
