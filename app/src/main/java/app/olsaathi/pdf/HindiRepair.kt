package app.olsaathi.pdf

import android.content.Context
import java.text.Normalizer

/**
 * Repairs Hindi extracted from NCERT's PDFs.
 *
 * NCERT sets its Hindi in Kokila, and the font's Unicode map breaks some
 * letters on the way out: a conjunct loses its halant (कयों for क्यों,
 * चपपलें for चप्पलें), a vowel sign comes out twice (क्योंों) or in two
 * halves (ज़ाेर), an i-sign lands before its consonant (िवस्तृत). Printed on a
 * worksheet those are misspellings in front of children learning to read, and
 * a translation model turns them into nonsense.
 *
 * The fixed patterns are rewritten by rule. The halant needs a word list:
 * कमल has none and क्यों needs one, and nothing in the letters says which.
 * [Words] is the IndicTrans2 vocabulary in frequency order, and a word is only
 * changed to a spelling one font fault away that is more frequent than the
 * word as extracted. On Sarangi Class 2, chapter 1, that repaired every broken
 * word of the Neema story, which then matched the hand-checked pack exactly.
 */
object HindiRepair {

    private const val VIRAMA = '्'
    private const val NUKTA = '़'
    private const val SIGNS =
        "ािीुूृॄॅॆेैॉॊोौँंः"

    /** Letters whose nukta is optional in modern Hindi; ड़ and ढ़ are not among them. */
    private const val PERSO_ARABIC = "कखगजफ"

    /** A stem match counts, but always below a whole-word match. */
    private const val STEM_PENALTY = 100_000

    private val WORD = Regex("[ऀ-ॿ]+")
    private val GAP = Regex("्[$SIGNS]")

    /** Hindi words with their frequency rank, 1 being the most frequent. */
    class Words(private val rank: Map<String, Int>) {
        fun rankOf(w: String): Int? = rank[w] ?: rank[stripNukta(w)]
        val size: Int get() = rank.size

        companion object {
            /** About 58,000 words; loaded per import, not kept. */
            fun load(context: Context): Words =
                context.assets.open("hindi/words.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    val m = HashMap<String, Int>(80_000)
                    var r = 0
                    lines.forEach { l ->
                        if (l.isNotBlank() && !l.startsWith("#")) {
                            r++
                            m.putIfAbsent(nfc(l.trim()), r)
                        }
                    }
                    Words(m)
                }

            /** For tests: the words in frequency order. */
            fun of(vararg ranked: String) = Words(ranked.withIndex().associate { (i, w) -> nfc(w) to i + 1 })
        }
    }

    fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

    internal fun stripNukta(w: String): String {
        if (w.indexOf(NUKTA) < 0) return w
        val sb = StringBuilder(w.length)
        for (i in w.indices) {
            if (w[i] == NUKTA && i > 0 && w[i - 1] in PERSO_ARABIC) continue
            sb.append(w[i])
        }
        return sb.toString()
    }

    private fun isConsonant(c: Char) = c in 'क'..'ह'

    /** The fixes that need no word list, on the whole text before it is split. */
    fun normalize(raw: String): String {
        var s = nfc(raw).replace("\u200D", "").replace("\u200C", "")
        // Page furniture on every NCERT page.
        s = s.replace(Regex("Reprint\\s+\\d{4}\\s*-\\s*\\d{2,4}"), " ")
        // A vowel sign split in two by the font map: ज़ाेर, एेसा, आैर.
        s = s.replace("ाे", "ो").replace("ाै", "ौ")
            .replace("एे", "ऐ").replace("अा", "आ")
            .replace("अो", "ओ").replace("अौ", "औ")
            .replace("आे", "ओ").replace("आै", "औ")
        // A halant left hanging before a line break: स् कूल.
        s = s.replace(Regex("्\\s+(?=[क-ह])"), "्")
        // A vowel sign written twice: क्योंों, खूूब.
        s = s.replace(Regex("([ा-ौ][ँं]?)\\1+"), "$1")
        // An i-sign drawn before its consonant: िवस्तृत.
        s = s.replace(Regex("(^|[\\s–—\"“(])ि([क-ह]़?)"), "$1$2ि")
        return s
    }

    /** [line] with each word at its most frequent close spelling, and how many changed. */
    fun repairLine(line: String, words: Words): Pair<String, Int> {
        var n = 0
        val out = WORD.replace(line) { m ->
            val best = bestSpelling(m.value, words)
            if (best != m.value) n++
            best
        }
        return out to n
    }

    internal fun bestSpelling(w: String, words: Words): String {
        val own = score(w, words)
        var best = w
        var bestScore = own ?: Int.MAX_VALUE
        // Dropping a doubled letter only for a word the list does not know
        // whole: ककड़ी is right, बातचचीत is not.
        for (c in candidates(w, allowDeletion = own == null || own > STEM_PENALTY)) {
            val s = score(c, words) ?: continue
            if (s < bestScore) {
                best = c
                bestScore = s
            }
        }
        return best
    }

    private fun score(w: String, words: Words): Int? {
        words.rankOf(w)?.let { return it }
        val stem = w.trimEnd { it in SIGNS }
        if (stem != w && stem.length >= 2) words.rankOf(stem)?.let { return STEM_PENALTY + it }
        return null
    }

    /** Spellings one font fault away from [w]. */
    internal fun candidates(w: String, allowDeletion: Boolean): List<String> {
        val out = ArrayList<String>()
        // A halant restored between two consonants.
        val slots = ArrayList<Int>()
        for (i in 0 until w.length - 1) {
            if (!isConsonant(w[i])) continue
            val j = if (w[i + 1] == NUKTA) i + 2 else i + 1
            if (j < w.length && isConsonant(w[j])) slots.add(j)
        }
        val used = slots.take(4)
        for (mask in 1 until (1 shl used.size)) {
            val sb = StringBuilder(w)
            for (k in used.indices.reversed()) if (mask and (1 shl k) != 0) sb.insert(used[k], VIRAMA)
            out.add(sb.toString())
        }
        // A letter the font map wrote twice.
        if (allowDeletion) {
            for (k in 0 until w.length - 1) {
                if (isConsonant(w[k]) && w[k] == w[k + 1]) out.add(w.removeRange(k, k + 1))
            }
        }
        // A halant straight before a vowel sign lost the consonant between
        // them: क्ा for क्या, अच्ी for अच्छी.
        GAP.find(w)?.let { g ->
            val at = g.range.first + 1
            for (c in 'क'..'ह') out.add(w.substring(0, at) + c + w.substring(at))
        }
        return out
    }
}
