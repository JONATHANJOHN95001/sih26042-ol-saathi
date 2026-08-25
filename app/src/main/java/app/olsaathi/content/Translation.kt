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
    /** Real translation from the Bhashini pack. */
    VERIFIED("Verified translation"),
    /** Hindi text respelled in the target script — not a translation. */
    TRANSLITERATED("Transliterated, not a translation"),
    /** No match in the pack. Empty target string. */
    UNAVAILABLE("No verified translation offline"),

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
) {
    /** True when the pack had an entry for this source. */
    val isAvailable: Boolean get() =
        provenance == Provenance.VERIFIED || provenance == Provenance.SAMPLE

    /** True only for real Bhashini output. Gate any claim on this, not on isAvailable. */
    val isTrustworthy: Boolean get() = provenance == Provenance.VERIFIED
}
