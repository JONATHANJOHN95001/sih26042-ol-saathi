package app.olsaathi.worksheet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.core.content.ContextCompat
import app.olsaathi.content.VerifiedContentPack
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a printable sheet of bilingual flashcards.
 *
 * The problem statement asks for "bilingual teaching materials such as
 * worksheets and flashcards". The worksheet was built; this is the other half.
 *
 * A card carries the Hindi and the Santali for one item, the English gloss so a
 * teacher who reads neither script can still follow, and the NIPUN Bharat
 * domain it belongs to. Six cards to an A4 page with dashed cut guides,
 * because the target school has scissors rather than a card printer.
 *
 * Same rules as the worksheet:
 *   N1  Entries with no Santali text are skipped rather than printed empty.
 *   N2  Fonts throw on failure. A missing font must not silently render boxes.
 *   N3  Never write UTF-8 text through an ASCII path.
 *   N4  Never hand-assemble a PDF. Use PdfDocument.
 */
class FlashcardPdf(private val context: Context) {

    companion object {
        /** A4 at 72 DPI. */
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842

        private const val MARGIN = 36f
        private const val COLUMNS = 2
        private const val ROWS = 3
        const val CARDS_PER_PAGE = COLUMNS * ROWS

        private const val TAG = "FlashcardPdf"
    }

    // ── Fonts: throw rather than fall back (N2) ──────────────────────

    private fun asset(name: String): Typeface = try {
        Typeface.createFromAsset(context.assets, "fonts/" + name)
    } catch (e: Exception) {
        Log.e(TAG, "FATAL: could not load " + name, e)
        throw RuntimeException(
            name + " failed to load from assets. Flashcards cannot render without it.", e
        )
    }

    private val devanagariTypeface: Typeface by lazy { asset("NotoSansDevanagari-Regular.ttf") }
    private val olChikiTypeface: Typeface by lazy { asset("NotoSansOlChiki-Regular.ttf") }

    // ── Paints ──────────────────────────────────────────────────────

    private val cutPaint = Paint().apply {
        color = Color.rgb(170, 170, 170)
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        isAntiAlias = true
    }

    private val domainPaint = Paint().apply {
        color = Color.rgb(120, 120, 120)
        textSize = 7.5f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val hindiPaint = Paint().apply {
        color = Color.BLACK
        textSize = 17f
        typeface = devanagariTypeface
        isAntiAlias = true
    }

    private val olChikiPaint = Paint().apply {
        color = Color.rgb(20, 60, 130)
        textSize = 19f
        typeface = olChikiTypeface
        isAntiAlias = true
    }

    private val glossPaint = Paint().apply {
        color = Color.rgb(90, 90, 90)
        textSize = 9.5f
        isAntiAlias = true
    }

    private val provenancePaint = Paint().apply {
        color = Color.rgb(150, 150, 150)
        textSize = 6.5f
        isAntiAlias = true
    }

    private val rulePaint = Paint().apply {
        color = Color.rgb(210, 210, 210)
        strokeWidth = 0.6f
        isAntiAlias = true
    }

    private val stampPaint = Paint().apply {
        color = Color.rgb(200, 30, 30)
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    /**
     * Build the flashcard sheet.
     *
     * @param lessonId lesson to draw cards for, or null for the phrase deck
     * @return the written file, or null if there was nothing to print
     */
    fun generate(lessonId: String?, pack: VerifiedContentPack): File? {
        val cards = pack.entries()
            .filter { if (lessonId == null) it.kind == "phrase" else it.lesson == lessonId }
            .filter { it.target.isNotBlank() }
            .sortedBy { it.id }

        if (cards.isEmpty()) {
            Log.w(TAG, "No printable cards for lesson=" + lessonId)
            return null
        }

        val document = PdfDocument()
        val cardWidth = (PAGE_WIDTH - 2 * MARGIN) / COLUMNS
        val cardHeight = (PAGE_HEIGHT - 2 * MARGIN) / ROWS

        cards.chunked(CARDS_PER_PAGE).forEachIndexed { pageIndex, pageCards ->
            val info = PdfDocument.PageInfo
                .Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1)
                .create()
            val page = document.startPage(info)
            val canvas = page.canvas

            if (pack.isSample) drawSampleStamp(canvas)

            pageCards.forEachIndexed { i, entry ->
                val left = MARGIN + (i % COLUMNS) * cardWidth
                val top = MARGIN + (i / COLUMNS) * cardHeight
                drawCard(canvas, entry, left, top, cardWidth, cardHeight, pack.isSample, pack.serviceName)
            }

            document.finishPage(page)
        }

        val name = if (lessonId == null) "flashcards-phrases" else "flashcards-" + lessonId
        val file = File(context.cacheDir, name + ".pdf")
        return try {
            FileOutputStream(file).use { document.writeTo(it) }
            document.close()
            Log.i(TAG, "Wrote " + cards.size + " cards, " + file.length() + " bytes")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Could not write flashcard PDF", e)
            document.close()
            null
        }
    }

    private fun drawCard(
        canvas: Canvas,
        entry: VerifiedContentPack.PackEntry,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        isSample: Boolean,
        /** Read from the pack, so a card printed after a content swap names the
         *  service that actually produced the line rather than the last one. */
        serviceName: String,
    ) {
        canvas.drawRect(left, top, left + width, top + height, cutPaint)

        val pad = 14f
        val innerWidth = width - 2 * pad
        var y = top + pad + 9f

        // NIPUN domain, so every card is traceable to the framework
        val domain = entry.nipunDomain.ifEmpty { entry.nipun }
        canvas.drawText(domain.uppercase(), left + pad, y, domainPaint)
        y += 6f
        canvas.drawLine(left + pad, y, left + width - pad, y, rulePaint)

        // Picture first, then the two scripts, then the gloss. If there is no
        // picture the text uses the space instead of leaving a gap.
        val hasIcon = drawIcon(
            canvas,
            entry.image,
            left + width / 2f,
            top + height * 0.30f,
            height * 0.26f
        )
        val hindiY = if (hasIcon) 0.53f else 0.34f
        val olY = if (hasIcon) 0.68f else 0.56f
        val glossY = if (hasIcon) 0.80f else 0.74f

        drawCentred(canvas, entry.source, left + pad, innerWidth, top + height * hindiY, hindiPaint)
        drawCentred(canvas, entry.target, left + pad, innerWidth, top + height * olY, olChikiPaint)
        drawCentred(canvas, entry.en, left + pad, innerWidth, top + height * glossY, glossPaint)

        val label = when {
            isSample -> "SAMPLE DATA, not a real translation"
            entry.reviewedBy.isNotEmpty() -> "Checked by " + entry.reviewedBy
            serviceName.isNotEmpty() -> "Machine translation, " + serviceName
            else -> "Machine translation"
        }
        canvas.drawText(label, left + pad, top + height - pad, provenancePaint)
    }

    /**
     * Draw the card's picture, if it has one.
     *
     * A six-year-old learning to read cannot decode the card yet. The picture
     * is what lets them connect the word they hear to the marks on the paper,
     * so it sits above the text rather than beside it.
     *
     * Returns true if something was drawn. A missing or unresolvable drawable
     * is skipped silently and the text simply moves up, because a card with no
     * picture is still a usable card.
     */
    private fun drawIcon(canvas: Canvas, name: String?, cx: Float, cy: Float, size: Float): Boolean {
        if (name.isNullOrEmpty()) return false
        @Suppress("DEPRECATION")
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id == 0) {
            Log.w(TAG, "no drawable named " + name)
            return false
        }
        val d = ContextCompat.getDrawable(context, id) ?: return false
        val half = (size / 2f).toInt()
        d.bounds = Rect(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
        d.draw(canvas)
        return true
    }

    /**
     * Draw text centred, shrinking it until it fits.
     *
     * Ol Chiki lines run longer than their Hindi source, so a fixed size
     * overflows the card. Shrinking keeps the card inside its cut line rather
     * than letting text run into the neighbouring card.
     */
    private fun drawCentred(
        canvas: Canvas,
        text: String,
        left: Float,
        maxWidth: Float,
        y: Float,
        basePaint: Paint,
    ) {
        if (text.isBlank()) return
        val paint = Paint(basePaint)
        var size = paint.textSize
        while (paint.measureText(text) > maxWidth && size > 7f) {
            size -= 0.5f
            paint.textSize = size
        }
        val x = left + (maxWidth - paint.measureText(text)) / 2f
        canvas.drawText(text, x, y, paint)
    }

    private fun drawSampleStamp(canvas: Canvas) {
        canvas.drawText(
            "SAMPLE DATA, NOT FOR CLASSROOM USE",
            MARGIN, MARGIN - 14f, stampPaint
        )
    }
}
