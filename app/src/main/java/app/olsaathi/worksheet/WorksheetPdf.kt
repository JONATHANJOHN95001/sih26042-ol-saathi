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

    /**
     * Generate a worksheet PDF for the given lesson.
     *
     * @param lessonId The lesson to generate a worksheet for
     * @param pack The loaded content pack
     * @param worksheetType The type of worksheet to generate
     * @param activityCount Number of activities per sheet (2-4)
     * @return The generated PDF file, or null if no lesson entries found
     */
    fun generate(
        lessonId: String,
        pack: VerifiedContentPack,
        worksheetType: WorksheetType = WorksheetType.CLASSROOM_DIALOGUES,
        activityCount: Int = 3
    ): File? {
        targetPaint.typeface = ScriptFonts.forTarget(context, pack.font)

        val lessonEntries = pack.entries(lessonId).filter { it.kind == "lesson" }
        if (lessonEntries.isEmpty()) return null

        // Select entries based on activity count
        val selectedEntries = lessonEntries.take(activityCount)
        
        val document = PdfDocument()
        val isSample = pack.isSample

        val nipunCodes = selectedEntries.map { it.nipun }.distinct().filter { it.isNotEmpty() }
        val nipunOutcomes = selectedEntries.map { it.nipunOutcome }.distinct().filter { it.isNotEmpty() }
        
        var pageNum = 1
        var y = MARGIN_TOP

        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = document.startPage(pageInfo)
        drawPageBorder(page.canvas)
        var canvas: Canvas = page.canvas

        // ── Header: Issuing body + title + form ID ──────────────────────
        y = drawEnhancedHeader(canvas, y, worksheetType)

        // ── Fields row: नाम, कक्षा, रोल नं, दिनांक ────────────────────────
        y = drawFieldsRow(canvas, y)

        // ── Lesson title (English + Hindi) ───────────────────────────────
        y = drawLessonTitle(canvas, y, lessonId)

        // ── NIPUN block ───────────────────────────────────────────────────
        y = drawNipunBlock(canvas, y, nipunCodes, nipunOutcomes, worksheetType)

        // ── Draw activities based on worksheet type ──────────────────────
        when (worksheetType) {
            WorksheetType.TRACE_AND_CONNECT -> {
                drawTraceAndConnect(canvas, y, selectedEntries, pack)
            }
            WorksheetType.WORD_FLASH_STRIPS -> {
                drawWordFlashStrips(canvas, y, selectedEntries, pack)
            }
            WorksheetType.SCRIPT_TRACING -> {
                drawScriptTracing(canvas, y, selectedEntries, pack)
            }
            WorksheetType.CLASSROOM_DIALOGUES -> {
                drawClassroomDialogues(canvas, y, selectedEntries, pack)
            }
        }

        // ── Footer ─────────────────────────────────────────────────────────
        drawFooter(canvas, pageNum, nipunCodes, worksheetType)
        if (isSample) drawSampleStamp(canvas)
        document.finishPage(page)

        // ── Save to worksheets subdirectory in cache ──────────────────────
        val worksheetsDir = File(context.cacheDir, "worksheets")
        if (!worksheetsDir.exists()) worksheetsDir.mkdirs()
        val file = File(worksheetsDir, "worksheet_${lessonId}_${worksheetType.name.lowercase()}.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        Log.i(TAG, "Worksheet generated: ${file.name} (${file.length()} bytes, $pageNum pages)")
        return file
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

    private fun drawClassroomDialogues(
        canvas: Canvas,
        startY: Float,
        entries: List<PackEntry>,
        pack: VerifiedContentPack
    ): Float {
        var y = startY

        drawExerciseHeading(canvas, WorksheetType.CLASSROOM_DIALOGUES, y)
        y += 18f
        
        entries.forEachIndexed { index, entry ->
            val translation = pack.lookup(entry.source)
            
            // Calculate needed space for this entry
            val targetWrapped = wrapText(entry.target, PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT - 20f, targetPaint)
            val targetLineHeight = calculateLineHeight(targetPaint, isIndicScript = true)
            val bridgeLines = if (translation.bridge.isNotEmpty()) 1 else 0
            val needed = 14f + 22f + 20f + (targetWrapped.size * targetLineHeight) +
                        (bridgeLines * 14f) + 14f + 20f
            
            if (y + needed > PAGE_HEIGHT - MARGIN_BOTTOM) {
                return y
            }
            
            // Activity number
            canvas.drawText("Dialogue ${index + 1}:", MARGIN_LEFT, y, labelPaint)
            y += 14f
            
            // Teacher label with provenance badge right-aligned
            val labelY = y
            canvas.drawText("शिक्षक:", MARGIN_LEFT, labelY, fieldLabelPaint)
            drawProvenanceBadge(canvas, PAGE_WIDTH - MARGIN_RIGHT - 180f, labelY, translation)
            // 22f, not 14f: the target is set at 16pt and its ascenders reach
            // roughly 14.5pt above their baseline, so at 14f they ran into the
            // provenance chip's background box. The chip has to stay legible,
            // because it is the line that says who produced the sentence
            // underneath it.
            y += 22f
            
            // Target text (Ol Chiki large text, wrapped)
            targetWrapped.forEach { line ->
                canvas.drawText(line, MARGIN_LEFT + 10f, y, targetPaint)
                y += targetLineHeight
            }
            
            // Bridge (Devanagari in brackets, smaller, italic) - only if present
            if (translation.bridge.isNotEmpty()) {
                canvas.drawText("(${translation.bridge})", MARGIN_LEFT + 10f, y, bridgePaint)
                y += 14f
            }
            
            // Hindi source in italics with prefix
            canvas.drawText("Hindi: \"${entry.source}\"", MARGIN_LEFT + 10f, y, italicHindiPaint)
            y += 18f
            
            // Practice lines
            canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
            y += 18f
        }
        
        return y
    }

    private fun drawTraceAndConnect(
        canvas: Canvas,
        startY: Float,
        entries: List<PackEntry>,
        pack: VerifiedContentPack
    ): Float {
        var y = startY
        val targetName = pack.languageEnglish.ifEmpty { "Target" }
        
        drawExerciseHeading(canvas, WorksheetType.TRACE_AND_CONNECT, y)
        val lessonLineHeight = calculateLineHeight(lessonTitlePaint, isIndicScript = false)
        y += lessonLineHeight
        
        canvas.drawText("Match each Hindi sentence with its $targetName translation:", MARGIN_LEFT, y, labelPaint)
        val labelLineHeight = calculateLineHeight(labelPaint, isIndicScript = false)
        y += labelLineHeight + 8f
        
        entries.forEachIndexed { index, entry ->
            val translation = pack.lookup(entry.source)
            
            val hindiLineHeight = calculateLineHeight(hindiPaint, isIndicScript = true)
            val targetLineHeight = calculateLineHeight(targetPaint, isIndicScript = true)
            val bridgeLines = if (translation.bridge.isNotEmpty()) 1 else 0
            val needed = hindiLineHeight +
                (wrapText(entry.target, 190f, targetPaint).size * targetLineHeight) +
                (bridgeLines * 14f) + 25f
            
            if (y + needed > PAGE_HEIGHT - MARGIN_BOTTOM) {
                return y
            }
            
            // Hindi on left, target on right with box
            canvas.drawText("${index + 1}. ${entry.source}", MARGIN_LEFT, y, hindiPaint)

            // The target is wrapped to the box, and the box grows to hold it.
            // It used to be drawn as one unbroken run into a fixed 200pt box,
            // so any language whose sentence is wider than 200pt ran off the
            // right edge of the paper. Measured at x=602.9 on a 595pt page in
            // Odia, clipped mid-word.
            val boxLeft = PAGE_WIDTH - MARGIN_RIGHT - 200f
            val boxInner = 200f - 10f
            val targetLines = wrapText(entry.target, boxInner, targetPaint)
            val boxHeight = targetLines.size * targetLineHeight + 8f +
                (if (translation.bridge.isNotEmpty()) 14f else 0f)
            val boxRect = RectF(boxLeft, y - 12f, PAGE_WIDTH - MARGIN_RIGHT, y + boxHeight)
            canvas.drawRect(boxRect, boxPaint)

            var lineY = y + 5f
            targetLines.forEach { line ->
                canvas.drawText(line, boxLeft + 5f, lineY, targetPaint)
                lineY += targetLineHeight
            }

            var badgeY = lineY + 2f
            if (translation.bridge.isNotEmpty()) {
                canvas.drawText("(${translation.bridge})", boxLeft + 5f, badgeY, bridgePaint)
                badgeY += 14f
            }

            drawProvenanceBadge(canvas, boxLeft + 5f, badgeY, translation)
            // Advance past whichever side is taller, so a wrapped target can
            // never be overdrawn by the next row.
            y += maxOf(hindiLineHeight + 10f, boxHeight + 20f)
        }
        
        return y
    }

    private fun drawWordFlashStrips(
        canvas: Canvas,
        startY: Float,
        entries: List<PackEntry>,
        pack: VerifiedContentPack
    ): Float {
        var y = startY

        drawExerciseHeading(canvas, WorksheetType.WORD_FLASH_STRIPS, y)
        val lessonLineHeight = calculateLineHeight(lessonTitlePaint, isIndicScript = false)
        y += lessonLineHeight
        
        canvas.drawText("Cut along dotted lines to create flash cards:", MARGIN_LEFT, y, labelPaint)
        val labelLineHeight = calculateLineHeight(labelPaint, isIndicScript = false)
        y += labelLineHeight + 8f
        
        entries.forEach { entry ->
            val translation = pack.lookup(entry.source)
            
            val hindiLineHeight = calculateLineHeight(hindiPaint, isIndicScript = true)
            val targetLineHeight = calculateLineHeight(targetPaint, isIndicScript = true)
            val bridgeLines = if (translation.bridge.isNotEmpty()) 1 else 0
            val needed = maxOf(hindiLineHeight, targetLineHeight) + (bridgeLines * 14f) + 35f
            
            if (y + needed > PAGE_HEIGHT - MARGIN_BOTTOM) {
                return y
            }
            
            // Both halves are wrapped to their own column. They used to be
            // drawn as single unbroken runs, so a sentence wider than half the
            // page spilled across the divider and, on the target side, past
            // the right edge of the paper.
            val midX = (MARGIN_LEFT + PAGE_WIDTH - MARGIN_RIGHT) / 2
            val halfInner = midX - MARGIN_LEFT - 20f
            val sourceLines = wrapText(entry.source, halfInner, hindiPaint)
            val targetLines = wrapText(entry.target, halfInner, targetPaint)
            val bridgeExtra = if (translation.bridge.isNotEmpty()) 14f else 0f
            val stripHeight = maxOf(
                55f,
                sourceLines.size * hindiLineHeight + 30f,
                targetLines.size * targetLineHeight + 30f + bridgeExtra
            )
            val rect = RectF(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y + stripHeight)
            canvas.drawRect(rect, boxPaint)

            var sourceY = y + 20f
            sourceLines.forEach { line ->
                canvas.drawText(line, MARGIN_LEFT + 10f, sourceY, hindiPaint)
                sourceY += hindiLineHeight
            }

            canvas.drawLine(midX, y, midX, y + stripHeight, linePaint)

            var targetY = y + 25f
            targetLines.forEach { line ->
                canvas.drawText(line, midX + 10f, targetY, targetPaint)
                targetY += targetLineHeight
            }
            
            // Bridge if present
            if (translation.bridge.isNotEmpty()) {
                canvas.drawText("(${translation.bridge})", midX + 10f, targetY, bridgePaint)
                targetY += 14f
            }
            
            drawProvenanceBadge(canvas, PAGE_WIDTH - MARGIN_RIGHT - 180f, y + stripHeight - 10f, translation)
            y += stripHeight + 5f
        }
        
        return y
    }

    private fun drawScriptTracing(
        canvas: Canvas,
        startY: Float,
        entries: List<PackEntry>,
        pack: VerifiedContentPack
    ): Float {
        var y = startY
        val targetName = pack.languageEnglish.ifEmpty { "Target" }
        
        drawExerciseHeading(canvas, WorksheetType.SCRIPT_TRACING, y)
        val lessonLineHeight = calculateLineHeight(lessonTitlePaint, isIndicScript = false)
        y += lessonLineHeight
        
        canvas.drawText("Trace the $targetName letters, then write your own:", MARGIN_LEFT, y, labelPaint)
        val labelLineHeight = calculateLineHeight(labelPaint, isIndicScript = false)
        y += labelLineHeight + 8f
        
        entries.forEachIndexed { index, entry ->
            val translation = pack.lookup(entry.source)
            
            val targetLineHeight = calculateLineHeight(targetPaint, isIndicScript = true)
            val bridgeLines = if (translation.bridge.isNotEmpty()) 1 else 0
            val needed = labelLineHeight * 3 + targetLineHeight * 3 + (bridgeLines * 14f) + 30f
            
            if (y + needed > PAGE_HEIGHT - MARGIN_BOTTOM) {
                return y
            }
            
            // Print target (solid)
            canvas.drawText("${index + 1}. Print target:", MARGIN_LEFT, y, labelPaint)
            y += labelLineHeight
            canvas.drawText(entry.target, MARGIN_LEFT + 10f, y, targetPaint)
            y += targetLineHeight + 6f
            
            // Bridge if present
            if (translation.bridge.isNotEmpty()) {
                canvas.drawText("(${translation.bridge})", MARGIN_LEFT + 10f, y, bridgePaint)
                y += 14f
            }
            
            // Dotted guide (lighter)
            canvas.drawText("Trace:", MARGIN_LEFT, y, labelPaint)
            y += labelLineHeight
            val dottedPaint = Paint(targetPaint).apply { alpha = 100 }
            canvas.drawText(entry.target, MARGIN_LEFT + 10f, y, dottedPaint)
            y += targetLineHeight + 6f
            
            // Self-practice
            canvas.drawText("Your turn:", MARGIN_LEFT, y, labelPaint)
            y += labelLineHeight
            canvas.drawLine(MARGIN_LEFT + 10f, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
            
            drawProvenanceBadge(canvas, PAGE_WIDTH - MARGIN_RIGHT - 180f, y - 10f, translation)
            y += targetLineHeight
        }
        
        return y
    }

    /**
     * Draw a provenance badge on the PDF using the same color vocabulary
     * as the on-screen badges. Driven entirely from entry.provenance.
     */
    private fun drawProvenanceBadge(
        canvas: Canvas,
        x: Float,
        y: Float,
        translation: app.olsaathi.content.Translation
    ) {
        val provenance = translation.provenance
        val label = translation.provenanceLabel
        
        // Get color based on provenance
        val color = when (provenance) {
            Provenance.HUMAN_VERIFIED -> COLOR_HUMAN_VERIFIED
            Provenance.VERIFIED -> COLOR_VERIFIED
            Provenance.ONLINE_MACHINE -> COLOR_ONLINE_MACHINE
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
        worksheetType: WorksheetType
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
        
        // Right: Engine name and page number
        val pageText = "OL SAATHI Offline Print Engine · Page $pageNum"
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
        pack: VerifiedContentPack
    ): File? {
        val covered = lines.filter { it.isCovered }
        if (covered.isEmpty()) return null

        targetPaint.typeface = ScriptFonts.forTarget(context, pack.font)
        val document = PdfDocument()
        val isSample = pack.isSample
        val type = WorksheetType.CLASSROOM_DIALOGUES
        val codes = listOf(type.nipunCode)

        var pageNum = 1
        var y = MARGIN_TOP

        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = document.startPage(pageInfo)
        drawPageBorder(page.canvas)
        var canvas: Canvas = page.canvas

        val targetName = pack.languageEnglish.ifEmpty { "the target language" }
        y = drawEnhancedHeader(canvas, y, type)
        y = drawFieldsRow(canvas, y)
        canvas.drawText("Lesson: $title  ·  $targetName", MARGIN_LEFT, y, lessonTitlePaint)
        y += 18f
        y = drawNipunBlock(canvas, y, codes, emptyList(), type)

        for (line in covered) {
            val needed = 14f + 18f + 14f + 20f + LINE_HEIGHT_WRITE + 20f
            if (y + needed > PAGE_HEIGHT - MARGIN_BOTTOM) {
                drawFooter(canvas, pageNum, codes, type)
                if (isSample) drawSampleStamp(canvas)
                document.finishPage(page)

                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                page = document.startPage(pageInfo)
                drawPageBorder(page.canvas)
                canvas = page.canvas
                y = MARGIN_TOP
            }

            canvas.drawText("Hindi:", MARGIN_LEFT, y, labelPaint)
            y += 14f
            canvas.drawText(line.hindi, MARGIN_LEFT, y, hindiPaint)
            y += 20f

            val source = if (line.live) "Live machine translation · Bhashini" else "Offline pack"
            canvas.drawText("$targetName  ($source):", MARGIN_LEFT, y, labelPaint)
            y += 14f
            canvas.drawText(line.translation!!, MARGIN_LEFT, y, targetPaint)
            y += 22f

            canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
            y += LINE_HEIGHT_WRITE
            canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint)
            y += 15f
        }

        drawFooter(canvas, pageNum, codes, type)
        if (isSample) drawSampleStamp(canvas)
        document.finishPage(page)

        val worksheetsDir = File(context.cacheDir, "worksheets")
        if (!worksheetsDir.exists()) worksheetsDir.mkdirs()
        val file = File(worksheetsDir, "worksheet_imported.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        Log.i(TAG, "Imported Worksheet generated: ${file.name} (${file.length()} bytes, $pageNum pages)")
        return file
    }
}