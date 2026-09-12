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

    // ── Paints ──────────────────────────────────────────────────────

    private val cutPaint = Paint().apply {
        color = Color.rgb(170, 170, 170)
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val domainPaint = Paint().apply {
        color = Color.rgb(120, 120, 120)
        textSize = 7.5f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val hindiPaint = Paint().apply {
        color = Color.BLACK
        textSize = 17f
        typeface = devanagariTypeface
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    /**
     * The target language's text, the biggest thing on the card.
     *
     * Typeface is set in generate(), because which script this draws is a
     * property of the pack rather than of this class. See WorksheetPdf for
     * the same reasoning.
     */
    private val targetPaint = Paint().apply {
        color = Color.rgb(20, 60, 130)
        textSize = 19f
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val glossPaint = Paint().apply {
        color = Color.rgb(90, 90, 90)
        textSize = 9.5f
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val provenancePaint = Paint().apply {
        color = Color.rgb(150, 150, 150)
        textSize = 6.5f
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val rulePaint = Paint().apply {
        color = Color.rgb(210, 210, 210)
        strokeWidth = 0.6f
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val taskFill = Paint().apply {
        color = Color.rgb(236, 245, 239)
        style = Paint.Style.FILL
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val taskEdge = Paint().apply {
        color = Color.rgb(19, 67, 51)
        style = Paint.Style.FILL
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val taskLabelPaint = Paint().apply {
        color = Color.rgb(19, 67, 51)
        textSize = 6.5f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val taskHindiPaint = Paint().apply {
        color = Color.rgb(31, 41, 55)
        textSize = 10.5f
        typeface = devanagariTypeface
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val taskEnPaint = Paint().apply {
        color = Color.rgb(75, 85, 99)
        textSize = 8f
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    private val stampPaint = Paint().apply {
        color = Color.rgb(200, 30, 30)
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        // Fractional advances: at 6 to 8 pt, whole-point rounding ate the spaces.
        isSubpixelText = true
        isLinearText = true
    }

    /** What the last generate produced. */
    data class CardResult(val file: File, val cards: Int, val pages: Int, val requested: Int, val capacity: Int, val setNo: Int)

    var lastResult: CardResult? = null
        private set

    /**
     * Flashcards from the selected material: [count] cards, chosen and ordered
     * afresh from [seed], so each Generate gives a new deck. Each card keeps
     * its picture, both scripts, the English gloss, its NIPUN Bharat domain
     * and where its translation came from; each page carries the set number.
     */
    fun generate(material: SheetMaterial, pack: VerifiedContentPack, count: Int, seed: Long): File? {
        targetPaint.typeface = ScriptFonts.forTarget(context, pack.font)
        val rng = kotlin.random.Random(seed)
        val setNo = QuestionGenerator.setNumber(seed)
        val usable = material.lines.filter { it.kind != "check" && it.target.isNotBlank() }
        val chosen = QuestionGenerator.pick(usable, count, rng)
        if (chosen.isEmpty()) return null
        val cards = chosen.map { l ->
            VerifiedContentPack.PackEntry(
                id = l.id, source = l.hindi, target = l.target, en = l.en,
                nipun = l.nipun.ifEmpty { WorksheetType.WORD_FLASH_STRIPS.nipunCode },
                nipunOutcome = l.nipunOutcome, nipunGoal = "", nipunDomain = l.nipunDomain,
                kind = l.kind, service = l.serviceName, audio = null, audioProvenance = null,
                image = l.image, lesson = material.title,
            )
        }
        // Every card gets a classroom task, dealt afresh for each set, so a
        // new Generate is a new deck even when the lesson has only as many
        // lines as cards were asked for.
        val tasks = CardTasks.assign(chosen, rng)
        val taskCodes = tasks.map { it.nipun }.distinct().sorted().joinToString(", ")

        // One file per set, named for it; the previous set of this material
        // goes, so the cache holds one deck per material, not one per tap.
        val prefix = "flashcards-" + material.key + "-set"
        context.cacheDir.listFiles()?.forEach { f ->
            if (f.name.startsWith(prefix) && f.name != "$prefix$setNo.pdf") f.delete()
        }
        val file = writeCards(
            cards, "$prefix$setNo", pack,
            footer = "${material.title} · Set $setNo · Tasks: NIPUN Bharat $taskCodes",
            tasks = tasks,
        ) { it.service } ?: return null
        lastResult = CardResult(file, cards.size, (cards.size + CARDS_PER_PAGE - 1) / CARDS_PER_PAGE,
            count, usable.size, setNo)
        return file
    }

    /**
     * Build the flashcard sheet.
     *
     * @param lessonId lesson to draw cards for, or null for the phrase deck
     * @return the written file, or null if there was nothing to print
     */
    fun generate(lessonId: String?, pack: VerifiedContentPack): File? {
        targetPaint.typeface = ScriptFonts.forTarget(context, pack.font)

        val cards = pack.entries()
            .filter { if (lessonId == null) it.kind == "phrase" else it.lesson == lessonId }
            .filter { it.target.isNotBlank() }
            .sortedBy { it.id }

        if (cards.isEmpty()) {
            Log.w(TAG, "No printable cards for lesson=" + lessonId)
            return null
        }

        val name = if (lessonId == null) "flashcards-phrases" else "flashcards-" + lessonId
        return writeCards(cards, name, pack) { pack.serviceName }
    }

    /**
     * Flashcards for the lines of a teacher's own PDF.
     *
     * Every card is tagged with the Word Flash Strips NIPUN outcome
     * (ECL1 4.8, reading familiar words), the same code the Materials screen
     * uses for cut-out word cards, and names where its line came from: the
     * offline pack, or Bhashini live. Lines with no translation are skipped.
     */
    fun generateForImport(
        title: String,
        lines: List<app.olsaathi.pdf.ImportedLine>,
        pack: VerifiedContentPack,
    ): File? {
        targetPaint.typeface = ScriptFonts.forTarget(context, pack.font)
        val cards = lines.filter { !it.translation.isNullOrBlank() }.map { line ->
            VerifiedContentPack.PackEntry(
                id = "imp-%03d".format(line.index),
                source = line.hindi,
                target = line.translation!!,
                en = "",
                nipun = WorksheetType.WORD_FLASH_STRIPS.nipunCode,
                nipunOutcome = "",
                nipunGoal = "",
                nipunDomain = "",
                kind = "imported",
                service = when {
                    line.live -> "Bhashini (live)"
                    line.onDevice -> "IndicTrans2 (on this tablet)"
                    else -> pack.serviceName
                },
                audio = null,
                audioProvenance = null,
                image = null,
                lesson = title,
            )
        }
        if (cards.isEmpty()) return null
        return writeCards(cards, "flashcards-imported", pack) { it.service }
    }

    private fun writeCards(
        cards: List<VerifiedContentPack.PackEntry>,
        name: String,
        pack: VerifiedContentPack,
        footer: String = "",
        /** One per card, in the same order; null prints cards without tasks. */
        tasks: List<CardTask>? = null,
        serviceFor: (VerifiedContentPack.PackEntry) -> String,
    ): File? {
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
                val task = tasks?.getOrNull(pageIndex * CARDS_PER_PAGE + i)
                drawCard(canvas, entry, left, top, cardWidth, cardHeight, pack.isSample, serviceFor(entry), task)
            }
            if (footer.isNotEmpty()) {
                // In the margin under the cards, outside every cut line.
                canvas.drawText(
                    "OL SAATHI flashcards · $footer · Page ${pageIndex + 1}",
                    MARGIN, PAGE_HEIGHT - MARGIN / 2f + 4f, provenancePaint
                )
            }

            document.finishPage(page)
        }

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
        task: CardTask? = null,
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
            top + height * (if (task != null) 0.265f else 0.30f),
            height * (if (task != null) 0.21f else 0.26f)
        )
        val hindiY: Float
        val olY: Float
        val glossY: Float
        if (task == null) {
            hindiY = if (hasIcon) 0.53f else 0.34f
            olY = if (hasIcon) 0.68f else 0.56f
            glossY = if (hasIcon) 0.80f else 0.74f
        } else {
            hindiY = if (hasIcon) 0.47f else 0.30f
            olY = if (hasIcon) 0.595f else 0.45f
            glossY = if (hasIcon) 0.69f else 0.58f
            drawTask(canvas, task, left + pad, innerWidth, top + height * 0.745f, top + height - pad - 9f)
        }

        drawCentred(canvas, entry.source, left + pad, innerWidth, top + height * hindiY, hindiPaint)
        drawCentred(canvas, entry.target, left + pad, innerWidth, top + height * olY, targetPaint)
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
     * The task band at the foot of a card: which outcome it practises, then
     * the task in Hindi for the teacher to read out and in English under it.
     */
    private fun drawTask(canvas: Canvas, task: CardTask, left: Float, width: Float, top: Float, bottom: Float) {
        val r = android.graphics.RectF(left, top, left + width, bottom)
        canvas.drawRoundRect(r, 5f, 5f, taskFill)
        canvas.drawRoundRect(android.graphics.RectF(left, top, left + 3f, bottom), 1.5f, 1.5f, taskEdge)
        val inner = left + 9f
        val innerWidth = width - 14f
        canvas.drawText(
            "TASK  ·  NIPUN ${task.nipun}  ·  ${task.domain.uppercase()}",
            inner, top + 9.5f, taskLabelPaint
        )
        drawLeft(canvas, task.hindi, inner, innerWidth, top + 23f, taskHindiPaint)
        drawLeft(canvas, task.english, inner, innerWidth, top + 34.5f, taskEnPaint)
    }

    /** Left-aligned text, shrunk until it fits [maxWidth]. */
    private fun drawLeft(canvas: Canvas, text: String, left: Float, maxWidth: Float, y: Float, basePaint: Paint) {
        val paint = Paint(basePaint)
        while (paint.measureText(text) > maxWidth && paint.textSize > 6f) paint.textSize -= 0.5f
        canvas.drawText(text, left, y, paint)
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
