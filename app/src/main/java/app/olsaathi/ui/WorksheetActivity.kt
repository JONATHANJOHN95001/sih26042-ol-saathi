package app.olsaathi.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import app.olsaathi.OlSaathiApplication
import app.olsaathi.R
import app.olsaathi.content.VerifiedContentPack
import app.olsaathi.databinding.ActivityWorksheetBinding
import app.olsaathi.worksheet.FlashcardPdf
import app.olsaathi.worksheet.WorksheetPdf
import app.olsaathi.worksheet.WorksheetType
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Teaching-materials screen.
 *
 * The problem statement asks for "bilingual teaching materials such as
 * worksheets and flashcards", so this screen produces both from the same
 * lesson selection, and offers share and print for either.
 *
 * Worksheet type and activity count are selectable and passed through to
 * WorksheetPdf.generate(). Before they were wired, both parameters always took
 * their defaults and three of the four generator implementations could not be
 * reached from the UI at all.
 *
 * The preview pane is rendered from the real PDF, never as a mock of one: the
 * same generate() call produces the file that is shared, printed and shown, so
 * the pane cannot disagree with what prints.
 *
 * The first spinner entry is the teaching-phrase deck rather than a lesson.
 * Flashcards can be made from it; worksheets cannot, because a worksheet is
 * built around one lesson's text. Choosing it and asking for a worksheet says
 * so rather than producing an empty page.
 */
class WorksheetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorksheetBinding
    private lateinit var pack: VerifiedContentPack  // reassigned on language switch
    private lateinit var worksheetPdf: WorksheetPdf
    private lateinit var flashcardPdf: FlashcardPdf
    private var currentPdf: File? = null

    /** The file waiting for the user to pick a destination for it. */
    private var pendingSave: File? = null

    /**
     * "Save a copy" goes through the system document picker rather than
     * writing to a hardcoded folder.
     *
     * The app holds no storage permission and asks for none: the picker hands
     * back a single write-once destination the user chose themselves, which
     * works the same on Android 9 and on Android 14 and cannot fail because a
     * Downloads folder was not writable. The PDF itself lives in the cache and
     * is disposable; this is how a teacher keeps one.
     */
    private val saveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val source = pendingSave
        pendingSave = null
        if (uri == null || source == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("no output stream")
            binding.textStatus.text = "Saved a copy of " + source.name
        } catch (e: Exception) {
            // A failed save must say so. Silently doing nothing looks
            // identical to a successful save that went somewhere unexpected.
            binding.textStatus.text = "Could not save: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** Parallel to the spinner. A null means the teaching-phrase deck. */
    private var lessonIds = listOf<String?>()

    /** Selected worksheet type; the enum is the single source of the labels. */
    private var selectedType = WorksheetType.CLASSROOM_DIALOGUES

    /** Activities per sheet, 2-4. */
    private var activityCount = 3

    /** The generation inputs the current preview was rendered from. */
    @Volatile
    private var renderedFor: WorksheetPreviewKey? = null

    /**
     * Incremented per preview request. A render that finishes when the count
     * has moved on is stale and must not touch the pane.
     */
    private val renderGeneration = AtomicInteger(0)

    /**
     * Single worker for preview renders and generation. Serialised so two
     * rapid taps cannot interleave two PdfDocument writes to the same output
     * path, and so PDF work never runs on the main thread.
     */
    private val pdfExecutor = Executors.newSingleThreadExecutor()

    /** The bitmap currently shown in the pane; recycled when replaced. */
    private var previewBitmap: Bitmap? = null

    /** One tile of the worksheet-type grid. */
    private class TypeTile(
        val container: View,
        val name: TextView,
        val hindi: TextView,
        val code: TextView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorksheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack
        worksheetPdf = WorksheetPdf(this)
        flashcardPdf = FlashcardPdf(this)

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import_pdf -> {
                    startActivity(Intent(this, ImportLessonActivity::class.java))
                    true
                }
                R.id.action_check_proof -> {
                    startActivity(Intent(this, CheckAndProofActivity::class.java))
                    true
                }
                else -> false
            }
        }

        bindTypeGrid()
        bindActivityCountRow()
        refreshLessonPicker()
        refreshIntro()

        // The language bar. Switching rebuilds the lesson picker, because the
        // per-language contamination guard drops a different set of entries in
        // each language and a card count carried over would be wrong.
        LanguagePicker.bind(
            this,
            binding.languageBar.root,
            binding.languageBar.spinnerLanguage,
            binding.languageBar.textLanguageNote,
        ) {
            pack = (application as OlSaathiApplication).pack
            refreshLessonPicker()
            refreshIntro()
            binding.textStatus.text = ""
            requestPreview()
        }

        binding.btnGenerate.setOnClickListener {
            val lessonId = selectedLesson() ?: return@setOnClickListener
            if (lessonId.isEmpty()) {
                binding.textStatus.text =
                    "A worksheet is built around one lesson's text. Pick a lesson, " +
                        "or use Generate Flashcards for the teaching phrases."
                revealResult()
                return@setOnClickListener
            }
            produce("Worksheet") {
                worksheetPdf.generate(lessonId, pack, selectedType, activityCount)
            }
        }

        binding.btnFlashcards.setOnClickListener {
            val selected = selectedLesson() ?: return@setOnClickListener
            produce("Flashcards") {
                flashcardPdf.generate(selected.ifEmpty { null }, pack)
            }
        }

        binding.btnImportPdf.setOnClickListener {
            startActivity(Intent(this, ImportLessonActivity::class.java))
        }

        binding.btnSave.setOnClickListener {
            currentPdf?.let {
                pendingSave = it
                saveLauncher.launch(it.name)
            }
        }
        binding.btnSave.text = getString(R.string.btn_save_as_pdf)
        binding.btnSavePreview.setOnClickListener {
            currentPdf?.let {
                pendingSave = it
                saveLauncher.launch(it.name)
            }
        }
        binding.btnShare.setOnClickListener { currentPdf?.let { sharePdf(it) } }
        binding.btnPrint.setOnClickListener { currentPdf?.let { printPdf(it) } }

        binding.bottomNav.selectedItemId = R.id.nav_worksheet
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_teach -> {
                    startActivity(Intent(this, ClassroomActivity::class.java))
                    finish(); true
                }
                R.id.nav_lessons -> {
                    startActivity(Intent(this, LessonListActivity::class.java))
                    finish(); true
                }
                R.id.nav_worksheet -> true
                else -> false
            }
        }

        // The first adapter-selection fires before the spinner reports a
        // position; posting once covers the initial preview without missing it.
        binding.spinnerLesson.post { requestPreview() }
    }

    private fun typeTiles(): Map<WorksheetType, TypeTile> = mapOf(
        WorksheetType.TRACE_AND_CONNECT to TypeTile(
            binding.typeContainerTraceConnect,
            binding.typeNameTraceConnect,
            binding.typeHindiTraceConnect,
            binding.typeCodeTraceConnect
        ),
        WorksheetType.WORD_FLASH_STRIPS to TypeTile(
            binding.typeContainerWordStrips,
            binding.typeNameWordStrips,
            binding.typeHindiWordStrips,
            binding.typeCodeWordStrips
        ),
        WorksheetType.SCRIPT_TRACING to TypeTile(
            binding.typeContainerScriptTracing,
            binding.typeNameScriptTracing,
            binding.typeHindiScriptTracing,
            binding.typeCodeScriptTracing
        ),
        WorksheetType.CLASSROOM_DIALOGUES to TypeTile(
            binding.typeContainerDialogues,
            binding.typeNameDialogues,
            binding.typeHindiDialogues,
            binding.typeCodeDialogues
        )
    )

    /**
     * Bind the 2x2 type grid. English name, Hindi subtitle and NIPUN code are
     * read off the enum here; retyping them into the layout would create a
     * second copy of published CBSE outcomes that could drift from the sheet.
     */
    private fun bindTypeGrid() {
        val tiles = typeTiles()
        tiles.forEach { (type, tile) ->
            tile.name.text = type.displayName
            tile.hindi.text = type.hindiSubtitle
            tile.code.text = type.nipunCode
            tile.container.setOnClickListener {
                if (selectedType != type) {
                    selectedType = type
                    syncTypeSelection()
                    requestPreview()
                }
                // Re-tapping the selected tile is a no-op on purpose: the
                // preview, the file and the state already agree.
            }
        }
        syncTypeSelection()
    }

    private fun syncTypeSelection() {
        typeTiles().forEach { (type, tile) ->
            tile.container.isSelected = type == selectedType
        }
    }

    /** Bind the 2/3/4 activity-count tiles, defaulting to 3. */
    private fun bindActivityCountRow() {
        val tiles = mapOf(
            2 to binding.tileCount2,
            3 to binding.tileCount3,
            4 to binding.tileCount4
        )
        tiles.forEach { (count, tile) ->
            tile.text = count.toString()
            tile.isSelected = count == activityCount
            tile.setOnClickListener {
                if (activityCount != count) {
                    activityCount = count
                    tiles.forEach { (c, v) -> v.isSelected = c == count }
                    requestPreview()
                }
            }
        }
    }

    /**
     * Rebuild the lesson dropdown from whichever pack is loaded.
     *
     * Called again after a language switch: the guard drops a different set of
     * entries in each language, so "Teaching phrases (40 cards)" is not a
     * constant and must not be cached across languages.
     */
    private fun refreshLessonPicker() {
        val phraseCount = pack.entries().count { it.kind == "phrase" }
        lessonIds = listOf<String?>(null) + pack.lessonIds()

        val displayNames = lessonIds.map { id ->
            if (id == null) "Teaching phrases (" + phraseCount + " cards)"
            else id.replace("-", " ").replaceFirstChar { c -> c.uppercase() }
        }
        val previous = binding.spinnerLesson.selectedItemPosition
        binding.spinnerLesson.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayNames)
        if (previous in displayNames.indices) {
            binding.spinnerLesson.setSelection(previous, false)
        }
        binding.spinnerLesson.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    refreshNipun()
                    requestPreview()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = refreshNipun()
            }
        refreshNipun()
    }

    /**
     * Show which published NIPUN outcome the selected lesson is aligned to.
     *
     * A lesson can span more than one outcome; when it does, naming any single
     * one would be a claim the content does not support, so the card lists the
     * codes and drops the sentence rather than picking a favourite.
     */
    private fun refreshNipun() {
        val entries = pack.entries(selectedLesson()).filter { it.nipun.isNotEmpty() }
        val codes = entries.map { it.nipun }.distinct()
        if (codes.isEmpty()) {
            binding.cardNipun.visibility = View.GONE
            return
        }
        binding.cardNipun.visibility = View.VISIBLE
        binding.textNipunCode.text = codes.joinToString(", ")
        val outcomes = entries.map { it.nipunOutcome }.distinct().filter { it.isNotEmpty() }
        binding.textNipunOutcome.text =
            if (outcomes.size == 1) outcomes.first()
            else "This lesson spans " + codes.size + " outcomes."
    }

    /** Name the language in the intro rather than claiming Santali forever. */
    private fun refreshIntro() {
        val name = (application as OlSaathiApplication)
            .currentLanguageOption()?.english
        binding.textWorksheetIntro.text =
            if (name != null) getString(R.string.worksheet_intro_format, name)
            else getString(R.string.worksheet_intro_default)
    }

    /**
     * Queue a preview regeneration.
     *
     * Called whenever lesson, worksheet type or activity count changes. The
     * render runs off the main thread because generate() writes a file; the
     * result is applied on the main thread only if it is still the newest
     * request.
     */
    private fun requestPreview() {
        val lessonId = selectedLesson() ?: return
        if (lessonId.isEmpty()) {
            // The phrase deck produces no worksheet. Showing a mock of a sheet
            // that cannot exist would be the pane lying, so it is hidden.
            renderedFor = null
            binding.previewSection.visibility = View.GONE
            binding.textPreviewStatus.text = getString(R.string.preview_lesson_required)
            return
        }
        val key = WorksheetPreviewKey(lessonId, selectedType, activityCount)
        if (key == renderedFor) return

        // A new selection means the page on screen is no longer the file that
        // was generated, so "Save as PDF" under it would save the wrong sheet.
        binding.btnSavePreview.visibility = View.GONE
        binding.previewSection.visibility = View.VISIBLE
        binding.textPreviewStatus.text = getString(R.string.preview_generating)
        val generation = renderGeneration.incrementAndGet()
        pdfExecutor.execute {
            try {
                val pdf = worksheetPdf.generate(lessonId, pack, selectedType, activityCount)
                if (pdf == null || !pdf.exists()) {
                    showPreviewError(getString(R.string.preview_lesson_required))
                    return@execute
                }
                cleanupPreviewCache(keep = pdf.name)
                val bitmap = renderFirstPage(pdf)
                runOnUiThread {
                    if (renderGeneration.get() != generation) {
                        bitmap?.recycle()
                        return@runOnUiThread
                    }
                    renderedFor = key
                    showPreviewBitmap(
                        bitmap,
                        "Page 1 · " + pdf.name + " (" + (pdf.length() / 1024) + " KB)"
                    )
                }
            } catch (e: Exception) {
                showPreviewError(e.message ?: "Preview failed")
            }
        }
    }

    /**
     * Rasterise page 1 of the real PDF at the pane's size.
     *
     * The bitmap is created at the A4 fit dimensions (see
     * WorksheetPreviewGeometry), and render() with a null transform scales the
     * page into that bitmap preserving aspect ratio, so 1:1.414 holds as the
     * pane widens and the page is never stretched. ARGB_8888 because
     * Devanagari head-strokes and Ol Chiki diacritics need antialiasing; at
     * pane scale the memory cost stays small and the bitmap is recycled the
     * moment a newer render replaces it.
     */
    private fun renderFirstPage(pdf: File): Bitmap? {
        return try {
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val container = binding.previewContainer
                        // Portrait puts the page inside the scroll at full
                        // width, where the container has no height of its own
                        // until the image gives it one. Size by width alone
                        // there; the first render can also run before layout,
                        // so fall back to the screen width minus the padding.
                        val fullWidth = container.tag == "fullWidth"
                        val inset = container.paddingLeft + container.paddingRight +
                            (32 * resources.displayMetrics.density).toInt()
                        val paneW = container.width.takeIf { it > 0 }
                            ?.minus(container.paddingLeft + container.paddingRight)
                            ?: if (fullWidth) resources.displayMetrics.widthPixels - inset
                            else (resources.displayMetrics.widthPixels * 0.55f).toInt()
                        val paneH = if (fullWidth) paneW * 2
                            else container.height.takeIf { it > 0 }
                                ?: (resources.displayMetrics.heightPixels * 0.6f).toInt()
                        val fit = WorksheetPreviewGeometry.fitInside(
                            paneW.toFloat(), paneH.toFloat(),
                            WorksheetPreviewGeometry.A4_WIDTH_PT,
                            WorksheetPreviewGeometry.A4_HEIGHT_PT
                        )
                        val bitmap = Bitmap.createBitmap(
                            fit[2].toInt().coerceAtLeast(1),
                            fit[3].toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(
                            bitmap, null, null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Swap the pane content on the main thread, recycling the old bitmap. */
    private fun showPreviewBitmap(bitmap: Bitmap?, label: String) {
        val old = previewBitmap
        previewBitmap = bitmap
        binding.previewContainer.removeAllViews()
        old?.takeIf { !it.isRecycled && it != bitmap }?.recycle()
        if (bitmap != null && !bitmap.isRecycled) {
            // adjustViewBounds lets the page set its own height, which is
            // what gives the full-width portrait container its size.
            val iv = ImageView(this).apply {
                adjustViewBounds = true
                setImageBitmap(bitmap)
            }
            binding.previewContainer.addView(
                iv,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                )
            )
            binding.textPreviewStatus.text = label
        } else {
            binding.textPreviewStatus.text =
                getString(R.string.preview_unavailable, "Page 1 could not be rasterised.")
        }
    }

    private fun showPreviewError(message: String) {
        runOnUiThread {
            binding.previewContainer.removeAllViews()
            binding.textPreviewStatus.text =
                getString(R.string.preview_unavailable, message)
        }
    }

    /**
     * Run one PDF producer and report honestly.
     *
     * A producer returning null means it had nothing to print. That is shown as
     * such rather than as a success with a missing file. The build runs on the
     * pdf worker: the generator writes a file, and on a 2 GB tablet that must
     * never block the UI thread.
     */
    private fun produce(what: String, build: () -> File?) {
        binding.textStatus.text = "Generating " + what.lowercase() + "..."
        binding.btnGenerate.isEnabled = false
        binding.btnFlashcards.isEnabled = false
        binding.btnSave.visibility = View.GONE
        binding.btnShare.visibility = View.GONE
        binding.btnPrint.visibility = View.GONE
        pdfExecutor.execute {
            try {
                val pdf = build()
                if (pdf != null && pdf.exists()) {
                    cleanupPreviewCache(keep = pdf.name)
                }
                runOnUiThread { onProduced(what, pdf) }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.textStatus.text =
                        "Error: " + (e.message ?: e.javaClass.simpleName)
                    binding.btnGenerate.isEnabled = true
                    binding.btnFlashcards.isEnabled = true
                }
            }
        }
    }

    private fun onProduced(what: String, pdf: File?) {
        if (pdf != null && pdf.exists()) {
            currentPdf = pdf
            binding.textStatus.text =
                what + ": " + pdf.name + " (" + (pdf.length() / 1024) + " KB)"
            binding.btnSave.visibility = View.VISIBLE
            binding.btnShare.visibility = View.VISIBLE
            binding.btnPrint.visibility = View.VISIBLE
        } else {
            binding.textStatus.text = "Nothing to print for this selection."
        }
        binding.btnGenerate.isEnabled = true
        binding.btnFlashcards.isEnabled = true
        if (pdf != null && pdf.exists()) showGenerated(pdf) else revealResult()
    }

    /**
     * Put the file just generated on screen at once: render its first page
     * into the preview, show "Save as PDF" under it, and scroll up to it.
     * Flashcards are a different file from the worksheet preview, so they
     * are rendered here too rather than leaving the worksheet on show.
     */
    private fun showGenerated(pdf: File) {
        binding.previewSection.visibility = View.VISIBLE
        binding.textPreviewStatus.text = getString(R.string.preview_generating)
        pdfExecutor.execute {
            val bitmap = renderFirstPage(pdf)
            runOnUiThread {
                showPreviewBitmap(bitmap, "Page 1 · " + pdf.name + " (" + (pdf.length() / 1024) + " KB)")
                binding.btnSavePreview.visibility = View.VISIBLE
                binding.previewSection.post {
                    val r = android.graphics.Rect(0, 0, binding.previewSection.width, binding.previewSection.height)
                    binding.previewSection.requestRectangleOnScreen(r, true)
                }
            }
        }
    }

    /**
     * Scroll the result line and its Save/Share/Print buttons into view.
     * On a phone they land below the fold, under the button that was just
     * pressed, and a teacher who sees nothing change assumes nothing happened.
     */
    private fun revealResult() {
        binding.textStatus.post {
            val target = if (binding.btnPrint.visibility == View.VISIBLE) binding.btnPrint else binding.textStatus
            val r = android.graphics.Rect(0, 0, target.width, target.height)
            target.requestRectangleOnScreen(r, false)
        }
    }

    /**
     * Delete stale worksheet PDFs from the generator's cache directory, keeping
     * the named one. Flashcard files live directly in cacheDir, not here, so
     * this cannot touch a file share/print still needs.
     */
    private fun cleanupPreviewCache(keep: String) {
        val dir = File(cacheDir, "worksheets")
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("worksheet_") && f.name != keep) f.delete()
        }
    }

    /** The chosen lesson id, "" for the phrase deck, or null if unusable. */
    private fun selectedLesson(): String? {
        val idx = binding.spinnerLesson.selectedItemPosition
        if (idx < 0 || idx >= lessonIds.size) return null
        return lessonIds[idx] ?: ""
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

    private fun printPdf(file: File) {
        try {
            val printManager = getSystemService(PRINT_SERVICE) as PrintManager
            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
                    val info = PrintDocumentInfo.Builder(file.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        // Flashcard sheets run to several pages. A hardcoded 1
                        // would have printed only the first.
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build()
                    callback.onLayoutFinished(info, false)
                }
                override fun onWrite(
                    pages: Array<PageRange>, destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?, callback: WriteResultCallback
                ) {
                    try {
                        file.inputStream().use { input ->
                            java.io.FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) { callback.onWriteFailed(e.message) }
                }
            }
            printManager.print(file.name, printAdapter, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not print: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        // Invalidate any in-flight UI update, then stop the worker before the
        // views it would touch go away.
        renderGeneration.incrementAndGet()
        pdfExecutor.shutdownNow()
        previewBitmap?.takeIf { !it.isRecycled }?.recycle()
        previewBitmap = null
        super.onDestroy()
    }
}

/**
 * The generation inputs a preview was rendered from. Re-rendering is skipped
 * while the key is unchanged, so spinner rebuilds on language switch do not
 * force redundant renders of a sheet that has not changed.
 */
data class WorksheetPreviewKey(
    val lessonId: String,
    val type: WorksheetType,
    val activityCount: Int
)

/**
 * Pure A4 fit arithmetic, split out so the aspect-ratio rule is testable on
 * the JVM: the preview must keep 1:1.414 as the pane widens, never stretch.
 */
object WorksheetPreviewGeometry {
    const val A4_WIDTH_PT = 595f
    const val A4_HEIGHT_PT = 842f

    /**
     * Fit a page of [pageW] x [pageH] inside a pane of [paneW] x [paneH],
     * centered. Returns [left, top, width, height] in pane pixels.
     */
    fun fitInside(paneW: Float, paneH: Float, pageW: Float, pageH: Float): FloatArray {
        val scale = minOf(paneW / pageW, paneH / pageH)
        val w = pageW * scale
        val h = pageH * scale
        return floatArrayOf((paneW - w) / 2f, (paneH - h) / 2f, w, h)
    }
}
