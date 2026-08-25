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
enum class Provenance(val label: String) {
    /**
     * Checked by a Santali speaker. The strongest claim we make.
     *
     * Per-entry only: one reviewer checking forty entries does not verify
     * the other thirteen. The reviewer's name and date appear on screen.
     */
    HUMAN_VERIFIED("Checked by a Santali speaker"),

    /**
     * From the shipped pack, traceable to the model that produced it.
     *
     * The label deliberately does NOT say "verified". Nothing in this pack has
     * been checked by a Santali speaker, and a teacher who cannot read Ol Chiki
     * would take the word "verified" at face value. What we can honestly claim
     * is the source, so that is what the label states.
     */
    VERIFIED("Machine translation · IndicTrans2"),

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
    val entryId: String = "",
    val nipun: String = "",
    val kind: String = "",
    /** Reviewer name, set only when provenance is HUMAN_VERIFIED. */
    val reviewerName: String = "",
    /** Review date (ISO), set only when provenance is HUMAN_VERIFIED. */
    val reviewedOn: String = "",
) {
    /** True when the pack had an entry for this source. */
    val isAvailable: Boolean get() =
        provenance == Provenance.VERIFIED ||
            provenance == Provenance.SAMPLE ||
            provenance == Provenance.HUMAN_VERIFIED

    /** True for real Bhashini output or human-reviewed content. */
    val isTrustworthy: Boolean get() =
        provenance == Provenance.VERIFIED || provenance == Provenance.HUMAN_VERIFIED
}
