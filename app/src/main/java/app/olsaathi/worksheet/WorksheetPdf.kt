package app.olsaathi.worksheet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import app.olsaathi.content.VerifiedContentPack
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a bilingual A4 worksheet PDF using [PdfDocument].
 *
 * Phase 5 requirement: One page per lesson containing:
 *   - Title
 *   - The Hindi line and the Ol Chiki line for each sentence
 *   - Generous space to write
 *   - The NIPUN outcome code in the footer
 *
 * N4: Never hand-assemble a PDF. Use android.graphics.pdf.PdfDocument.
 * N5: Bundle the fonts — load both typefaces from assets.
 * N2: If either font fails to load, throw — never fall back to the
 *     default font, which would render boxes.
 * N3: Never write UTF-8 text through an ASCII path.
 */
class WorksheetPdf(private val context: Context) {

    /** A4 at 72 DPI: 595 × 842 points. */
    companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        private const val MARGIN_LEFT = 50f
        private const val MARGIN_RIGHT = 50f
        private const val MARGIN_TOP = 50f
        private const val MARGIN_BOTTOM = 60f
        private const val LINE_HEIGHT_WRITE = 55f
        private const val TAG = "WorksheetPdf"
    }

    // ── Font loading: throw on failure (N2, N5) ─────────────────────

    private val devanagariTypeface: Typeface by lazy {
        try {
            Typeface.createFromAsset(context.assets, "fonts/NotoSansDevanagari-Regular.ttf")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Could not load Devanagari font", e)
            throw RuntimeException(
                "NotoSansDevanagari-Regular.ttf failed to load from assets. " +
                    "The worksheet cannot render Hindi text without it.", e
            )
        }
    }

    private val olChikiTypeface: Typeface by lazy {
        try {
            Typeface.createFromAsset(context.assets, "fonts/NotoSansOlChiki-Regular.ttf")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Could not load Ol Chiki font", e)
            throw RuntimeException(
                "NotoSansOlChiki-Regular.ttf failed to load from assets. " +
                    "The worksheet cannot render Santali text without it.", e
            )
        }
    }

    // ── Paint objects ───────────────────────────────────────────────

    private val titlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val lessonTitlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val hindiPaint = Paint().apply {
        color = Color.BLACK
        textSize = 14f
        typeface = devanagariTypeface
        isAntiAlias = true
    }

    private val olChikiPaint = Paint().apply {
        color = Color.parseColor("#1A237E") // deep indigo
        textSize = 16f
        typeface = olChikiTypeface
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.GRAY
        textSize = 10f
        isAntiAlias = true
    }

    private val footerPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 9f
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 0.5f
    }

    private val sampleStampPaint = Paint().apply {
        color = Color.parseColor("#C62828") // alarming red
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        alpha = 180
    }

    /**
     * Generate a worksheet PDF for the given lesson.
     *
     * @param lessonId The lesson to generate a worksheet for
     * @param pack The loaded content pack
     * @return The generated PDF file, or null if no lesson entries found
     */
    fun generate(lessonId: String, pack: VerifiedContentPack): File? {
        val entries = pack.entries(lessonId).filter { it.kind == "lesson" }
        if (entries.isEmpty()) return null

        val document = PdfDocument()
        val isSample = pack.isSample

        // Collect the distinct NIPUN codes from lesson entries
        val nipunCodes = entries.map { it.nipun }.distinct().filter { it.isNotEmpty() }
        val nipunFooter = buildString {
            append("NIPUN Bharat, Foundational Literacy and Numeracy")
            if (nipunCodes.isNotEmpty()) {
                append("  •  ")
                append(nipunCodes.joinToString(", "))
            }
        }

        var pageNum = 1
        var y = MARGIN_TOP

        // Start first page
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        // ── Header ────────────────────────────────────────────────
        // Title
        canvas.drawText("Bilingual Worksheet — Hindi & Santali", MARGIN_LEFT, y, titlePaint)
        y += 24f

        // Lesson title (human-readable)
        val displayTitle = lessonId.replace("-", " ").replaceFirstChar { it.uppercase() }
        canvas.drawText(displayTitle, MARGIN_LEFT, y, lessonTitlePaint)
        y += 18f

        // Name line
        canvas.drawText("Name: _____________________________", MARGIN_LEFT, y, labelPaint)
        y += 16f

        // Divider
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
        y += 20f

        // ── Lesson lines ──────────────────────────────────────────
        for (entry in entries) {
            // Check if we need a new page (enough room for Hindi + Ol Chiki + writing space)
            val needed = 14f + 18f + 14f + 20f + LINE_HEIGHT_WRITE + 20f
            if (y + needed > PAGE_HEIGHT - MARGIN_BOTTOM) {
                // Footer on current page
                drawFooter(canvas, pageNum, nipunFooter)
                if (isSample) drawSampleStamp(canvas)
                document.finishPage(page)

                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN_TOP
            }

            // Hindi text
            canvas.drawText("Hindi:", MARGIN_LEFT, y, labelPaint)
            y += 14f
            canvas.drawText(entry.source, MARGIN_LEFT, y, hindiPaint)
            y += 20f

            // Ol Chiki text
            canvas.drawText("Santali (Ol Chiki):", MARGIN_LEFT, y, labelPaint)
            y += 14f
            canvas.drawText(entry.target, MARGIN_LEFT, y, olChikiPaint)
            y += 22f

            // Writing lines (generous space for the child to write)
            canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
            y += LINE_HEIGHT_WRITE
            canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
            y += 15f
        }

        // ── Footer on last page ──────────────────────────────────
        drawFooter(canvas, pageNum, nipunFooter)
        if (isSample) drawSampleStamp(canvas)
        document.finishPage(page)

        // ── Save to worksheets subdirectory in cache ─────────────
        val worksheetsDir = File(context.cacheDir, "worksheets")
        if (!worksheetsDir.exists()) worksheetsDir.mkdirs()
        val file = File(worksheetsDir, "worksheet_${lessonId}.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        Log.i(TAG, "Worksheet generated: ${file.name} (${file.length()} bytes, $pageNum pages)")
        return file
    }

    /**
     * Draw the footer with NIPUN code and page number.
     */
    private fun drawFooter(canvas: Canvas, pageNum: Int, nipunText: String) {
        val footerY = PAGE_HEIGHT - 20f
        val footerLine = "$nipunText  •  Page $pageNum"
        canvas.drawText(footerLine, MARGIN_LEFT, footerY, footerPaint)
    }

    /**
     * Stamp "SAMPLE DATA — NOT A REAL TRANSLATION" diagonally across
     * every page when the pack is placeholder content.
     *
     * A fake worksheet must never be able to look like a real one.
     */
    private fun drawSampleStamp(canvas: Canvas) {
        canvas.save()
        canvas.rotate(
            -30f,
            (PAGE_WIDTH / 2).toFloat(),
            (PAGE_HEIGHT / 2).toFloat()
        )
        canvas.drawText(
            "SAMPLE DATA — NOT A REAL TRANSLATION",
            60f,
            (PAGE_HEIGHT / 2).toFloat(),
            sampleStampPaint
        )
        canvas.restore()
    }
}
