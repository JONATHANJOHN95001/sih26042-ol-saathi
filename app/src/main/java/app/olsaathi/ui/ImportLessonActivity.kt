package app.olsaathi.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olsaathi.OlSaathiApplication
import app.olsaathi.R
import app.olsaathi.content.VerifiedContentPack
import app.olsaathi.databinding.ActivityImportLessonBinding
import app.olsaathi.net.BhashiniClient
import app.olsaathi.pdf.ImportResult
import app.olsaathi.pdf.ImportedLine
import app.olsaathi.pdf.LessonPdfImporter
import app.olsaathi.util.NetworkGuard
import app.olsaathi.worksheet.FlashcardPdf
import app.olsaathi.worksheet.ScriptFonts
import app.olsaathi.worksheet.SheetMaterial
import app.olsaathi.worksheet.WorksheetPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Add any lesson PDF and turn it into NIPUN-tagged worksheets and flashcards.
 *
 * The Hindi is extracted and matched against the offline pack first. With a
 * key and a network, every line the pack does not know is then translated
 * live by Bhashini, so a teacher's own PDF produces a complete sheet instead
 * of a sheet of gaps. Each line keeps saying where it came from: on screen
 * here, and on the printed sheet and cards. Offline, the old behaviour stands:
 * only pack lines are used and the rest say "Not in offline pack".
 */
class ImportLessonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportLessonBinding
    private lateinit var pack: VerifiedContentPack
    private var currentUri: Uri? = null
    private var result: ImportResult? = null
    private var title = "Imported lesson"

    /** Set when the Lessons tab's textbook shelf opened this screen. */
    private var bookTitle: String? = null

    /** True for a chapter fetched from NCERT, which is read the NCERT way. */
    private var fromNcert = false
    private var currentPdf: File? = null
    private var pendingSave: File? = null

    private val saveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val source = pendingSave
        pendingSave = null
        if (uri == null || source == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
            Toast.makeText(this, "Saved " + source.name, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val openLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Not every provider grants persistence; the one-off read still works.
            }
            currentUri = uri
            val file = displayName(uri)?.substringBeforeLast('.') ?: "Imported lesson"
            title = bookTitle?.let { "$it · $file" } ?: file
            importPdf(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportLessonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Coverage and translations differ per language, so a switch re-reads.
        LanguagePicker.bind(
            this,
            binding.languageBar.root,
            binding.languageBar.spinnerLanguage,
            binding.languageBar.textLanguageNote,
        ) {
            pack = (application as OlSaathiApplication).pack
            currentUri?.let { importPdf(it) }
        }

        binding.btnChoosePdf.setOnClickListener { openLauncher.launch(arrayOf("application/pdf")) }
        intent.getStringExtra(EXTRA_BOOK)?.let { book ->
            bookTitle = book
            binding.toolbar.subtitle = "Adding a chapter from $book"
        }
        intent.getStringExtra(EXTRA_URL)?.let { url ->
            fromNcert = true
            val chapter = intent.getStringExtra(EXTRA_CHAPTER).orEmpty()
            binding.toolbar.subtitle = "From NCERT: " + listOfNotNull(bookTitle, chapter.ifEmpty { null }).joinToString(" · ")
            if (savedInstanceState == null) downloadAndImport(url, chapter)
        }
        binding.btnOpenMaterials.setOnClickListener {
            startActivity(Intent(this, WorksheetActivity::class.java)
                .putExtra(WorksheetActivity.EXTRA_MATERIAL_KEY, SheetMaterial.importedKey(title)))
        }
        binding.btnGenerateWorksheet.setOnClickListener {
            result?.let { r -> produce("Worksheet") { WorksheetPdf(this).generateForImport(title, r.lines, pack) } }
        }
        binding.btnGenerateFlashcards.setOnClickListener {
            result?.let { r -> produce("Flashcards") { FlashcardPdf(this).generateForImport(title, r.lines, pack) } }
        }
        binding.btnSavePdf.setOnClickListener {
            currentPdf?.let { pendingSave = it; saveLauncher.launch(it.name) }
        }
        binding.btnSharePdf.setOnClickListener { currentPdf?.let { sharePdf(it) } }

        binding.recyclerLines.layoutManager = LinearLayoutManager(this)
    }

    private fun importPdf(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.textStatus.visibility = View.VISIBLE
        binding.textStatus.text = "Reading PDF..."
        binding.cardCoverage.visibility = View.GONE
        binding.recyclerLines.visibility = View.GONE
        binding.rowGenerate.visibility = View.GONE
        binding.resultSection.visibility = View.GONE

        val forPack = pack
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var r = LessonPdfImporter(this@ImportLessonActivity).import(uri, forPack, ncert = fromNcert)
                r = translateMissing(r, forPack)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showResult(r)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.textStatus.text = "This is not a readable PDF."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.textStatus.text = "Error reading PDF: ${e.message}"
                }
            }
        }
    }

    /**
     * Fetch one chapter from NCERT's own site, then read it like any PDF.
     * The PDF stays NCERT's: it sits in the cache (the last few only) so a
     * language switch can re-read it, and what the app keeps is the repaired,
     * translated lines, saved for Materials.
     */
    private fun downloadAndImport(url: String, chapter: String) {
        val name = url.substringAfterLast('/')
        title = listOfNotNull(bookTitle, chapter.ifEmpty { name.substringBefore('.') }).joinToString(" · ")
        binding.progressBar.visibility = View.VISIBLE
        binding.textStatus.visibility = View.VISIBLE
        binding.textStatus.text = "Downloading from NCERT..."
        binding.btnChoosePdf.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val dir = File(cacheDir, "ncert").apply { mkdirs() }
            val file = File(dir, name)
            try {
                // NCERT's server drops the odd connection; one retry.
                val conn = (1..2).firstNotNullOfOrNull { attempt ->
                    try {
                        (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 15_000
                            readTimeout = 30_000
                            if (responseCode != 200) throw IOException("NCERT answered $responseCode")
                        }
                    } catch (e: IOException) {
                        if (attempt == 2) throw e
                        null
                    }
                } ?: throw IOException("no answer from NCERT")
                NetworkGuard.recordNetworkCall()
                val total = conn.contentLengthLong
                val part = File(dir, "$name.part")
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var shown = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - shown >= 512 * 1024) {
                                shown = done
                                val msg = if (total > 0) "Downloading from NCERT: %.1f of %.1f MB"
                                    .format(done / 1e6, total / 1e6) else "Downloading from NCERT: %.1f MB".format(done / 1e6)
                                withContext(Dispatchers.Main) { binding.textStatus.text = msg }
                            }
                        }
                    }
                }
                conn.disconnect()
                if (!part.renameTo(file)) throw IOException("Could not keep the downloaded file")
                // NCERT chapters run from 1 to 61 MB; keep only the last three.
                dir.listFiles()?.filter { it.name.endsWith(".pdf") }?.sortedByDescending { it.lastModified() }
                    ?.drop(3)?.forEach { it.delete() }
                withContext(Dispatchers.Main) {
                    binding.btnChoosePdf.isEnabled = true
                    currentUri = Uri.fromFile(file)
                    importPdf(Uri.fromFile(file))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnChoosePdf.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    binding.textStatus.text = "Could not download from NCERT (" + (e.message ?: e.javaClass.simpleName) +
                        "). Downloading needs the internet; chapters already in Materials work offline."
                }
            }
        }
    }

    /**
     * Translate the lines the pack does not know, live, when there is a key
     * and a network. Capped at [MAX_LIVE_LINES] so a long textbook cannot hold
     * the screen for minutes; the rest stay marked as not translated.
     */
    private suspend fun translateMissing(r: ImportResult, forPack: VerifiedContentPack): ImportResult {
        val client = BhashiniClient()
        val missing = r.lines.filter { !it.isCovered }
        if (missing.isEmpty()) return r
        if (!client.isConfigured || !NetworkGuard.isOnline(this)) return translateOnDevice(r, forPack)
        val code = forPack.languageCode.ifEmpty { "sat" }
        val todo = missing.take(MAX_LIVE_LINES)
        val done = HashMap<Int, String>()
        todo.forEachIndexed { i, line ->
            withContext(Dispatchers.Main) {
                binding.textStatus.text = "Translating line ${i + 1} of ${todo.size} with Bhashini..."
            }
            client.translate(line.hindi, code)?.let { done[line.index] = it }
        }
        return r.copy(lines = r.lines.map { line ->
            done[line.index]?.let { line.copy(translation = it, live = true) } ?: line
        })
    }

    /**
     * No network: translate the missing lines with the on-device model when
     * this tablet has it, labelled as on-device, never as the pack.
     */
    private suspend fun translateOnDevice(r: ImportResult, forPack: VerifiedContentPack): ImportResult {
        val code = forPack.languageCode.ifEmpty { "sat" }
        val mt = app.olsaathi.mt.OfflineTranslator
        if (!mt.available(this, code)) return r
        val todo = r.lines.filter { !it.isCovered }.take(MAX_LIVE_LINES)
        val done = HashMap<Int, String>()
        todo.forEachIndexed { i, line ->
            withContext(Dispatchers.Main) {
                binding.textStatus.text = "Translating line ${i + 1} of ${todo.size} on this tablet..."
            }
            val t = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                mt.translate(this, line.hindi, code) { cont.resumeWith(Result.success(it)) }
            }
            t?.let { done[line.index] = it }
        }
        return r.copy(lines = r.lines.map { line ->
            done[line.index]?.let { line.copy(translation = it, onDevice = true) } ?: line
        })
    }

    private fun showResult(r: ImportResult) {
        result = r
        // Keep the chapter, so the Materials screen can make new question
        // sets and flashcards from it later, with no need to import again.
        if (r.covered.isNotEmpty()) {
            SheetMaterial.saveImported(this, pack.languageCode, title, r.lines)
        }
        if (r.lines.isEmpty()) {
            binding.textStatus.visibility = View.VISIBLE
            binding.textStatus.text = if (r.skipped > 0)
                "This PDF is in ${r.skippedScript.ifEmpty { "another script" }}, not Hindi, so nothing was " +
                    "translated. Ol Saathi makes lessons from Hindi text: add a chapter from a Hindi book, " +
                    "or from the Hindi-medium edition of this one."
            else "This is a scanned or image-only PDF. No text could be extracted."
            return
        }
        binding.textStatus.visibility = View.GONE
        binding.cardCoverage.visibility = View.VISIBLE
        binding.recyclerLines.visibility = View.VISIBLE

        val languageName = (application as OlSaathiApplication).currentLanguageOption()?.english
            ?: r.languageEnglish.ifEmpty { r.languageCode }
        val fromPack = r.lines.count { it.isCovered && !it.live && !it.onDevice }
        val live = r.lines.count { it.live }
        val onDevice = r.lines.count { it.onDevice }
        val none = r.lines.count { !it.isCovered }
        binding.textCoverage.text = buildString {
            append("${r.lines.size} lines in $languageName: ")
            append("$fromPack from the offline pack")
            if (live > 0) append(", $live translated live by Bhashini")
            if (onDevice > 0) append(", $onDevice translated on this tablet (IndicTrans2)")
            if (none > 0) append(", $none not translated")
            if (r.repairedWords > 0) append(". ${r.repairedWords} words repaired where the PDF's font broke the spelling")
            if (r.skipped > 0) append(". ${r.skipped} lines left out: they are in " +
                r.skippedScript.ifEmpty { "another script" } + ", not Hindi")
            append(".\nSaved for Materials: new question sets and flashcards any time, tagged with NIPUN Bharat outcomes.")
        }
        binding.recyclerLines.adapter = ImportedLineAdapter(r.lines, pack.font)
        binding.rowGenerate.visibility = if (r.covered.isNotEmpty()) View.VISIBLE else View.GONE
        binding.btnOpenMaterials.visibility = if (r.covered.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun produce(what: String, build: () -> File?) {
        binding.btnGenerateWorksheet.isEnabled = false
        binding.btnGenerateFlashcards.isEnabled = false
        binding.textStatus.visibility = View.VISIBLE
        binding.textStatus.text = "Generating ${what.lowercase()}..."
        lifecycleScope.launch(Dispatchers.IO) {
            val pdf = try { build() } catch (e: Exception) { null }
            val bitmap = pdf?.takeIf { it.exists() }?.let { renderFirstPage(it) }
            withContext(Dispatchers.Main) {
                binding.btnGenerateWorksheet.isEnabled = true
                binding.btnGenerateFlashcards.isEnabled = true
                binding.textStatus.visibility = View.GONE
                if (pdf == null || !pdf.exists()) {
                    Toast.makeText(this@ImportLessonActivity, "Nothing to generate.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                currentPdf = pdf
                binding.imagePreview.setImageBitmap(bitmap)
                binding.textResult.text = "$what · page 1 · ${pdf.name} (${pdf.length() / 1024} KB)"
                binding.resultSection.visibility = View.VISIBLE
                binding.resultSection.post {
                    val rect = android.graphics.Rect(0, 0, binding.resultSection.width, binding.resultSection.height)
                    binding.resultSection.requestRectangleOnScreen(rect, true)
                }
            }
        }
    }

    /** Page 1 of the real PDF at screen width, so the preview is the print. */
    private fun renderFirstPage(pdf: File): Bitmap? = try {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val w = resources.displayMetrics.widthPixels - (48 * resources.displayMetrics.density).toInt()
                    val h = (w.toFloat() * page.height / page.width).toInt()
                    Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.removeSuffix(".pdf")?.removeSuffix(".PDF") else null
        }
    } catch (e: Exception) {
        null
    }

    private fun sharePdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share " + file.name))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    inner class ImportedLineAdapter(
        private val lines: List<ImportedLine>,
        packFont: String,
    ) : RecyclerView.Adapter<ImportedLineAdapter.VH>() {

        private val targetTypeface = ScriptFonts.forTarget(this@ImportLessonActivity, packFont)
        private val sourceTypeface = ScriptFonts.forSource(this@ImportLessonActivity)

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val textHindi: TextView = view.findViewById(R.id.textHindi)
            val textTranslation: TextView = view.findViewById(R.id.textTranslation)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_imported_line, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val line = lines[position]
            holder.textHindi.typeface = sourceTypeface
            holder.textHindi.text = line.hindi
            val t = line.translation
            if (t != null) {
                holder.textTranslation.typeface = targetTypeface
                holder.textTranslation.setTextColor(
                    ContextCompat.getColor(this@ImportLessonActivity, R.color.md_theme_onSurfaceVariant))
                // Every line says where it came from, as everywhere else in the app.
                val label = when {
                    line.live -> "Live machine translation · Bhashini"
                    line.onDevice -> "Machine translation, on this tablet · IndicTrans2"
                    else -> "Offline pack"
                }
                holder.textTranslation.text = SpannableStringBuilder(t).apply {
                    append("\n")
                    val start = length
                    append(label)
                    setSpan(RelativeSizeSpan(0.7f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(Color.parseColor("#6B7A73")), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            } else {
                holder.textTranslation.typeface = android.graphics.Typeface.DEFAULT
                holder.textTranslation.text = "Not in offline pack"
                holder.textTranslation.setTextColor(Color.parseColor("#9E9E9E"))
                holder.textTranslation.setTypeface(null, android.graphics.Typeface.ITALIC)
                holder.itemView.setBackgroundColor(Color.parseColor("#F5F5F5"))
            }
        }

        override fun getItemCount() = lines.size
    }

    companion object {
        private const val MAX_LIVE_LINES = 60

        /** Book title from the Lessons tab's textbook shelf. */
        const val EXTRA_BOOK = "book_title"

        /** An NCERT chapter PDF to download and read, instead of picking a file. */
        const val EXTRA_URL = "ncert_url"

        /** "Chapter 3", for the title the chapter is saved under. */
        const val EXTRA_CHAPTER = "ncert_chapter"
    }
}
