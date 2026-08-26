package app.olsaathi.ui

import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import app.olsaathi.OlSaathiApplication
import app.olsaathi.R
import app.olsaathi.content.VerifiedContentPack
import app.olsaathi.databinding.ActivityWorksheetBinding
import app.olsaathi.worksheet.FlashcardPdf
import app.olsaathi.worksheet.WorksheetPdf
import java.io.File

/**
 * Teaching-materials screen.
 *
 * The problem statement asks for "bilingual teaching materials such as
 * worksheets and flashcards", so this screen produces both from the same
 * lesson selection, and offers share and print for either.
 *
 * The first spinner entry is the teaching-phrase deck rather than a lesson.
 * Flashcards can be made from it; worksheets cannot, because a worksheet is
 * built around one lesson's text. Choosing it and asking for a worksheet says
 * so rather than producing an empty page.
 */
class WorksheetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorksheetBinding
    private lateinit var pack: VerifiedContentPack
    private lateinit var worksheetPdf: WorksheetPdf
    private lateinit var flashcardPdf: FlashcardPdf
    private var currentPdf: File? = null

    /** Parallel to the spinner. A null means the teaching-phrase deck. */
    private var lessonIds = listOf<String?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorksheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack
        worksheetPdf = WorksheetPdf(this)
        flashcardPdf = FlashcardPdf(this)

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Overflow menu → Check & Proof
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_check_proof -> {
                    startActivity(Intent(this, CheckAndProofActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Build the picker: the phrase deck, then every lesson in the pack
        val phraseCount = pack.entries().count { it.kind == "phrase" }
        lessonIds = listOf<String?>(null) + pack.lessonIds()

        val displayNames = lessonIds.map { id ->
            if (id == null) "Teaching phrases (" + phraseCount + " cards)"
            else id.replace("-", " ").replaceFirstChar { c -> c.uppercase() }
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayNames)
        binding.spinnerLesson.adapter = adapter

        binding.btnGenerate.setOnClickListener {
            val lessonId = selectedLesson() ?: return@setOnClickListener
            if (lessonId.isEmpty()) {
                binding.textStatus.text =
                    "A worksheet is built around one lesson's text. Pick a lesson, " +
                        "or use Generate Flashcards for the teaching phrases."
                return@setOnClickListener
            }
            produce("Worksheet") { worksheetPdf.generate(lessonId, pack) }
        }

        binding.btnFlashcards.setOnClickListener {
            val lessonId = selectedLesson() ?: return@setOnClickListener
            produce("Flashcards") {
                flashcardPdf.generate(lessonId.ifEmpty { null }, pack)
            }
        }

        binding.btnShare.setOnClickListener { currentPdf?.let { sharePdf(it) } }
        binding.btnPrint.setOnClickListener { currentPdf?.let { printPdf(it) } }

        // ── Bottom nav ────────────────────────────────────────────
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
    }

    /**
     * The chosen lesson id, "" for the phrase deck, or null if the spinner is
     * in an unusable state.
     */
    private fun selectedLesson(): String? {
        val idx = binding.spinnerLesson.selectedItemPosition
        if (idx < 0 || idx >= lessonIds.size) return null
        return lessonIds[idx] ?: ""
    }

    /**
     * Run one PDF producer and report honestly.
     *
     * A producer returning null means it had nothing to print. That is shown as
     * such rather than as a success with a missing file.
     */
    private fun produce(what: String, build: () -> File?) {
        binding.textStatus.text = "Generating " + what.lowercase() + "..."
        binding.btnGenerate.isEnabled = false
        binding.btnFlashcards.isEnabled = false
        binding.btnShare.visibility = View.GONE
        binding.btnPrint.visibility = View.GONE
        try {
            val pdf = build()
            if (pdf != null && pdf.exists()) {
                currentPdf = pdf
                binding.textStatus.text = what + ": " + pdf.name + " (" + (pdf.length() / 1024) + " KB)"
                binding.btnShare.visibility = View.VISIBLE
                binding.btnPrint.visibility = View.VISIBLE
            } else {
                binding.textStatus.text = "Nothing to print for this selection."
            }
        } catch (e: Exception) {
            binding.textStatus.text = "Error: " + e.message
        } finally {
            binding.btnGenerate.isEnabled = true
            binding.btnFlashcards.isEnabled = true
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
}
