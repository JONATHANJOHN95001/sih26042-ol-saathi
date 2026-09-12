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
import app.olsaathi.worksheet.SheetMaterial
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
            binding.form.textStatus.text = "Saved a copy of " + source.name
        } catch (e: Exception) {
            // A failed save must say so. Silently doing nothing looks
            // identical to a successful save that went somewhere unexpected.
            binding.form.textStatus.text = "Could not save: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Parallel to the spinner: "phrases", then the pack's lesson ids, then
     * the teacher's imported chapters ("imported-..."). Whatever is selected
     * is the material every question and card is made from.
     */
    private var materialKeys = listOf<String>()

    /** Selected worksheet type; the enum is the single source of the labels. */
    private var selectedType = WorksheetType.QUESTIONS

    /** Questions (or activities) per sheet, from the chips or typed in. */
    private var questionCount = 10

    /** The selected material, built once per selection and language. */
    private var materialCache: Pair<String, SheetMaterial>? = null

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
        val code: TextView,
        val selectedMark: TextView
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
        // The overflow ⋮ took the light theme's dark tint, which vanished on
        // the forest-green bar; white matches the back arrow and the title.
        binding.toolbar.overflowIcon?.mutate()?.setTint(android.graphics.Color.WHITE)

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

        // The home dashboard's material tiles open this screen with a type
        // already chosen.
        intent.getStringExtra(EXTRA_TYPE)?.let { name ->
            WorksheetType.values().firstOrNull { it.name == name }?.let { selectedType = it }
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
            binding.form.textStatus.text = ""
            requestPreview()
        }
        // On this screen the shared language bar sits inside its own card, so
        // it drops its strip background, and the card goes when the bar does.
        binding.languageBar.root.background = null
        (binding.languageBar.root.parent as? View)?.visibility =
            binding.languageBar.root.visibility

        // Every tap is a new seed, so every tap is a new set of questions or
        // cards from the same material; the set number printed on the sheet
        // tells two papers apart.
        binding.form.btnGenerate.setOnClickListener {
            val material = selectedMaterial() ?: return@setOnClickListener
            produce("Worksheet") {
                worksheetPdf.generate(material, pack, selectedType, questionCount, System.nanoTime())
            }
        }

        binding.form.btnFlashcards.setOnClickListener {
            val material = selectedMaterial() ?: return@setOnClickListener
            produce("Flashcards") {
                flashcardPdf.generate(material, pack, questionCount, System.nanoTime())
            }
        }

        binding.form.btnImportPdf.setOnClickListener {
            startActivity(Intent(this, ImportLessonActivity::class.java))
        }

        binding.form.btnSave.setOnClickListener {
            currentPdf?.let {
                pendingSave = it
                saveLauncher.launch(it.name)
            }
        }
        binding.form.btnSave.text = getString(R.string.btn_save_as_pdf)
        binding.btnSavePreview.setOnClickListener {
            currentPdf?.let {
                pendingSave = it
                saveLauncher.launch(it.name)
            }
        }
        binding.form.btnShare.setOnClickListener { currentPdf?.let { sharePdf(it) } }
        binding.form.btnPrint.setOnClickListener { currentPdf?.let { printPdf(it) } }

        BottomNavIcons.apply(binding.bottomNav)
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
        binding.form.spinnerLesson.post { requestPreview() }
    }

    private fun typeTiles(): Map<WorksheetType, TypeTile> = mapOf(
        WorksheetType.QUESTIONS to TypeTile(
            binding.form.typeContainerQuestions,
            binding.form.typeNameQuestions,
            binding.form.typeHindiQuestions,
            binding.form.typeCodeQuestions,
            binding.form.typeSelectedQuestions
        ),
        WorksheetType.TRACE_AND_CONNECT to TypeTile(
            binding.form.typeContainerTraceConnect,
            binding.form.typeNameTraceConnect,
            binding.form.typeHindiTraceConnect,
            binding.form.typeCodeTraceConnect,
            binding.form.typeSelectedTraceConnect
        ),
        WorksheetType.WORD_FLASH_STRIPS to TypeTile(
            binding.form.typeContainerWordStrips,
            binding.form.typeNameWordStrips,
            binding.form.typeHindiWordStrips,
            binding.form.typeCodeWordStrips,
            binding.form.typeSelectedWordStrips
        ),
        WorksheetType.SCRIPT_TRACING to TypeTile(
            binding.form.typeContainerScriptTracing,
            binding.form.typeNameScriptTracing,
            binding.form.typeHindiScriptTracing,
            binding.form.typeCodeScriptTracing,
            binding.form.typeSelectedScriptTracing
        ),
        WorksheetType.CLASSROOM_DIALOGUES to TypeTile(
            binding.form.typeContainerDialogues,
            binding.form.typeNameDialogues,
            binding.form.typeHindiDialogues,
            binding.form.typeCodeDialogues,
            binding.form.typeSelectedDialogues
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
            val chosen = type == selectedType
            tile.container.isSelected = chosen
            tile.selectedMark.visibility = if (chosen) View.VISIBLE else View.GONE
        }
    }

    /**
     * The number of questions: four chips, or a number typed in (1 to 40).
     * The note under them says how many this material can actually make, so
     * a teacher asking for 20 from a ten-line lesson is told, not surprised.
     */
    private fun bindActivityCountRow() {
        val chips = mapOf(
            5 to binding.form.chipCount5,
            10 to binding.form.chipCount10,
            15 to binding.form.chipCount15,
            20 to binding.form.chipCount20,
        )
        fun sync() {
            chips.forEach { (n, v) -> v.isSelected = n == questionCount }
            binding.form.editCount.isSelected = questionCount !in chips.keys
            refreshCountNote()
        }
        chips.forEach { (n, v) ->
            v.setOnClickListener {
                if (questionCount != n) {
                    questionCount = n
                    binding.form.editCount.setText("")
                    sync()
                    requestPreview()
                }
            }
        }
        binding.form.editCount.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                val n = s?.toString()?.toIntOrNull() ?: return
                val clamped = n.coerceIn(1, 40)
                if (clamped != questionCount) {
                    questionCount = clamped
                    sync()
                    requestPreview()
                }
            }
        })
        sync()
    }

    /** "This lesson can make up to N" for the selected material and type. */
    private fun refreshCountNote() {
        val material = selectedMaterial() ?: return
        val statements = material.lines.count { it.kind != "check" }
        val cap = if (selectedType == WorksheetType.QUESTIONS)
            app.olsaathi.worksheet.QuestionGenerator.capacity(material.lines) else statements
        val noun = if (selectedType == WorksheetType.QUESTIONS) "questions" else "activities"
        // A count this material cannot fill is not offered: the number on
        // the sheet is always the number chosen.
        listOf(5 to binding.form.chipCount5, 10 to binding.form.chipCount10,
            15 to binding.form.chipCount15, 20 to binding.form.chipCount20).forEach { (n, chip) ->
            chip.isEnabled = n <= cap
            chip.alpha = if (n <= cap) 1f else 0.35f
        }
        val effective = minOf(questionCount, cap)
        binding.form.btnGenerate.text = "📄   Generate Worksheet  ·  $effective $noun"
        binding.form.textCountNote.text = when {
            cap == 0 -> "This material has nothing to make $noun from."
            questionCount > cap -> "\"${material.title}\" makes at most $cap $noun of this type, so the sheet " +
                "has $cap. For more, pick a bigger material, such as a whole NCERT chapter."
            else -> "$questionCount $noun from \"${material.title}\" (it can make up to $cap). " +
                "Flashcards: ${minOf(questionCount, statements)} cards."
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
        val imported = SheetMaterial.listImported(this, pack.languageCode)
        materialKeys = listOf("phrases") + pack.lessonIds() + imported.map { it.first }
        val importedTitles = imported.toMap()
        val displayNames = materialKeys.map { key ->
            when {
                key == "phrases" -> "Teaching phrases ($phraseCount lines)"
                key.startsWith("imported-") -> (importedTitles[key] ?: "Imported chapter").let { t ->
                    (if (t.contains(" · Chapter ")) "📘 " else "📄 ") + t
                }
                else -> key.replace("-", " ").replaceFirstChar { c -> c.uppercase() }
            }
        }
        val previousKey = selectedKey()
        // A chapter just downloaded arrives selected; used once.
        val wanted = intent.getStringExtra(EXTRA_MATERIAL_KEY)?.also { intent.removeExtra(EXTRA_MATERIAL_KEY) }
        materialCache = null
        binding.form.spinnerLesson.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayNames)
        val restore = materialKeys.indexOf(wanted).takeIf { it >= 0 }
            ?: materialKeys.indexOf(previousKey).takeIf { it >= 0 }
            // A lesson, not the phrase deck, is the better first selection.
            ?: materialKeys.indexOfFirst { it != "phrases" && !it.startsWith("imported-") }.takeIf { it >= 0 }
            ?: 0
        binding.form.spinnerLesson.setSelection(restore, false)
        binding.form.spinnerLesson.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    refreshNipun()
                    refreshCountNote()
                    requestPreview()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = refreshNipun()
            }
        refreshNipun()
        refreshCountNote()
    }

    /**
     * Show which published NIPUN outcome the selected lesson is aligned to.
     *
     * A lesson can span more than one outcome; when it does, naming any single
     * one would be a claim the content does not support, so the card lists the
     * codes and drops the sentence rather than picking a favourite.
     */
    private fun refreshNipun() {
        val entries = selectedMaterial()?.lines.orEmpty().filter { it.nipun.isNotEmpty() }
        val codes = entries.map { it.nipun }.distinct()
        refreshSelectionCounts(codes.size)
        if (codes.isEmpty()) {
            binding.form.cardNipun.visibility = View.GONE
            return
        }
        binding.form.cardNipun.visibility = View.VISIBLE
        showNipunChips(codes)
        val outcomes = entries.map { it.nipunOutcome }.distinct().filter { it.isNotEmpty() }
        binding.form.textNipunOutcome.text =
            if (outcomes.size == 1) outcomes.first()
            else "This lesson spans " + codes.size + " outcomes."
    }

    /** One label chip per NIPUN code. Labels, so they are not tappable. */
    private fun showNipunChips(codes: List<String>) {
        val group = binding.form.chipGroupNipun
        group.removeAllViews()
        codes.forEach { code ->
            group.addView(com.google.android.material.chip.Chip(this).apply {
                text = code
                isClickable = false
                isCheckable = false
                textSize = 12f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@WorksheetActivity, R.font.plus_jakarta_sans_700)
                setTextColor(0xFF1E4B38.toInt())
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(0xFFEDF6F0.toInt())
                chipStrokeColor = android.content.res.ColorStateList.valueOf(0xFFCBE5D4.toInt())
                chipStrokeWidth = resources.displayMetrics.density
                chipMinHeight = 28 * resources.displayMetrics.density
                shapeAppearanceModel = shapeAppearanceModel.withCornerSize(8 * resources.displayMetrics.density)
                setEnsureMinTouchTargetSize(false)
            })
        }
    }

    /**
     * The counts under the lesson picker and on the flashcard button.
     *
     * Where the design mockup printed fixed numbers ("10 Cards Deck"), these
     * are counted from the loaded pack with the same filters the generators
     * use, so the button cannot promise more cards than the sheet holds.
     */
    private fun refreshSelectionCounts(outcomeCount: Int) {
        val material = selectedMaterial() ?: return
        val statements = material.lines.count { it.kind != "check" }
        val checks = material.lines.count { it.kind == "check" }
        val outcomes = if (outcomeCount == 1) "1 NIPUN outcome" else "$outcomeCount NIPUN outcomes"
        binding.form.textLessonSubtitle.text = "$statements lines" +
            (if (checks > 0) " · $checks questions" else "") +
            (if (outcomeCount > 0) " · $outcomes" else "")
        val cards = minOf(questionCount, statements)
        binding.form.btnFlashcards.text = "🎴   " + getString(R.string.btn_generate_flashcards) +
            "  ·  " + cards + if (cards == 1) " card" else " cards"
    }

    /** Name the language in the intro rather than claiming Santali forever. */
    private fun refreshIntro() {
        val name = (application as OlSaathiApplication)
            .currentLanguageOption()?.english
        binding.form.textIntroKicker.text =
            "✨ BILINGUAL CLASSROOM KIT  •  HINDI + " + (name ?: "TARGET").uppercase()
        binding.form.textWorksheetIntro.text =
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
        val material = selectedMaterial() ?: return
        refreshSelectionCounts(material.lines.map { it.nipun }.filter { it.isNotEmpty() }.distinct().size)
        refreshCountNote()
        val key = WorksheetPreviewKey(material.key, selectedType, questionCount)
        if (key == renderedFor) return

        // A new selection means the page on screen is no longer the file that
        // was generated, so "Save as PDF" under it would save the wrong sheet.
        binding.btnSavePreview.visibility = View.GONE
        binding.previewSection.visibility = View.VISIBLE
        binding.textPreviewStatus.text = getString(R.string.preview_generating)
        val generation = renderGeneration.incrementAndGet()
        pdfExecutor.execute {
            try {
                // One possible set, the same while the selection is unchanged;
                // Generate makes a new one each time and shows it here.
                val pdf = worksheetPdf.generate(material, pack, selectedType, questionCount, key.hashCode().toLong())
                if (pdf == null || !pdf.exists()) {
                    showPreviewError("This material has nothing to make this sheet from.")
                    return@execute
                }
                cleanupPreviewCache(keep = pdf.name)
                val pages = worksheetPdf.lastResult?.pages ?: 1
                val bitmap = renderFirstPage(pdf)
                runOnUiThread {
                    if (renderGeneration.get() != generation) {
                        bitmap?.recycle()
                        return@runOnUiThread
                    }
                    renderedFor = key
                    showPreviewBitmap(
                        bitmap,
                        "Preview, page 1 of $pages · tap Generate for a new set"
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
        binding.form.textStatus.text = "Generating " + what.lowercase() + "..."
        binding.form.btnGenerate.isEnabled = false
        binding.form.btnFlashcards.isEnabled = false
        binding.form.btnSave.visibility = View.GONE
        binding.form.btnShare.visibility = View.GONE
        binding.form.btnPrint.visibility = View.GONE
        pdfExecutor.execute {
            try {
                val pdf = build()
                val line = if (pdf != null && pdf.exists()) resultLine(what, pdf) else null
                if (pdf != null && pdf.exists()) {
                    cleanupPreviewCache(keep = pdf.name)
                }
                runOnUiThread { onProduced(what, pdf, line) }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.form.textStatus.text =
                        "Error: " + (e.message ?: e.javaClass.simpleName)
                    binding.form.btnGenerate.isEnabled = true
                    binding.form.btnFlashcards.isEnabled = true
                }
            }
        }
    }

    private fun onProduced(what: String, pdf: File?, line: String?) {
        if (pdf != null && pdf.exists()) {
            currentPdf = pdf
            binding.form.textStatus.text = line ?: resultLine(what, pdf)
            binding.form.btnSave.visibility = View.VISIBLE
            binding.form.btnShare.visibility = View.VISIBLE
            binding.form.btnPrint.visibility = View.VISIBLE
        } else {
            binding.form.textStatus.text = "Nothing to print for this selection."
        }
        binding.form.btnGenerate.isEnabled = true
        binding.form.btnFlashcards.isEnabled = true
        if (pdf != null && pdf.exists()) showGenerated(pdf) else revealResult()
    }

    /** What was generated, and anything short of what was asked for. */
    private fun resultLine(what: String, pdf: File): String {
        val kb = pdf.length() / 1024
        if (what == "Worksheet") {
            val r = worksheetPdf.lastResult ?: return "Worksheet ready ($kb KB)."
            val noun = if (selectedType == WorksheetType.QUESTIONS) "questions" else "activities"
            val short = if (r.items < r.requested)
                " You asked for ${r.requested}; this material can make ${r.capacity}." else ""
            return "Worksheet, set ${r.setNo}: ${r.items} $noun on ${r.pages} " +
                (if (r.pages == 1) "page" else "pages") + ", with answer key where it applies ($kb KB).$short"
        }
        val r = flashcardPdf.lastResult ?: return "Flashcards ready ($kb KB)."
        val short = if (r.cards < r.requested)
            " You asked for ${r.requested}; this material has ${r.capacity} lines." else ""
        return "Flashcards, set ${r.setNo}: ${r.cards} cards on ${r.pages} " +
            (if (r.pages == 1) "page" else "pages") + ", each with a new NIPUN classroom task ($kb KB).$short"
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
        binding.form.textStatus.post {
            val target = if (binding.form.btnPrint.visibility == View.VISIBLE) binding.form.btnPrint else binding.form.textStatus
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
        val saving = currentPdf?.name
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("worksheet_") && f.name != keep && f.name != saving) f.delete()
        }
    }

    /** The selected material's key, or null before the picker is filled. */
    private fun selectedKey(): String? {
        val idx = binding.form.spinnerLesson.selectedItemPosition
        return materialKeys.getOrNull(idx)
    }

    /** The selected lesson, phrase deck or imported chapter, as lines. */
    private fun selectedMaterial(): SheetMaterial? {
        val key = selectedKey() ?: return null
        val cacheKey = pack.languageCode + "/" + key
        materialCache?.let { if (it.first == cacheKey) return it.second }
        val m = when {
            key == "phrases" -> SheetMaterial.fromPack(pack, "")
            key.startsWith("imported-") -> SheetMaterial.loadImported(this, key, pack)
            else -> SheetMaterial.fromPack(pack, key)
        } ?: return null
        materialCache = cacheKey to m
        return m
    }

    override fun onResume() {
        super.onResume()
        // A chapter imported since this screen was built joins the list.
        val keysBefore = materialKeys
        val now = listOf("phrases") + pack.lessonIds() +
            SheetMaterial.listImported(this, pack.languageCode).map { it.first }
        if (keysBefore.isNotEmpty() && now != keysBefore) {
            refreshLessonPicker()
            requestPreview()
        }
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

    companion object {
        /** A [WorksheetType] name to preselect. */
        const val EXTRA_TYPE = "worksheet_type"

        /** A material key to preselect, such as a chapter just downloaded. */
        const val EXTRA_MATERIAL_KEY = "material_key"
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
