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
import app.olsaathi.worksheet.WorksheetPdf
import java.io.File

/**
 * Worksheet generation screen.
 *
 * Phase 5 requirement: Select a lesson, generate a bilingual A4
 * worksheet with Hindi + Ol Chiki, and offer share/print.
 * N5: Uses PdfDocument, never hand-assembles PDF.
 */
class WorksheetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorksheetBinding
    private lateinit var pack: VerifiedContentPack
    private lateinit var worksheetPdf: WorksheetPdf

    private var currentPdf: File? = null
    private var lessonIds = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorksheetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack
        worksheetPdf = WorksheetPdf(this)

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Build lesson list
        lessonIds = pack.lessonIds()
        if (lessonIds.isEmpty()) {
            binding.textStatus.text = "No lessons found in the content pack."
            binding.btnGenerate.isEnabled = false
            return
        }

        val displayNames = lessonIds.map { id ->
            id.replace("-", " ").replaceFirstChar { it.uppercase() }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayNames)
        binding.spinnerLesson.adapter = adapter

        // Generate button
        binding.btnGenerate.setOnClickListener {
            val idx = binding.spinnerLesson.selectedItemPosition
            if (idx < 0 || idx >= lessonIds.size) return@setOnClickListener

            val lessonId = lessonIds[idx]
            binding.textStatus.text = "Generating worksheet..."
            binding.btnGenerate.isEnabled = false

            try {
                val pdf = worksheetPdf.generate(lessonId, pack)
                if (pdf != null && pdf.exists()) {
                    currentPdf = pdf
                    binding.textStatus.text = "Worksheet generated: ${pdf.name} (${pdf.length() / 1024} KB)"
                    binding.btnShare.visibility = View.VISIBLE
                    binding.btnPrint.visibility = View.VISIBLE
                } else {
                    binding.textStatus.text = "No lesson content found for this lesson."
                }
            } catch (e: Exception) {
                binding.textStatus.text = "Error generating worksheet: ${e.message}"
            } finally {
                binding.btnGenerate.isEnabled = true
            }
        }

        // Share button
        binding.btnShare.setOnClickListener {
            val pdf = currentPdf ?: return@setOnClickListener
            sharePdf(pdf)
        }

        // Print button
        binding.btnPrint.setOnClickListener {
            val pdf = currentPdf ?: return@setOnClickListener
            printPdf(pdf)
        }
    }

    private fun sharePdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share worksheet"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun printPdf(file: File) {
        try {
            val printManager = getSystemService(PRINT_SERVICE) as PrintManager
            val documentName = "Worksheet: ${file.name}"

            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder(file.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback.onLayoutFinished(info, false)
                }

                override fun onWrite(
                    pages: Array<PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    try {
                        file.inputStream().use { input ->
                            java.io.FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    }
                }
            }
            printManager.print(documentName, printAdapter, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not print: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
