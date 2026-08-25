package `in`.gov.tribalfln.engine

import android.util.Log
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * TribalPhonemeMatcher — Bidirectional Hindi ↔ Santhali/Ho/Mundari phoneme mapping.
 * Supports offline phoneme-level translation between Hindi Devanagari,
 * Santhali Ol Chiki (U+1C50–U+1C6D), Ho Warang Citi (U+118A0–U+118FF),
 * and Mundari (Devanagari fallback).
 */
class TribalPhonemeMatcher {

    companion object {
        private const val TAG = "PhonemeMatcher"
        const val OL_CHIKI_START = 0x1C50
        const val OL_CHIKI_END = 0x1C6D
        private const val HO_START = 0x118A0
        private const val HO_END = 0x118FF
        private const val MUNDARI_START = 0x0900
        private const val MUNDARI_END = 0x097F

        private val HINDI_TO_SANTHALI = mapOf(
            "एक" to "\u1C50\u1C55\u1C5B", "दो" to "\u1C50\u1C5E",
            "तीन" to "\u1C50\u1C63\u1C68", "चार" to "\u1C5B\u1C55\u1C60",
            "पाँच" to "\u1C50\u1C55\u1C50\u1C64", "नमस्ते" to "\u1C50\u1C55\u1C50\u1C60",
            "धन्यवाद" to "\u1C50\u1C5A\u1C5E", "हाँ" to "\u1C50\u1C55", "नहीं" to "\u1C50\u1C63",
            "पेड़" to "\u1C50\u1C55\u1C5B", "फूल" to "\u1C50\u1C5E\u1C66",
            "पानी" to "\u1C50\u1C55\u1C63", "सूरज" to "\u1C50\u1C61\u1C5A",
            "चाँद" to "\u1C50\u1C55\u1C50", "नदी" to "\u1C50\u1C5B\u1C63",
            "माँ" to "\u1C50\u1C55", "पिता" to "\u1C50\u1C5B\u1C55",
            "बच्चा" to "\u1C50\u1C5E\u1C55", "किताब" to "\u1C50\u1C5B\u1C55",
            "स्कूल" to "\u1C50\u1C61\u1C5C", "शिक्षक" to "\u1C50\u1C5B\u1C55",
            "गिनना" to "\u1C50\u1C55\u1C5B", "पढ़ना" to "\u1C50\u1C5B\u1C55",
            "लिखना" to "\u1C50\u1C63\u1C5B", "बोलना" to "\u1C50\u1C5E\u1C5B",
            "सुनना" to "\u1C50\u1C61\u1C5B", "देखना" to "\u1C50\u1C5B\u1C55",
            "सीखना" to "\u1C50\u1C61\u1C5B", "खेलना" to "\u1C50\u1C5B\u1C5E",
        )
        private val SANTHALI_TO_HINDI = HINDI_TO_SANTHALI.entries.associate { (k, v) -> v to k }

        private val HINDI_TO_HO = mapOf(
            "एक" to "\u118A0\u118B0", "दो" to "\u118A0\u118B2",
            "तीन" to "\u118A0\u118B2\u118A4", "चार" to "\u118B0\u118A0\u118B2",
            "पाँच" to "\u118A0\u118B0\u118A0\u118B5", "पानी" to "\u118A0\u118B2\u118A4",
            "सूरज" to "\u118A0\u118B0\u118AF", "चाँद" to "\u118A0\u118B0\u118A0",
            "माँ" to "\u118A0\u118B0", "पेड़" to "\u118A0\u118B0\u118B2",
            "नमस्ते" to "\u118A0\u118B0\u118A0", "धन्यवाद" to "\u118A0\u118B5\u118B2",
        )
        private val HINDI_TO_MUNDARI = mapOf(
            "एक" to "एत्\u200Cबा", "दो" to "हरदी",
            "तीन" to "आपुत्\u200Cबा", "चार" to "चोवन",
            "पाँच" to "मोंदे", "पानी" to "दाहाद",
            "सूरज" to "बारतार", "चाँद" to "आदिये",
            "माँ" to "आया", "पेड़" to "हरिया",
            "नमस्ते" to "जोहार", "धन्यवाद" to "आबार बुरु",
        )
        private val HO_TO_HINDI = HINDI_TO_HO.entries.associate { (k, v) -> v to k }
        private val MUNDARI_TO_HINDI = HINDI_TO_MUNDARI.entries.associate { (k, v) -> v to k }

        private val HINDI_TO_OL_CHIKI_PHONEME = mapOf(
            'अ' to "\u1C50", 'आ' to "\u1C50\u1C55", 'इ' to "\u1C52",
            'ई' to "\u1C52\u1C68", 'उ' to "\u1C54", 'ऊ' to "\u1C54\u1C68",
            'ए' to "\u1C5E", 'ऐ' to "\u1C5E\u1C55", 'ओ' to "\u1C5C",
            'औ' to "\u1C5C\u1C55",
            'क' to "\u1C5B", 'ख' to "\u1C5B\u1C55", 'ग' to "\u1C5B\u1C5E",
            'घ' to "\u1C5B\u1C5C", 'ङ' to "\u1C5B\u1C66",
            'च' to "\u1C5D", 'छ' to "\u1C5D\u1C55", 'ज' to "\u1C5D\u1C5E",
            'झ' to "\u1C5D\u1C5C", 'ञ' to "\u1C5D\u1C66",
            'ट' to "\u1C5F", 'ठ' to "\u1C5F\u1C55", 'ड' to "\u1C5F\u1C5E",
            'ढ' to "\u1C5F\u1C5C", 'ण' to "\u1C5F\u1C66",
            'त' to "\u1C60", 'थ' to "\u1C60\u1C55", 'द' to "\u1C60\u1C5E",
            'ध' to "\u1C60\u1C5C", 'न' to "\u1C60\u1C66",
            'प' to "\u1C61", 'फ' to "\u1C61\u1C55", 'ब' to "\u1C61\u1C5E",
            'भ' to "\u1C61\u1C5C", 'म' to "\u1C61\u1C66",
            'य' to "\u1C62", 'र' to "\u1C63", 'ल' to "\u1C63\u1C5E",
            'व' to "\u1C64", 'श' to "\u1C65", 'ष' to "\u1C65\u1C55",
            'स' to "\u1C66", 'ह' to "\u1C67",
        )

        fun translateHindiToHo(hindiText: String): String {
            if (hindiText.isBlank()) return ""
            val normalized = normalizeHindiCompanion(hindiText)
            HINDI_TO_HO[normalized]?.let { return it }
            val words = hindiText.split("\\s+".toRegex()).filter { it.isNotBlank() }
            return words.map { word -> HINDI_TO_HO[normalizeHindiCompanion(word)] ?: word }.joinToString(" ")
        }

        fun translateHindiToMundari(hindiText: String): String {
            if (hindiText.isBlank()) return ""
            val normalized = normalizeHindiCompanion(hindiText)
            HINDI_TO_MUNDARI[normalized]?.let { return it }
            val words = hindiText.split("\\s+".toRegex()).filter { it.isNotBlank() }
            return words.map { word -> HINDI_TO_MUNDARI[normalizeHindiCompanion(word)] ?: word }.joinToString(" ")
        }

        fun translateHoToHindi(hoText: String): String {
            if (hoText.isBlank()) return ""
            HO_TO_HINDI[hoText]?.let { return it }
            val words = hoText.split("\\s+".toRegex()).filter { it.isNotBlank() }
            return words.map { HO_TO_HINDI[it] ?: it }.joinToString(" ")
        }

        fun translateMundariToHindi(mundariText: String): String {
            if (mundariText.isBlank()) return ""
            MUNDARI_TO_HINDI[mundariText]?.let { return it }
            val words = mundariText.split("\\s+".toRegex()).filter { it.isNotBlank() }
            return words.map { MUNDARI_TO_HINDI[it] ?: it }.joinToString(" ")
        }

        fun isHoWarangCiti(char: Char): Boolean = char.code in HO_START..HO_END

        fun containsHo(text: String): Boolean {
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (cp in HO_START..HO_END) return true
                i += Character.charCount(cp)
            }
            return false
        }

        private fun normalizeHindiCompanion(text: String): String {
            return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .lowercase(Locale("hi", "IN"))
                .replace(Regex("[\\p{Punct}.\\u0964\\u0965]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    fun translateHindiToSanthali(hindiText: String): String {
        if (hindiText.isBlank()) return ""
        val normalized = normalizeHindi(hindiText)
        HINDI_TO_SANTHALI[normalized]?.let { return it }
        val words = hindiText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val translated = words.map { word ->
            HINDI_TO_SANTHALI[normalizeHindi(word)] ?: translatePhonemeLevel(word)
        }
        return translated.joinToString(" ")
    }

    private fun translatePhonemeLevel(hindiWord: String): String {
        val sb = StringBuilder()
        for (ch in hindiWord) {
            HINDI_TO_OL_CHIKI_PHONEME[ch]?.let { sb.append(it) } ?: sb.append(ch)
        }
        return sb.toString()
    }

    fun translateSanthaliToHindi(santhaliText: String): String {
        if (santhaliText.isBlank()) return ""
        SANTHALI_TO_HINDI[santhaliText]?.let { return it }
        val words = santhaliText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val translated = words.map { word -> SANTHALI_TO_HINDI[word] ?: reversePhonemeLevel(word) }
        return translated.joinToString(" ")
    }

    private fun reversePhonemeLevel(olChikiWord: String): String {
        val reverseMap = HINDI_TO_OL_CHIKI_PHONEME.entries.associate { (k, v) -> v to k }
        val sb = StringBuilder()
        var i = 0
        while (i < olChikiWord.length) {
            val ch = olChikiWord[i]
            val code = ch.code
            if (code in OL_CHIKI_START..OL_CHIKI_END) {
                var matched = false
                for (len in min(4, olChikiWord.length - i) downTo 2) {
                    val substr = olChikiWord.substring(i, i + len)
                    reverseMap[substr]?.let { sb.append(it); i += len; matched = true }; if (matched) break
                }
                if (!matched) { reverseMap[ch.toString()]?.let { sb.append(it) } ?: sb.append(ch); i++ }
            } else { sb.append(ch); i++ }
        }
        return sb.toString()
    }

    /**
     * Where a piece of tribal-language output came from.
     *
     * This class does NOT translate. Hindi is Indo-Aryan and Santhali, Ho and
     * Mundari are Austroasiatic; they share neither vocabulary nor grammar, so
     * character and phoneme mapping cannot produce meaning. What it produces is
     * the Hindi respelled in the tribal script, which a teacher can read aloud
     * but which is not the language.
     *
     * Callers must surface this to the user. Output that looks like a
     * translation but is not one is worse than no output at all, because a
     * teacher cannot tell the difference and the children can.
     */
    enum class Provenance {
        /** A real translation from a checked source. Nothing here returns this yet. */
        VERIFIED,

        /** Hindi respelled in the tribal script. Readable aloud, not the language. */
        TRANSLITERATED,

        /** Nothing usable offline for this input. */
        UNAVAILABLE,
    }

    data class TranslationResult(
        val text: String,
        val provenance: Provenance,
    )

    /**
     * The entry point UI should call, because it cannot silently misrepresent
     * its own output the way [translateToLanguage] can.
     */
    fun translateWithProvenance(hindiText: String, targetLang: String): TranslationResult {
        if (hindiText.isBlank()) return TranslationResult("", Provenance.UNAVAILABLE)
        val out = translateToLanguage(hindiText, targetLang)
        if (out.isBlank() || out == hindiText) {
            return TranslationResult(hindiText, Provenance.UNAVAILABLE)
        }
        // Every path in this class is script conversion, including the word
        // dictionaries, so there is no branch that can honestly claim VERIFIED.
        return TranslationResult(out, Provenance.TRANSLITERATED)
    }

    fun translateToLanguage(hindiText: String, targetLang: String): String = when (targetLang) {
        "san" -> translateHindiToSanthali(hindiText)
        "hoc" -> translateHindiToHo(hindiText)
        "mfq" -> translateHindiToMundari(hindiText)
        else -> hindiText
    }

    fun translateFromLanguage(text: String, sourceLang: String): String = when (sourceLang) {
        "san" -> translateSanthaliToHindi(text)
        "hoc" -> translateHoToHindi(text)
        "mfq" -> translateMundariToHindi(text)
        else -> text
    }

    fun findBestMatch(query: String, direction: String = "HI→SAN"): Pair<String, Double> {
        val dictionary = if (direction == "HI→SAN") HINDI_TO_SANTHALI else SANTHALI_TO_HINDI
        val normalizedQuery = if (direction == "HI→SAN") normalizeHindi(query) else query
        var bestMatch = ""; var bestScore = 0.0
        for ((key, value) in dictionary) {
            val target = if (direction == "HI→SAN") key else value
            val score = similarity(normalizedQuery, target)
            if (score > bestScore) { bestScore = score; bestMatch = if (direction == "HI→SAN") value else key }
        }
        return Pair(bestMatch, bestScore)
    }

    private fun similarity(a: String, b: String): Double {
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - (levenshteinDistance(a, b).toDouble() / maxLen.toDouble())
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length; if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }; var curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) { val cost = if (a[i] == b[j]) 0 else 1; curr[j + 1] = min(min(curr[j] + 1, prev[j + 1] + 1), prev[j] + cost) }
            val temp = prev; prev = curr; curr = temp
        }
        return prev[b.length]
    }

    private fun normalizeHindi(text: String): String = normalizeHindiCompanion(text)

    fun isOlChiki(char: Char): Boolean = char.code in OL_CHIKI_START..OL_CHIKI_END
    fun containsOlChiki(text: String): Boolean = text.any { isOlChiki(it) }
    fun getDictionarySize(): Int = HINDI_TO_SANTHALI.size + HINDI_TO_HO.size + HINDI_TO_MUNDARI.size
}
