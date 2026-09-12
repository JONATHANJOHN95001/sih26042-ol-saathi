package app.olsaathi.worksheet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import app.olsaathi.R
import android.graphics.pdf.PdfDocument
import android.util.Log
import app.olsaathi.content.Provenance
import app.olsaathi.content.VerifiedContentPack
import app.olsaathi.content.VerifiedContentPack.PackEntry
import java.io.File
import java.io.FileOutputStream

/**
 * Generates bilingual A4 worksheet PDFs using [PdfDocument].
 *
 * Now supports four worksheet types (Trace & Connect, Word Flash Strips,
 * Script Tracing, Classroom Dialogues), configurable activity counts, enhanced
 * headers with NIPUN blocks, and per-line provenance badges driven from actual
 * entry provenance.
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
        private const val MARGIN_TOP = 70f
        private const val MARGIN_BOTTOM = 70f
        private const val LINE_HEIGHT_WRITE = 55f
        private const val TAG = "WorksheetPdf"
        
        // Provenance badge colors (from colors.xml)
        private const val COLOR_HUMAN_VERIFIED = 0xFF1565C0.toInt()  // human_verified_blue
        private const val COLOR_VERIFIED = 0xFF4F7A3F.toInt()        // success_green
        private const val COLOR_ONLINE_MACHINE = 0xFF1E6F6B.toInt()  // online_machine_teal
        private const val COLOR_TRANSLITERATED = 0xFFD97706.toInt()  // warning_orange
        private const val COLOR_UNAVAILABLE = 0xFF79747E.toInt()     // md_theme_outline
        private const val COLOR_SAMPLE = 0xFFC62828.toInt()          // sample_red
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

    // ── Paint objects ───────────────────────────────────────────────

    private val headerPaint = Paint().apply {
        color = Color.BLACK
        textSize = 10f
        typeface = Typeface.DEFAULT
        isAntiAlias = true
    }

    private val titlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val lessonTitlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    /**
     * Devanagari at heading size.
     *
     * The exercise headings are bilingual, e.g. TRACE & CONNECT followed by
     * the same thing in Hindi, and they used to be drawn end to end with
     * lessonTitlePaint, which is Typeface.DEFAULT_BOLD. That is a system Latin
     * face. Whether the Hindi renders then depends entirely on the device's
     * font fallback, and this app bundles its own Indic fonts precisely
     * because that fallback cannot be relied on: CheckAndProofActivity
     * verifies the bundled files at runtime for the same reason. On a cheap
     * tablet with incomplete system fonts the heading printed as boxes.
     */
    private val hindiHeadingPaint = Paint().apply {
        color = Color.BLACK
        textSize = 13f
        typeface = devanagariTypeface
        isAntiAlias = true
    }

    private val hindiPaint = Paint().apply {
        color = Color.BLACK
        textSize = 14f
        typeface = devanagariTypeface
        isAntiAlias = true
    }

    private val targetPaint = Paint().apply {
        color = Color.parseColor("#1A237E")
        textSize = 16f
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.GRAY
        textSize = 10f
        isAntiAlias = true
    }

    private val fieldLabelPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 9f
        typeface = devanagariTypeface
        isAntiAlias = true
    }

    private val footerPaint = Paint().apply {
        color = Color.DKGRAY
        textSize = 9f
        isAntiAlias = true
    }

    /**
     * The frame around the printed area.
     *
     * Not decoration. These sheets are photocopied in bulk on school
     * machines, and a visible frame is what tells a teacher feeding paper
     * whether the page came through square and whether anything was cut off
     * at the edge. A sheet with one side of the frame missing is a sheet to
     * copy again.
     */
    private val borderPaint = Paint().apply {
        color = 0xFF6B6560.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 0.5f
    }

    private val bridgePaint = Paint().apply {
        color = Color.parseColor("#544E54")  // on-surface-variant
        textSize = 10f
        typeface = devanagariTypeface
        isAntiAlias = true
        // Deliberately no synthetic italic skew here, and this is not a style
        // preference.
        //
        // A skew inside PdfDocument shears the text about the page origin rather
        // than about its own baseline, so a run is displaced horizontally in
        // proportion to how far down the page it sits. At y=308 a -0.25 skew put
        // the Hindi source line at x=-8, off the left edge of the paper, and the
        // further down the page a line sat the worse it got. Measured in the
        // generated PDF, not guessed.
        //
        // The bundled Noto faces ship Regular only, so there is no real italic to
        // switch to. Emphasis is not worth a line that does not print at all.
    }

    private val italicHindiPaint = Paint().apply {
        color = Color.BLACK
        textSize = 14f
        typeface = devanagariTypeface
        isAntiAlias = true
        // Deliberately no synthetic italic skew here, and this is not a style
        // preference.
        //
        // A skew inside PdfDocument shears the text about the page origin rather
        // than about its own baseline, so a run is displaced horizontally in
        // proportion to how far down the page it sits. At y=308 a -0.25 skew put
        // the Hindi source line at x=-8, off the left edge of the paper, and the
        // further down the page a line sat the worse it got. Measured in the
        // generated PDF, not guessed.
        //
        // The bundled Noto faces ship Regular only, so there is no real italic to
        // switch to. Emphasis is not worth a line that does not print at all.
    }

    private val boxPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val nipunBoxPaint = Paint().apply {
        color = Color.parseColor("#F4EFEA")  // surface-container
        style = Paint.Style.FILL
    }

    private val nipunBoxOutlinePaint = Paint().apply {
        color = Color.parseColor("#E2DBD2")  // outline-variant
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val nipunTextPaint = Paint().apply {
        color = Color.parseColor("#19201D")  // on-surface
        textSize = 10f
        isAntiAlias = true
    }

    private val nipunCodePaint = Paint().apply {
        color = Color.parseColor("#1E4B38")  // primary
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val provenancePaint = Paint().apply {
        textSize = 8f
        isAntiAlias = true
    }

    private val provenanceBgPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val sampleStampPaint = Paint().apply {
        color = Color.parseColor("#C62828")
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        alpha = 180
    }

    /** What the last [generate] produced, for the screen to report honestly. */
    data class SheetResult(
        val file: File,
        val pages: Int,
        /** Questions or activities actually printed. */
        val items: Int,
        /** What the teacher asked for. */
        val requested: Int,
        /** The most this material can make for this sheet type. */
        val capacity: Int,
        /** Printed on every page, so two papers from one lesson can be told apart. */
        val setNo: Int,
    )

    var lastResult: SheetResult? = null
        private set

    /**
     * Pack lesson (or, with an empty [lessonId], the phrase deck), fixed
     * seed. Kept for callers that need the same sheet every time, such as the
     * Check and Proof screen.
     */
    fun generate(
        lessonId: String,
        pack: VerifiedContentPack,
        worksheetType: WorksheetType = WorksheetType.CLASSROOM_DIALOGUES,
        activityCount: Int = 3,
    ): File? = generate(SheetMaterial.fromPack(pack, lessonId), pack, worksheetType, activityCount, seed = 1L)

    /**
     * Build a worksheet from [material]: [count] questions or activities of
     * [worksheetType], over as many A4 pages as they need.
     *
     * A new [seed] gives a new sheet: which lines are used, their order, the
     * question kinds and the order of the options all come from it. Every page
     * carries the bilingual header, the NIPUN Bharat outcome box on page one,
     * bilingual instructions, per-line provenance and a footer with the set
     * number; Questions and Trace & Connect end with an answer key for the
     * teacher.
     */
    fun generate(
        material: SheetMaterial,
        pack: VerifiedContentPack,
        worksheetType: WorksheetType,
        count: Int,
        seed: Long,
    ): File? {
        targetPaint.typeface = ScriptFonts.forTarget(context, pack.font)
        val rng = kotlin.random.Random(seed)
        val setNo = QuestionGenerator.setNumber(seed)

        val statements = material.lines.filter { it.kind != "check" }
        val capacity: Int
        val items: List<Any>
        when (worksheetType) {
            WorksheetType.QUESTIONS -> {
                capacity = QuestionGenerator.capacity(material.lines)
                items = QuestionGenerator.questions(material.lines, count, rng)
            }
            else -> {
                capacity = statements.size
                items = QuestionGenerator.pick(statements, count, rng)
            }
        }
        if (items.isEmpty()) return null
        val usedLines = items.map { if (it is Question) it.line else it as SheetLine }
        val nipunCodes = usedLines.map { it.nipun }.distinct().filter { it.isNotEmpty() }
        val nipunOutcomes = usedLines.map { it.nipunOutcome }.distinct().filter { it.isNotEmpty() }

        val sheet = Sheet(worksheetType, nipunCodes, setNo, pack.isSample, material.title)
        sheet.start()
        val c = sheet.canvas
        sheet.y = drawEnhancedHeader(c, sheet.y, worksheetType)
        sheet.y = drawFieldsRow(c, sheet.y)
        sheet.y = drawLessonTitle(c, sheet.y, material.title)
        sheet.y = drawNipunBlock(c, sheet.y, nipunCodes, nipunOutcomes, worksheetType)
        sheet.y = drawInstructions(c, sheet.y, worksheetType, material.languageEnglish, items.size)

        val targetName = material.languageEnglish.ifEmpty { "the target language" }
        val answers = ArrayList<String>()
        when (worksheetType) {
            WorksheetType.QUESTIONS ->
                items.forEachIndexed { i, q -> drawQuestion(sheet, i + 1, q as Question, answers) }
            WorksheetType.TRACE_AND_CONNECT ->
                drawTraceAndConnect(sheet, items.map { it as SheetLine }, rng, answers)
            WorksheetType.WORD_FLASH_STRIPS ->
                items.forEachIndexed { i, l -> drawStrip(sheet, i + 1, l as SheetLine) }
            WorksheetType.SCRIPT_TRACING ->
                items.forEachIndexed { i, l -> drawTracing(sheet, i + 1, l as SheetLine, targetName) }
            WorksheetType.CLASSROOM_DIALOGUES ->
                items.forEachIndexed { i, l -> drawDialogue(sheet, i + 1, l as SheetLine) }
        }
        if (answers.isNotEmpty()) drawAnswerKey(sheet, answers)
        sheet.finishPage()

        val worksheetsDir = File(context.cacheDir, "worksheets")
        if (!worksheetsDir.exists()) worksheetsDir.mkdirs()
        // The set number is in the name: a preview and a generated set are
        // different files, so refreshing one can never replace the other, and
        // "Save as PDF" suggests a name that says which set it is.
        val file = File(worksheetsDir, "worksheet_${material.key}_${worksheetType.name.lowercase()}_set$setNo.pdf")
        FileOutputStream(file).use { out -> sheet.doc.writeTo(out) }
        sheet.doc.close()
        lastResult = SheetResult(file, sheet.pageNum, items.size, count, capacity, setNo)
        Log.i(TAG, "Worksheet ${file.name}: ${items.size} items, ${sheet.pageNum} pages, set $setNo")
        return file
    }

    /**
     * The pages of one sheet. [ensure] starts a new page when the next block
     * will not fit, finishing the current one with its footer first, so no
     * question is ever split across two pages or cut off at the bottom.
     */
    inner class Sheet(
        private val type: WorksheetType,
        private val codes: List<String>,
        private val setNo: Int,
        private val isSample: Boolean,
        private val title: String,
    ) {
        val doc = PdfDocument()
        var pageNum = 0
        private lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = 0f

        fun start() {
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
            canvas = page.canvas
            drawPageBorder(canvas)
            y = MARGIN_TOP
            if (pageNum > 1) {
                canvas.drawText(
                    "$title · ${type.displayName} · Set $setNo · continued", MARGIN_LEFT, y - 20f, labelPaint
                )
            }
        }

        fun ensure(height: Float) {
            if (y + height > PAGE_HEIGHT - MARGIN_BOTTOM) {
                finishPage()
                start()
            }
        }

        fun finishPage() {
            drawFooter(canvas, pageNum, codes, type, setNo)
            if (isSample) drawSampleStamp(canvas)
            doc.finishPage(page)
        }
    }

    /** "Activity instructions" in both languages, as the problem statement asks. */
    private fun drawInstructions(canvas: Canvas, startY: Float, type: WorksheetType, language: String, n: Int): Float {
        val lang = language.ifEmpty { "the target language" }
        val (en, hi) = when (type) {
            WorksheetType.QUESTIONS ->
                "Answer all $n questions. Tick (✓) the right answer." to "सभी $n प्रश्नों के उत्तर दें। सही उत्तर पर (✓) लगाएं।"
            WorksheetType.TRACE_AND_CONNECT ->
                "Join each Hindi sentence to its $lang translation, then trace it." to "हर वाक्य को उसके अनुवाद से मिलाएं, फिर उस पर लिखें।"
            WorksheetType.WORD_FLASH_STRIPS ->
                "Cut along the lines. Read each strip aloud in both languages." to "रेखाओं पर काटें। हर पट्टी दोनों भाषाओं में पढ़ें।"
            WorksheetType.SCRIPT_TRACING ->
                "Trace each $lang line, then write it yourself." to "हर पंक्ति पर लिखें, फिर खुद लिखें।"
            WorksheetType.CLASSROOM_DIALOGUES ->
                "Teacher reads the line; the class repeats and writes it." to "शिक्षक पंक्ति पढ़ें; कक्षा दोहराए और लिखे।"
        }
        canvas.drawText(en, MARGIN_LEFT, startY, labelPaint)
        canvas.drawText(hi, MARGIN_LEFT, startY + 15f, fieldLabelPaint)
        return startY + 34f
    }

    // ── Questions ────────────────────────────────────────────────────────

    private fun drawQuestion(sheet: Sheet, n: Int, q: Question, answers: MutableList<String>) {
        val width = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        val promptPaint = if (q.kind == QuestionKind.CHOOSE_HINDI) targetPaint else hindiPaint
        val optionPaint = if (q.kind == QuestionKind.CHOOSE_TARGET) targetPaint else hindiPaint
        val promptLines = wrapText(q.prompt, width - 24f, promptPaint)
        val promptH = promptLines.size * calculateLineHeight(promptPaint, true)
        val optH = calculateLineHeight(optionPaint, true)
        // Options wrap to the page: a long Tamil or Malayalam line ran past
        // the right edge of the paper when drawn as one run.
        val optionLines = q.options.map { wrapText(it, width - 60f, optionPaint) }
        val extraTarget = q.kind == QuestionKind.OPEN
        val body = 16f + promptH +
            (if (extraTarget) calculateLineHeight(targetPaint, true) else 0f) +
            (if (q.options.isEmpty()) 2 * LINE_HEIGHT_WRITE * 0.6f
             else optionLines.sumOf { it.size } * optH) + 18f
        sheet.ensure(body)
        val canvas = sheet.canvas
        var y = sheet.y

        drawBilingual(canvas, "Q$n. " + q.kind.english + "  ", q.kind.hindi, MARGIN_LEFT, y)
        drawProvenanceBadge(canvas, PAGE_WIDTH - MARGIN_RIGHT - 190f, y, q.line)
        y += 16f
        for (line in promptLines) {
            y += calculateLineHeight(promptPaint, true) * 0.75f
            canvas.drawText(line, MARGIN_LEFT + 16f, y, promptPaint)
            y += calculateLineHeight(promptPaint, true) * 0.25f
        }
        if (extraTarget) {
            // The lesson's own question, in both languages.
            y += calculateLineHeight(targetPaint, true) * 0.75f
            canvas.drawText(q.line.target, MARGIN_LEFT + 16f, y, targetPaint)
            y += calculateLineHeight(targetPaint, true) * 0.25f
        }
        if (q.options.isEmpty()) {
            y += 22f
            canvas.drawLine(MARGIN_LEFT + 16f, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
            y += 22f
            canvas.drawLine(MARGIN_LEFT + 16f, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
            // Listed in the key too, so a missing number never looks like a
            // question the key forgot.
            answers.add("Q$n: in the child's own words (बच्चे के अपने शब्दों में)")
        } else {
            optionLines.forEachIndexed { i, wrapped ->
                wrapped.forEachIndexed { j, part ->
                    y += optH * 0.8f
                    if (j == 0) {
                        canvas.drawCircle(MARGIN_LEFT + 24f, y - 4f, 5f, boxPaint)
                        canvas.drawText("(${'a' + i})", MARGIN_LEFT + 34f, y, labelPaint)
                    }
                    canvas.drawText(part, MARGIN_LEFT + 56f, y, optionPaint)
                    y += optH * 0.2f
                }
            }
            answers.add("Q$n: (${'a' + q.answerIndex}) ${q.answer}")
        }
        sheet.y = y + 18f
    }

    /** The last block of the sheet, for the teacher to mark with. */
    private fun drawAnswerKey(sheet: Sheet, answers: List<String>) {
        sheet.ensure(40f + 20f * minOf(answers.size, 3))
        var canvas = sheet.canvas
        drawBilingual(canvas, "Answer key (for the teacher)  ", "उत्तर कुंजी (शिक्षक के लिए)", MARGIN_LEFT, sheet.y)
        sheet.y += 20f
        for (a in answers) {
            // An answer is an option: Hindi or the target script.
            val paint = if (a.any { it.code in 0x0900..0x097F }) hindiPaint else targetPaint
            val p = Paint(paint).apply { textSize = paint.textSize * 0.85f }
            val parts = wrapText(a, PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT - 20f, p)
            sheet.ensure(20f * parts.size)
            canvas = sheet.canvas
            parts.forEach { part ->
                canvas.drawText(part, MARGIN_LEFT + 10f, sheet.y, p)
                sheet.y += 20f
            }
        }
    }

    // ── Trace & Connect ──────────────────────────────────────────────────

    /**
     * Hindi on the left in order, the translations on the right shuffled and
     * lettered, so joining them is a real exercise; each translation is
     * printed as pencil-trace letters. Rows are one height each, centred.
     */
    private fun drawTraceAndConnect(sheet: Sheet, lines: List<SheetLine>, rng: kotlin.random.Random, answers: MutableList<String>) {
        val right = lines.shuffled(rng)
        val contentW = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        val hindiW = contentW * 0.40f
        val boxLeft = MARGIN_LEFT + contentW * 0.50f
        val boxRight = PAGE_WIDTH - MARGIN_RIGHT
        val boxPad = 8f
        val boxInner = boxRight - boxLeft - boxPad * 2 - 16f
        val dotLeftX = MARGIN_LEFT + hindiW + 12f
        val dotRightX = boxLeft - 10f
        val hindiLineHeight = calculateLineHeight(hindiPaint, isIndicScript = true)
        val trace = tracePaint()
        val traceLineHeight = trace.fontSpacing * 1.08f

        lines.forEachIndexed { index, left ->
            val tgt = right[index]
            val letter = ('A' + index).toString()
            val hindiLines = wrapText("${index + 1}. ${left.hindi}", hindiW, hindiPaint)
            val targetLines = wrapText(tgt.target, boxInner, trace)
            val bridgeLines = if (tgt.bridge.isNotEmpty())
                wrapText("(${tgt.bridge})", boxInner, bridgePaint) else emptyList()
            val boxH = boxPad + targetLines.size * traceLineHeight + bridgeLines.size * 12f + 14f + boxPad
            val hindiH = hindiLines.size * hindiLineHeight
            val rowH = maxOf(boxH, hindiH + boxPad * 2)
            sheet.ensure(rowH + 14f)
            val canvas = sheet.canvas
            val top = sheet.y
            val centre = top + rowH / 2f

            var hy = centre - hindiH / 2f - hindiPaint.ascent() * 0.9f
            hindiLines.forEach { line ->
                canvas.drawText(line, MARGIN_LEFT, hy, hindiPaint)
                hy += hindiLineHeight
            }
            canvas.drawCircle(dotLeftX, centre, 3f, connectDotPaint)
            canvas.drawCircle(dotRightX, centre, 3f, connectDotPaint)

            val boxTop = centre - boxH / 2f
            canvas.drawRoundRect(RectF(boxLeft, boxTop, boxRight, boxTop + boxH), 6f, 6f, boxPaint)
            canvas.drawText(letter, boxLeft + boxPad, boxTop + boxPad + 12f, lessonTitlePaint)
            var ty = boxTop + boxPad - trace.ascent()
            val textLeft = boxLeft + boxPad + 16f
            targetLines.forEach { line ->
                canvas.drawLine(textLeft, ty + 2f, boxRight - boxPad, ty + 2f, traceGuidePaint)
                val path = android.graphics.Path()
                trace.getTextPath(line, 0, line.length, textLeft, ty, path)
                canvas.drawPath(path, trace)
                ty += traceLineHeight
            }
            var by = ty - traceLineHeight + trace.descent() + 10f
            bridgeLines.forEach { line ->
                canvas.drawText(line, textLeft, by, bridgePaint)
                by += 12f
            }
            drawProvenanceBadge(canvas, textLeft, by + 2f, tgt)
            sheet.y = top + rowH + 14f
        }
        lines.forEachIndexed { i, l ->
            answers.add("${i + 1} → ${'A' + right.indexOf(l)}")
        }
    }

    private fun tracePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = targetPaint.typeface
        textSize = targetPaint.textSize * 1.25f
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(120, 120, 120)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(1.6f, 1.1f), 0f)
    }

    private val traceGuidePaint = Paint().apply {
        color = Color.rgb(200, 214, 228)
        strokeWidth = 0.6f
    }

    private val connectDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 75, 56)
        style = Paint.Style.FILL
    }

    // ── Word Flash Strips ────────────────────────────────────────────────

    private fun drawStrip(sheet: Sheet, n: Int, line: SheetLine) {
        val midX = (MARGIN_LEFT + PAGE_WIDTH - MARGIN_RIGHT) / 2
        val halfInner = midX - MARGIN_LEFT - 20f
        val hindiLineHeight = calculateLineHeight(hindiPaint, isIndicScript = true)
        val targetLineHeight = calculateLineHeight(targetPaint, isIndicScript = true)
        val sourceLines = wrapText("$n. ${line.hindi}", halfInner, hindiPaint)
        val targetLines = wrapText(line.target, halfInner, targetPaint)
        val bridgeExtra = if (line.bridge.isNotEmpty()) 14f else 0f
        val stripHeight = maxOf(
            55f,
            sourceLines.size * hindiLineHeight + 30f,
            targetLines.size * targetLineHeight + 30f + bridgeExtra
        )
        sheet.ensure(stripHeight + 8f)
        val canvas = sheet.canvas
        val y = sheet.y
        canvas.drawRect(RectF(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y + stripHeight), boxPaint)
        var sy = y + 20f
        sourceLines.forEach { canvas.drawText(it, MARGIN_LEFT + 10f, sy, hindiPaint); sy += hindiLineHeight }
        canvas.drawLine(midX, y, midX, y + stripHeight, linePaint)
        var ty = y + 25f
        targetLines.forEach { canvas.drawText(it, midX + 10f, ty, targetPaint); ty += targetLineHeight }
        if (line.bridge.isNotEmpty()) canvas.drawText("(${line.bridge})", midX + 10f, ty, bridgePaint)
        drawProvenanceBadge(canvas, PAGE_WIDTH - MARGIN_RIGHT - 180f, y + stripHeight - 6f, line)
        sheet.y = y + stripHeight + 8f
    }

    // ── Script Tracing ───────────────────────────────────────────────────

    private fun drawTracing(sheet: Sheet, n: Int, line: SheetLine, targetName: String) {
        val labelLineHeight = calculateLineHeight(labelPaint, isIndicScript = false)
        val targetLineHeight = calculateLineHeight(targetPaint, isIndicScript = true)
        val hindiLineHeight = calculateLineHeight(hindiPaint, isIndicScript = true)
        val bridge = if (line.bridge.isNotEmpty()) 14f else 0f
        val needed = hindiLineHeight + labelLineHeight * 3 + targetLineHeight * 3 + bridge + 24f
        sheet.ensure(needed)
        val canvas = sheet.canvas
        var y = sheet.y
        canvas.drawText("$n. ${line.hindi}", MARGIN_LEFT, y, hindiPaint)
        y += hindiLineHeight * 0.8f
        canvas.drawText("Read ($targetName):", MARGIN_LEFT, y, labelPaint)
        y += labelLineHeight
        canvas.drawText(line.target, MARGIN_LEFT + 10f, y, targetPaint)
        y += targetLineHeight * 0.7f
        if (line.bridge.isNotEmpty()) {
            canvas.drawText("(${line.bridge})", MARGIN_LEFT + 10f, y, bridgePaint)
            y += 14f
        }
        canvas.drawText("Trace:", MARGIN_LEFT, y, labelPaint)
        y += labelLineHeight
        val trace = tracePaint()
        val path = android.graphics.Path()
        trace.getTextPath(line.target, 0, line.target.length, MARGIN_LEFT + 10f, y + 4f, path)
        canvas.drawLine(MARGIN_LEFT + 10f, y + 6f, PAGE_WIDTH - MARGIN_RIGHT, y + 6f, traceGuidePaint)
        canvas.drawPath(path, trace)
        y += targetLineHeight * 0.8f
        canvas.drawText("Your turn:", MARGIN_LEFT, y, labelPaint)
        y += labelLineHeight + 8f
        canvas.drawLine(MARGIN_LEFT + 10f, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
        drawProvenanceBadge(canvas, PAGE_WIDTH - MARGIN_RIGHT - 180f, sheet.y, line)
        sheet.y = y + 18f
    }

    // ── Classroom Dialogues ──────────────────────────────────────────────

    private fun drawDialogue(sheet: Sheet, n: Int, line: SheetLine) {
        val targetWrapped = wrapText(line.target, PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT - 20f, targetPaint)
        val targetLineHeight = calculateLineHeight(targetPaint, isIndicScript = true)
        val bridge = if (line.bridge.isNotEmpty()) 20f else 0f
        val needed = 14f + 22f + targetWrapped.size * targetLineHeight + bridge + 22f + 36f
        sheet.ensure(needed)
        val canvas = sheet.canvas
        var y = sheet.y
        canvas.drawText("Dialogue $n:", MARGIN_LEFT, y, labelPaint)
        y += 14f
        canvas.drawText("शिक्षक:", MARGIN_LEFT, y, fieldLabelPaint)
        drawProvenanceBadge(canvas, PAGE_WIDTH - MARGIN_RIGHT - 180f, y, line)
        y += 22f
        targetWrapped.forEach { canvas.drawText(it, MARGIN_LEFT + 10f, y, targetPaint); y += targetLineHeight }
        if (line.bridge.isNotEmpty()) {
            canvas.drawText("(${line.bridge})", MARGIN_LEFT + 10f, y, bridgePaint)
            y += 20f
        }
        canvas.drawText("Hindi: \"${line.hindi}\"", MARGIN_LEFT + 10f, y, italicHindiPaint)
        y += 22f
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
        sheet.y = y + 18f
    }

    /**
     * Draw the page frame, inset from the paper edge.
     *
     * The inset is 22f rather than the 50f text margin: a frame sitting on the
     * same line as the text reads as a box around a paragraph, not as the edge
     * of the sheet. It stays well inside the non-printable border that cheap
     * laser printers reserve.
     */
    private fun drawPageBorder(canvas: Canvas) {
        val inset = 22f
        canvas.drawRect(
            inset,
            inset,
            PAGE_WIDTH - inset,
            PAGE_HEIGHT - inset,
            borderPaint
        )
    }

    private fun drawEnhancedHeader(canvas: Canvas, startY: Float, worksheetType: WorksheetType): Float {
        var y = startY
        
        // Issuing body (left) - Devanagari
        canvas.drawText("ओएल साथी परियोजना", MARGIN_LEFT, y, fieldLabelPaint)
        
        // Title (center)
        val titleText = "BILINGUAL CLASSROOM WORKSHEET"
        val titleWidth = titlePaint.measureText(titleText)
        canvas.drawText(titleText, (PAGE_WIDTH - titleWidth) / 2, y, titlePaint)
        
        // Form ID (right)
        val formId = "Form ${worksheetType.nipunCode}"
        val formIdWidth = headerPaint.measureText(formId)
        canvas.drawText(formId, PAGE_WIDTH - MARGIN_RIGHT - formIdWidth, y, headerPaint)
        
        y += 20f
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
        y += 15f
        
        return y
    }

    private fun drawFieldsRow(canvas: Canvas, startY: Float): Float {
        var y = startY
        val fieldWidth = (PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT - 30f) / 4
        var x = MARGIN_LEFT
        
        // नाम (Name)
        canvas.drawText("नाम:", x, y, fieldLabelPaint)
        canvas.drawLine(x + 35f, y + 2f, x + fieldWidth, y + 2f, linePaint)
        x += fieldWidth + 10f
        
        // कक्षा (Class)
        canvas.drawText("कक्षा:", x, y, fieldLabelPaint)
        canvas.drawLine(x + 35f, y + 2f, x + fieldWidth, y + 2f, linePaint)
        x += fieldWidth + 10f
        
        // रोल नं (Roll)
        canvas.drawText("रोल नं:", x, y, fieldLabelPaint)
        canvas.drawLine(x + 45f, y + 2f, x + fieldWidth, y + 2f, linePaint)
        x += fieldWidth + 10f
        
        // दिनांक (Date)
        canvas.drawText("दिनांक:", x, y, fieldLabelPaint)
        canvas.drawLine(x + 40f, y + 2f, x + fieldWidth, y + 2f, linePaint)
        
        y += 18f
        return y
    }

    /**
     * Draw a Latin run and a Devanagari run side by side, each in a face that
     * can actually render it, and return the total width.
     *
     * Splitting the string is the whole point: one drawText call cannot use
     * two typefaces, and passing mixed-script text to a Latin face is how the
     * Hindi half ends up as boxes.
     */
    private fun drawBilingual(
        canvas: Canvas,
        latin: String,
        hindi: String,
        x: Float,
        y: Float,
    ): Float {
        canvas.drawText(latin, x, y, lessonTitlePaint)
        val latinWidth = lessonTitlePaint.measureText(latin)
        canvas.drawText(hindi, x + latinWidth, y, hindiHeadingPaint)
        return latinWidth + hindiHeadingPaint.measureText(hindi)
    }

    /**
     * The heading for the one exercise section on the sheet.
     *
     * Both halves come from [WorksheetType] rather than being retyped here.
     * They were duplicated as literals in four separate draw methods, which is
     * four places for the printed sheet to disagree with the tile the teacher
     * tapped to ask for it.
     */
    private fun drawExerciseHeading(
        canvas: Canvas,
        type: WorksheetType,
        y: Float,
    ): Float {
        // Always section 1: a sheet carries a single exercise section, and the
        // dialogues heading previously numbered itself by entry count, so a
        // three-entry sheet announced itself as section 3.
        return drawBilingual(
            canvas,
            "1. " + type.displayName.uppercase() + " (",
            type.hindiSubtitle + ")",
            MARGIN_LEFT,
            y,
        )
    }

    private fun drawLessonTitle(canvas: Canvas, startY: Float, lessonId: String): Float {
        val displayTitle = lessonId.replace("-", " ").replaceFirstChar { it.uppercase() }

        // Measured from exactly the runs that are drawn. This used to measure
        // two strings that were not the ones drawn, so the centred title sat
        // off centre by the difference between them. And the Hindi word was
        // drawn with lessonTitlePaint, a system Latin face, rather than the
        // bundled Devanagari one.
        val latin = "Lesson: " + displayTitle + "  "
        val hindiLabel = "पाठ: "
        val latinWidth = lessonTitlePaint.measureText(latin)
        val hindiWidth = hindiHeadingPaint.measureText(hindiLabel)
        val titleWidth = lessonTitlePaint.measureText(displayTitle)

        var x = (PAGE_WIDTH - (latinWidth + hindiWidth + titleWidth)) / 2
        canvas.drawText(latin, x, startY, lessonTitlePaint)
        x += latinWidth
        canvas.drawText(hindiLabel, x, startY, hindiHeadingPaint)
        x += hindiWidth
        canvas.drawText(displayTitle, x, startY, lessonTitlePaint)

        return startY + 18f
    }

    private fun drawNipunBlock(
        canvas: Canvas,
        startY: Float,
        codes: List<String>,
        outcomes: List<String>,
        worksheetType: WorksheetType
    ): Float {
        // The worksheet type carries its own published outcome code, and the
        // sheet claims alignment to it, so it belongs in the box. It was
        // passed in and ignored, which meant a teacher could pick Script
        // Tracing and print a sheet whose NIPUN block never mentioned the
        // code that type is aligned to.
        val allCodes = if (codes.contains(worksheetType.nipunCode)) codes
                       else codes + worksheetType.nipunCode

        val maxWidth = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT - 20f
        val italicPaint = Paint(nipunTextPaint).apply {
            // Deliberately no synthetic italic skew here, and this is not a style
            // preference.
            //
            // A skew inside PdfDocument shears the text about the page origin rather
            // than about its own baseline, so a run is displaced horizontally in
            // proportion to how far down the page it sits. At y=308 a -0.25 skew put
            // the Hindi source line at x=-8, off the left edge of the paper, and the
            // further down the page a line sat the worse it got. Measured in the
            // generated PDF, not guessed.
            //
            // The bundled Noto faces ship Regular only, so there is no real italic to
            // switch to. Emphasis is not worth a line that does not print at all.
        }

        // An outcome sentence is printed whatever the count. Previously it was
        // printed only when there was exactly one, so a lesson spanning two
        // outcomes printed bare codes and no sentence at all: the outcome text
        // is the part a reader can actually check, and dropping it quietly
        // undid the reason nipunOutcome was added to every entry.
        val primaryOutcome = outcomes.firstOrNull().orEmpty()
        val outcomeLines = if (primaryOutcome.isEmpty()) emptyList()
                           else wrapText(primaryOutcome, maxWidth, italicPaint).take(2)
        val alsoCovers = if (outcomes.size > 1)
            "| and " + (outcomes.size - 1) + " more outcome(s) in this lesson" else ""

        // Height is measured from the content rather than fixed. It was a flat
        // 55f while the contents could reach 59f, so a sheet with a co-mapped
        // code and an outcome sentence printed its last line outside the box.
        val codeLine = 14f
        val textLine = 11f
        var contentHeight = 12f + codeLine
        if (allCodes.size > 1) contentHeight += textLine
        contentHeight += outcomeLines.size * textLine
        if (alsoCovers.isNotEmpty()) contentHeight += textLine
        val boxHeight = maxOf(55f, contentHeight + 8f)

        val rect = RectF(MARGIN_LEFT, startY, PAGE_WIDTH - MARGIN_RIGHT, startY + boxHeight)
        canvas.drawRect(rect, nipunBoxPaint)
        canvas.drawRect(rect, nipunBoxOutlinePaint)

        var y = startY + 12f

        canvas.drawText("NIPUN BHARAT: " + allCodes.first(), MARGIN_LEFT + 10f, y, nipunCodePaint)
        y += codeLine

        if (allCodes.size > 1) {
            canvas.drawText(
                "| Co-mapped: " + allCodes.drop(1).joinToString(", "),
                MARGIN_LEFT + 10f, y, nipunTextPaint
            )
            y += textLine
        }

        outcomeLines.forEach { line ->
            canvas.drawText(line, MARGIN_LEFT + 10f, y, italicPaint)
            y += textLine
        }

        if (alsoCovers.isNotEmpty()) {
            canvas.drawText(alsoCovers, MARGIN_LEFT + 10f, y, nipunTextPaint)
            y += textLine
        }

        return startY + boxHeight + 15f
    }

    /**
     * Draw a provenance badge on the PDF using the same color vocabulary
     * as the on-screen badges. Driven entirely from entry.provenance.
     */
    private fun drawProvenanceBadge(
        canvas: Canvas,
        x: Float,
        y: Float,
        line: SheetLine
    ) {
        val provenance = line.provenance
        val label = line.provenanceLabel
        
        // Get color based on provenance
        val color = when (provenance) {
            Provenance.HUMAN_VERIFIED -> COLOR_HUMAN_VERIFIED
            Provenance.VERIFIED -> COLOR_VERIFIED
            Provenance.ONLINE_MACHINE -> COLOR_ONLINE_MACHINE
            Provenance.ON_DEVICE_MACHINE -> COLOR_ONLINE_MACHINE
            Provenance.TRANSLITERATED -> COLOR_TRANSLITERATED
            Provenance.UNAVAILABLE -> COLOR_UNAVAILABLE
            Provenance.SAMPLE -> COLOR_SAMPLE
        }
        
        // Measure text for background box
        provenancePaint.color = color
        val textWidth = provenancePaint.measureText(label)
        
        // Draw background pill (low alpha)
        provenanceBgPaint.color = Color.argb(38, Color.red(color), Color.green(color), Color.blue(color))
        val rect = RectF(x, y - 10f, x + textWidth + 12f, y + 4f)
        canvas.drawRoundRect(rect, 6f, 6f, provenanceBgPaint)
        
        // Draw text (full color)
        canvas.drawText(label, x + 6f, y, provenancePaint)
    }

    private fun drawFooter(
        canvas: Canvas,
        pageNum: Int,
        nipunCodes: List<String>,
        worksheetType: WorksheetType,
        setNo: Int = 0,
    ) {
        val footerY = PAGE_HEIGHT - 30f
        
        // Left: which sheet this is, what it aligns to, and that provenance
        // is per line. Both parameters were passed in and ignored, so every
        // sheet carried the same generic footer whatever it actually was.
        val codeList = if (nipunCodes.isEmpty()) worksheetType.nipunCode
                       else nipunCodes.joinToString(", ")
        canvas.drawText(
            worksheetType.displayName + " · NIPUN Bharat: " + codeList +
                " · Provenance tagged per line",
            MARGIN_LEFT,
            footerY,
            footerPaint
        )
        
        // Right: the set, so two papers from one lesson can be told apart,
        // and the page.
        val setText = if (setNo > 0) "Set $setNo · " else ""
        val pageText = "OL SAATHI · ${setText}Page $pageNum"
        val pageWidth = footerPaint.measureText(pageText)
        canvas.drawText(pageText, PAGE_WIDTH - MARGIN_RIGHT - pageWidth, footerY, footerPaint)
    }

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

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        
        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        
        return lines
    }

    /**
     * Calculate line height for text using font metrics.
     * For Indic scripts, applies 1.6 line height for better readability.
     */
    private fun calculateLineHeight(paint: Paint, isIndicScript: Boolean = true): Float {
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        return if (isIndicScript) {
            textHeight * 1.6f
        } else {
            textHeight * 1.2f
        }
    }

    /**
     * A NIPUN-format worksheet for the lines of a teacher's own PDF.
     *
     * Same frame as the pack worksheets: issuing-body header with the form's
     * NIPUN code, the name/class/roll/date row, the NIPUN Bharat outcome box
     * and the aligned footer on every page. The activity is Classroom
     * Dialogues (ECL2 4.1a: listening and responding in the home language),
     * because an arbitrary lesson is sentences a teacher reads and a child
     * answers. Each line names where its translation came from.
     */
    fun generateForImport(
        title: String,
        lines: List<app.olsaathi.pdf.ImportedLine>,
        pack: VerifiedContentPack,
        worksheetType: WorksheetType = WorksheetType.QUESTIONS,
        count: Int = 10,
    ): File? = generate(
        SheetMaterial.fromImport(title, lines, pack), pack, worksheetType, count, System.nanoTime()
    )
}