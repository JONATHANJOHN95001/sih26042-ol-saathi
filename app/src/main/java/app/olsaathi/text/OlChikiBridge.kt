package app.olsaathi.text

/**
 * Ol Chiki to Devanagari pronunciation bridge. A port of
 * `tools/olchiki_bridge.py`, and it must stay character for character equal to
 * it: `OlChikiBridgeTest` checks every Santali line in the shipped pack against
 * the `bridge` field that script wrote.
 *
 * This is a transliteration, not a translation. It exists at runtime for one
 * reason: Bhashini's only Santali voice (IIT Madras, `Bhashini/IITM/TTS`) says
 * nothing for Ol Chiki input, 0.02 s of silence with a success response, and
 * speaks the same words written in Devanagari. A live translation arrives in
 * Ol Chiki, so it has to cross this bridge before it can be spoken.
 *
 * Ol Chiki letter names encode their own phonetic values (see the Python file
 * for the derivation). Ol Chiki is alphabetic and Devanagari is an abugida, so
 * a written vowel after a consonant always becomes an explicit matra here.
 */
object OlChikiBridge {

    /** Independent vowel, and the matra that attaches to a preceding consonant. */
    private val VOWELS = mapOf(
        'ᱚ' to ("ओ" to "ो"),   // LA   o
        'ᱟ' to ("आ" to "ा"),   // LAA  aa
        'ᱤ' to ("इ" to "ि"),   // LI   i
        'ᱩ' to ("उ" to "ु"),   // LU   u
        'ᱮ' to ("ए" to "े"),   // LE   e
        'ᱳ' to ("ओ" to "ो"),   // LO   o
    )

    private val CONSONANTS = mapOf(
        'ᱛ' to "त", 'ᱜ' to "ग", 'ᱝ' to "ङ", 'ᱞ' to "ल",
        'ᱠ' to "क", 'ᱡ' to "ज", 'ᱢ' to "म", 'ᱣ' to "व",
        'ᱥ' to "स", 'ᱦ' to "ह", 'ᱧ' to "ञ", 'ᱨ' to "र",
        'ᱪ' to "च", 'ᱫ' to "द", 'ᱬ' to "ण", 'ᱭ' to "य",
        'ᱯ' to "प", 'ᱰ' to "ड", 'ᱱ' to "न", 'ᱲ' to "ड़",
        'ᱴ' to "ट", 'ᱵ' to "ब", 'ᱶ' to "व", 'ᱷ' to "ह",
    )

    private const val VIRAMA = "्"

    private val MARKS = mapOf(
        'ᱸ' to "ं",   // MU TTUDDAG, nasalisation
        'ᱹ' to "",    // GAAHLAA TTUDDAAG, glottal; no Devanagari equivalent
        'ᱺ' to "ं",   // MU-GAAHLAA TTUDDAAG
        'ᱻ' to "",    // RELAA
        'ᱼ' to "",    // PHAARKAA
        'ᱽ' to "",    // AHAD
        '᱾' to "।",   // MUCAAD, danda
        '᱿' to "॥",   // DOUBLE MUCAAD
    )

    /** True when [text] contains any Ol Chiki character at all. */
    fun containsOlChiki(text: String): Boolean = text.any { it.code in 0x1C50..0x1C7F }

    /** Ol Chiki to Devanagari. Characters outside the block pass through. */
    fun toDevanagari(text: String): String {
        val out = StringBuilder(text.length + 8)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val consonant = CONSONANTS[ch]
            if (consonant != null) {
                out.append(consonant)
                val next = text.getOrNull(i + 1)
                val matra = next?.let { VOWELS[it] }?.second
                if (matra != null) {
                    out.append(matra)
                    i += 2
                    continue
                }
                // Virama only inside a word: a trailing one reads as an error,
                // जोहार् rather than जोहार.
                if (next != null && next in CONSONANTS) out.append(VIRAMA)
                i += 1
                continue
            }
            val vowel = VOWELS[ch]
            when {
                vowel != null -> out.append(vowel.first)
                ch in MARKS -> out.append(MARKS.getValue(ch))
                ch.code in 0x1C50..0x1C59 -> out.append((0x0966 + (ch.code - 0x1C50)).toChar())
                else -> out.append(ch)
            }
            i += 1
        }
        return out.toString()
    }
}
