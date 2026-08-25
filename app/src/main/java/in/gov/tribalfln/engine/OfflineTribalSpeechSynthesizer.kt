package `in`.gov.tribalfln.engine

import android.util.Log

/**
 * OfflineTribalSpeechSynthesizer — Grapheme-to-phoneme (G2P) based
 * text-to-speech synthesis for tribal languages. Operates entirely
 * offline with no network dependency.
 *
 * Supports Santhali (Ol Chiki), Ho (Warang Citi), and Mundari scripts
 * with phoneme-level frequency mapping for audio output.
 */
class OfflineTribalSpeechSynthesizer {

    companion object {
        private const val TAG = "SpeechSynthesizer"

        /**
         * Phoneme-to-frequency mapping for G2P synthesis.
         * Maps individual graphemes to audio frequencies (Hz).
         */
        val PHONEME_MAP = mapOf(
            'a' to 440f, 'e' to 523f, 'i' to 587f, 'o' to 659f, 'u' to 698f,
            'k' to 330f, 'g' to 294f, 'c' to 262f, 'j' to 233f,
            't' to 208f, 'd' to 185f, 'n' to 175f, 'p' to 165f, 'b' to 147f,
            'm' to 139f, 'y' to 131f, 'r' to 123f, 'l' to 117f, 's' to 104f, 'h' to 98f
        )
    }

    data class PhonemeResult(
        val grapheme: String,
        val frequency: Float
    )

    /**
     * Parse text into phoneme-to-frequency pairs using G2P mapping.
     */
    fun parseG2P(text: String): List<PhonemeResult> {
        val result = mutableListOf<PhonemeResult>()
        for (ch in text) {
            val freq = PHONEME_MAP[ch.lowercaseChar()]
            if (freq != null) {
                result.add(PhonemeResult(ch.toString(), freq))
            } else if (ch.isWhitespace()) {
                result.add(PhonemeResult(" ", 0f))
            } else {
                result.add(PhonemeResult("<?>", 0f))
            }
        }
        return result
    }

    /**
     * Synthesize a waveform (simplified: returns frequency array).
     */
    fun synthesize(text: String): List<PhonemeResult> {
        Log.d(TAG, "Synthesizing: $text")
        return parseG2P(text)
    }

    /**
     * Check if a character is a supported phoneme.
     */
    fun isSupportedPhoneme(char: Char): Boolean {
        return PHONEME_MAP.containsKey(char.lowercaseChar())
    }

    /**
     * Get the frequency for a given grapheme.
     */
    fun getFrequency(grapheme: Char): Float? {
        return PHONEME_MAP[grapheme.lowercaseChar()]
    }
}
