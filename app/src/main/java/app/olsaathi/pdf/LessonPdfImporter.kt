package app.olsaathi.pdf

import android.content.Context
import android.net.Uri
import android.util.Log
import app.olsaathi.content.VerifiedContentPack
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
    fun import(uri: Uri, pack: VerifiedContentPack): ImportResult {
        // PdfBox-Android needs its resources unpacked once per process before
        // any document is opened, or font loading fails at parse time.
        PDFBoxResourceLoader.init(context.applicationContext)

        val text: String
        val pages: Int
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("Could not open the selected file.")
            PDDocument.load(input).use { doc ->
                pages = doc.numberOfPages
                text = PDFTextStripper().getText(doc)
            }
        }

        val lines = segment(text).mapIndexed { i, hindi ->
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
                "${lines.count { it.isCovered }} matched in ${pack.languageCode}")

        return ImportResult(
            pageCount = pages,
            lines = lines,
            languageCode = pack.languageCode,
            languageEnglish = pack.languageEnglish,
        )
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
         * Shorter fragments are page furniture: numerals, headers, a stray
         * "1." from a numbered list. Matching those against the pack produces
         * noise in the coverage figure and nothing useful on a worksheet.
         */
        private const val MIN_SENTENCE_CHARS = 4
    }
}
