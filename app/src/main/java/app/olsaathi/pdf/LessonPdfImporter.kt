package app.olsaathi.pdf

import android.content.Context
import android.net.Uri
import android.util.Log
import app.olsaathi.content.VerifiedContentPack
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.IOException

/**
 * One Hindi line pulled out of a teacher's PDF, and what the app can do with it.
 *
 * [translation] is null when the pack has never seen this sentence. That is the
 * common case for a PDF the app has not been built around, and it is reported
 * rather than papered over.
 */
data class ImportedLine(
    val index: Int,
    val hindi: String,
    val translation: String?,
    val entryId: String?,
    /** True when [translation] came from Bhashini at import time rather than
     *  from the offline pack. Shown on screen and printed on the sheet. */
    val live: Boolean = false,
    /** True when [translation] came from the on-device IndicTrans2 model. */
    val onDevice: Boolean = false,
) {
    val isCovered: Boolean get() = translation != null
}

/**
 * The result of reading one PDF: what was found, and how much of it the app
 * can honestly render in the target language.
 */
data class ImportResult(
    val pageCount: Int,
    val lines: List<ImportedLine>,
    val languageCode: String,
    val languageEnglish: String,
    /** Lines left out because they are not in Devanagari, so not Hindi. */
    val skipped: Int = 0,
    /** The script most skipped lines were in, e.g. "Tamil", for the message. */
    val skippedScript: String = "",
    /** Words whose spelling the PDF's font map broke and [HindiRepair] fixed. */
    val repairedWords: Int = 0,
) {
    val covered: List<ImportedLine> get() = lines.filter { it.isCovered }
    val uncovered: List<ImportedLine> get() = lines.filter { !it.isCovered }
    val coveragePercent: Int
        get() = if (lines.isEmpty()) 0 else covered.size * 100 / lines.size
}

/**
 * Reads a teacher's lesson PDF and matches its Hindi against the shipped pack.
 *
 * WHAT THIS DOES NOT DO, AND WHY
 * ------------------------------
 * It does not translate. It cannot, and pretending otherwise is the one thing
 * this codebase refuses to do.
 *
 * Translating arbitrary new Hindi on the tablet would mean carrying a
 * translation model. IndicTrans2 is 1.1B parameters and about 4.4 GB in
 * float32; even distilled and quantised to int8 it is roughly 200 MB, on a
 * device with 2 GB of RAM total where this whole app currently peaks at 43 MB.
 * There is no version of that which runs on the hardware the problem statement
 * names.
 *
 * So the honest design is: extract the Hindi, match what the pack already
 * knows, and tell the teacher plainly which lines it does not. A worksheet
 * built from the matched lines is real. A worksheet with machine-guessed
 * filler in it would look identical to a teacher who cannot read the target
 * language, and would be worse than useless in front of a class.
 *
 * The lines it could not match are not thrown away: [ImportResult.uncovered]
 * is what the sync step sends to the build pipeline, which is exactly what the
 * problem statement's "after initial content synchronisation" clause allows.
 */
class LessonPdfImporter(private val context: Context) {

    /**
     * Extract and match. Call off the main thread: PDF parsing is slow and
     * allocates, and a 2 GB tablet will show it.
     *
     * @throws IOException if the file cannot be opened or parsed, so the
     *         caller can say "this is not a readable PDF" rather than showing
     *         an empty result that looks like an empty document.
     */
    fun import(uri: Uri, pack: VerifiedContentPack, ncert: Boolean = false): ImportResult {
        // PdfBox-Android needs its resources unpacked once per process before
        // any document is opened, or font loading fails at parse time.
        PDFBoxResourceLoader.init(context.applicationContext)

        val text: String
        val pages: Int
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("Could not open the selected file.")
            PDDocument.load(input).use { doc ->
                pages = doc.numberOfPages
                val all = PDFTextStripper().getText(doc)
                text = if (ncert) {
                    // NCERT sets the lesson in the regular face; headings,
                    // credits, page numbers and the notes for the teacher are
                    // bold or italic. If the regular face holds too little,
                    // this is not that layout, and everything is kept.
                    val regular = RegularTextStripper().getText(doc)
                    if (devanagariLetters(regular) * 10 >= devanagariLetters(all) * 3) regular else all
                } else all
            }
        }

        // Fix what the font map broke, then keep only real sentences.
        val words = HindiRepair.Words.load(context)
        var repaired = 0
        val sentences = tidy(segment(HindiRepair.normalize(text)), ncert).map { s ->
            val (fixed, n) = HindiRepair.repairLine(s, words)
            repaired += n
            fixed
        }

        // Only Hindi goes on. A Tamil or English chapter sent to a Hindi
        // model comes back as confident nonsense, labelled as a translation;
        // on the tablet a Tamil textbook imported into Sarangi did exactly that.
        val (hindiLines, otherLines) = sentences.partition { isDevanagari(it) }
        val lines = hindiLines.mapIndexed { i, hindi ->
            // Keep on pack.lookup() rather than TranslationRouter. This reports which
            // lines are honestly "not in offline pack" so the uncovered list can be
            // sent to the build pipeline. Online translations would dishonestly inflate
            // the coverage figure and defeat the sync workflow.
            val t = pack.lookup(hindi)
            // lookup() always returns a Translation; an unknown sentence comes
            // back UNAVAILABLE with an empty target. Treat that as a miss
            // rather than as an empty translation, which would print a blank
            // line on the worksheet where the target language should be.
            val usable = t.isAvailable && t.target.isNotBlank()
            ImportedLine(
                index = i,
                hindi = hindi,
                translation = if (usable) t.target else null,
                entryId = if (usable) t.entryId.ifEmpty { null } else null,
            )
        }

        Log.i(TAG, "Imported $pages page(s), ${lines.size} lines, " +
                "${lines.count { it.isCovered }} matched in ${pack.languageCode}, " +
                "${otherLines.size} skipped as not Hindi")

        return ImportResult(
            pageCount = pages,
            lines = lines,
            languageCode = pack.languageCode,
            languageEnglish = pack.languageEnglish,
            skipped = otherLines.size,
            skippedScript = otherLines.map { mainScript(it) }.filter { it.isNotEmpty() }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "",
            repairedWords = repaired,
        )
    }


    /** Text in the regular face only, the way NCERT sets its lessons. */
    private class RegularTextStripper : PDFTextStripper() {
        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            val face = textPositions?.firstOrNull()?.font?.name.orEmpty()
            if (face.contains("Bold", ignoreCase = true) || face.contains("Italic", ignoreCase = true)) return
            super.writeString(text, textPositions)
        }
    }

    companion object {
        private const val TAG = "LessonPdfImporter"

        /**
         * Split extracted page text into candidate sentences.
         *
         * Hindi sentences end with a danda (U+0964), and PDFs also carry ASCII
         * full stops and question marks from mixed content. Line breaks inside a
         * PDF are layout artefacts rather than sentence boundaries, so they are
         * collapsed first; splitting on them would cut most sentences in half and
         * guarantee a miss against the pack.
         */
        internal fun segment(raw: String): List<String> {
            // Strip C0 control characters first. PDFTextStripper emits a form
            // feed between pages, and a PDF whose header used a font with no
            // usable ToUnicode map yields NUL bytes where the glyphs were.
            // Left in, that junk prefixes the first real sentence and the pack
            // lookup misses it, so a line that IS in the pack reports as "not
            // in offline pack". That is the one kind of wrong answer this app
            // must never give, and it was caught by importing a real PDF.
            val flattened = raw.replace(Regex("[\u0000-\u001F]"), " ")
                .replace(Regex("[\\r\\n]+"), " ")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
            if (flattened.isEmpty()) return emptyList()

            return flattened.split(Regex("(?<=[।॥?!.])"))
                .map { it.trim() }
                .filter { it.length >= MIN_SENTENCE_CHARS && it.any { c -> c.isLetter() } }
        }

        /**
         * Sentences as a teacher would print them. A closing quote the split
         * left at the start of the next sentence goes back to its own, and
         * Hindi that is not a sentence is dropped: fill-in-the-blank stubs
         * ("और ....."), single words, letter tables from an activity, and
         * lines the font map turned into Latin letters. From NCERT, a page
         * number run into a sentence goes too; in a teacher's own PDF a
         * leading numeral may be the sentence, so there it stays.
         * Lines in another script pass through, to be counted as skipped.
         */
        internal fun tidy(parts: List<String>, ncert: Boolean): List<String> {
            val out = ArrayList<String>()
            for (raw in parts) {
                var p = raw
                val quote = CLOSING_QUOTE.find(p)
                if (quote != null && out.isNotEmpty()) {
                    out[out.size - 1] = out.last() + quote.value.trim()
                    p = p.substring(quote.range.last + 1)
                }
                if (ncert) p = p.replace(PAGE_NUMBER, "")
                p = p.trim()
                if (p.isNotEmpty()) out.add(p)
            }
            return out.filter { p ->
                if (!isDevanagari(p)) return@filter true
                val words = DEVANAGARI_WORD.findAll(p).map { it.value }.toList()
                p.length >= MIN_SENTENCE_CHARS && words.size >= 2 &&
                    !BLANK.containsMatchIn(p) &&
                    words.count { it.length == 1 } * 10 <= words.size * 3 &&
                    !LATIN_DAMAGE.containsMatchIn(p)
            }
        }

        private val CLOSING_QUOTE = Regex("^[”’]+\\s*")
        private val PAGE_NUMBER = Regex("^\\d{1,2}\\s+(?=[ऀ-ॿ])")
        private val DEVANAGARI_WORD = Regex("[ऀ-ॿ]+")
        private val BLANK = Regex("\\s\\.$|\\.\\.|_|…")
        private val LATIN_DAMAGE = Regex("[À-ɏ]")

        private fun devanagariLetters(s: String) = s.count { it in 'ऀ'..'ॿ' }

        /**
         * True when most of the line's letters are Devanagari. Hindi lines
         * carry the odd English word or numeral, so it is a majority, not all.
         * Text from a legacy Hindi font with no Unicode map extracts as Latin
         * gibberish, and is caught here too.
         */
        fun isDevanagari(line: String): Boolean {
            val counts = scriptCounts(line)
            val total = counts.values.sum()
            return total >= 2 && (counts[Character.UnicodeScript.DEVANAGARI] ?: 0) * 10 >= total * 6
        }

        /** The script most of the line's letters are in, as a name for people. */
        fun mainScript(line: String): String {
            val top = scriptCounts(line).maxByOrNull { it.value }?.key ?: return ""
            return when (top) {
                Character.UnicodeScript.LATIN -> "English (Latin letters)"
                Character.UnicodeScript.DEVANAGARI -> "Devanagari"
                else -> top.name.lowercase().replaceFirstChar { it.uppercase() }
            }
        }

        private fun scriptCounts(line: String): Map<Character.UnicodeScript, Int> {
            val out = HashMap<Character.UnicodeScript, Int>()
            line.codePoints().forEach { cp ->
                val s = Character.UnicodeScript.of(cp)
                if (s != Character.UnicodeScript.COMMON && s != Character.UnicodeScript.INHERITED &&
                    s != Character.UnicodeScript.UNKNOWN) out[s] = (out[s] ?: 0) + 1
            }
            return out
        }

        /**
         * Shorter fragments are page furniture: numerals, headers, a stray
         * "1." from a numbered list. Matching those against the pack produces
         * noise in the coverage figure and nothing useful on a worksheet.
         */
        private const val MIN_SENTENCE_CHARS = 4
    }
}
