package app.olsaathi.ui

import app.olsaathi.worksheet.WorksheetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for WorksheetPreviewGeometry, the pure A4 fit arithmetic behind the
 * live preview pane. The pane must keep the page at 1:1.414 as it widens and
 * never stretch it, and the page must stay centered inside the pane.
 */
class WorksheetPreviewGeometryTest {

    private val w = WorksheetPreviewGeometry.A4_WIDTH_PT
    private val h = WorksheetPreviewGeometry.A4_HEIGHT_PT

    @Test
    fun aspectRatioIsAlwaysA4() {
        // A wide pane: width no longer limits the fit, so the height must set
        // the scale and the ratio must come out 1:1.414 exactly.
        val wide = WorksheetPreviewGeometry.fitInside(1000f, 500f, w, h)
        assertEquals(h / w, wide[3] / wide[2], 0.001f)

        // A tall pane: the width limits instead, same ratio rule.
        val tall = WorksheetPreviewGeometry.fitInside(300f, 1200f, w, h)
        assertEquals(h / w, tall[3] / tall[2], 0.001f)
    }

    @Test
    fun pageFitsInsidePaneInBothDimensions() {
        val fit = WorksheetPreviewGeometry.fitInside(400f, 700f, w, h)
        assertTrue("fit width $fit exceeds pane", fit[2] <= 400f + 0.01f)
        assertTrue("fit height $fit exceeds pane", fit[3] <= 700f + 0.01f)
    }

    @Test
    fun atLeastOneDimensionTouchesThePane() {
        // If neither dimension touched the pane edge, the page was shrunk
        // more than necessary and the preview renders smaller than it could.
        val fit = WorksheetPreviewGeometry.fitInside(400f, 700f, w, h)
        val touchesWidth = Math.abs(fit[2] - 400f) < 0.01f
        val touchesHeight = Math.abs(fit[3] - 700f) < 0.01f
        assertTrue(touchesWidth || touchesHeight)
    }

    @Test
    fun pageIsCenteredInThePane() {
        val fit = WorksheetPreviewGeometry.fitInside(400f, 700f, w, h)
        assertEquals(fit[0], (400f - fit[2]) / 2f, 0.01f)
        assertEquals(fit[1], (700f - fit[3]) / 2f, 0.01f)
    }

    @Test
    fun changingTypeOrCountChangesThePreviewKey() {
        // The key decides whether a re-render is skipped. If two different
        // parameter sets collided, a changed sheet would silently keep the
        // stale preview.
        val base = WorksheetPreviewKey("greetings", WorksheetType.CLASSROOM_DIALOGUES, 3)
        val otherType = WorksheetPreviewKey("greetings", WorksheetType.TRACE_AND_CONNECT, 3)
        val otherCount = WorksheetPreviewKey("greetings", WorksheetType.CLASSROOM_DIALOGUES, 4)
        val otherLesson = WorksheetPreviewKey("colours", WorksheetType.CLASSROOM_DIALOGUES, 3)
        assertTrue(base != otherType)
        assertTrue(base != otherCount)
        assertTrue(base != otherLesson)
    }

    @Test
    fun a4ConstantsMatchPdfDocumentPage() {
        // WorksheetPdf builds its pages at these exact dimensions; the preview
        // geometry must fit the same page, not a different shape.
        assertEquals(595f, WorksheetPreviewGeometry.A4_WIDTH_PT, 0.0001f)
        assertEquals(842f, WorksheetPreviewGeometry.A4_HEIGHT_PT, 0.0001f)
        // Integer A4 points give 842/595 = 1.4151; the true A4 ratio 1.4142
        // rounds into these page constants. Hold the fit to the constants,
        // which is what PdfDocument actually draws.
        assertEquals(1.414f, 842f / 595f, 0.002f)
    }
}
