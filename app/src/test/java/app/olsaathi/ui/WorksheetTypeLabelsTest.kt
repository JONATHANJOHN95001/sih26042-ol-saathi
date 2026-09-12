package app.olsaathi.ui

import app.olsaathi.worksheet.WorksheetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The type tiles read their English name, Hindi subtitle and NIPUN code off
 * the enum; nothing is retyped in the layout or the activity. These tests pin
 * that contract: five types, each with all three fields populated, and codes
 * that match the published CBSE Class 1 ECL outcomes used throughout the app.
 */
class WorksheetTypeLabelsTest {

    @Test
    fun allFiveTypesExist() {
        assertEquals(
            listOf(
                WorksheetType.TRACE_AND_CONNECT,
                WorksheetType.WORD_FLASH_STRIPS,
                WorksheetType.SCRIPT_TRACING,
                WorksheetType.CLASSROOM_DIALOGUES,
                WorksheetType.QUESTIONS,
            ),
            WorksheetType.values().toList()
        )
    }

    @Test
    fun everyTypeCarriesNameSubtitleAndNipunCode() {
        WorksheetType.values().forEach { type ->
            assertTrue("${type.name} has an empty displayName", type.displayName.isNotBlank())
            assertTrue("${type.name} has an empty hindiSubtitle", type.hindiSubtitle.isNotBlank())
            assertTrue("${type.name} has an empty nipunCode", type.nipunCode.isNotBlank())
        }
    }

    @Test
    fun nipunCodesMatchThePublishedOutcomes() {
        // These are published CBSE Class 1 ECL codes, not ours to invent. If
        // one of these changes, it means the framework mapping changed and the
        // sheets and the tile chips must be re-checked together.
        assertEquals("ECL2 4.5", WorksheetType.TRACE_AND_CONNECT.nipunCode)
        assertEquals("ECL1 4.8", WorksheetType.WORD_FLASH_STRIPS.nipunCode)
        assertEquals("ECL2 4.5", WorksheetType.SCRIPT_TRACING.nipunCode)
        assertEquals("ECL2 4.1a", WorksheetType.CLASSROOM_DIALOGUES.nipunCode)
        // The code the packs already give their comprehension checks.
        assertEquals("ECL1 4.6", WorksheetType.QUESTIONS.nipunCode)
    }
}
