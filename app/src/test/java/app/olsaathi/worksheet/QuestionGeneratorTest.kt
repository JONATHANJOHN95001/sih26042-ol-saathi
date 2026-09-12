package app.olsaathi.worksheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionGeneratorTest {

    @Test
    fun setNumbersSpreadEvenWhenTheClockIsCoarse() {
        // Seeds a millisecond apart, as a clock with 1 ms steps would give.
        val numbers = (1L..300L).map { QuestionGenerator.setNumber(1_700_000_000_000_000_000L + it * 1_000_000L) }
        assertTrue(numbers.all { it in 1000..9999 })
        assertTrue("only ${numbers.distinct().size} distinct", numbers.distinct().size > 280)
        assertEquals(QuestionGenerator.setNumber(42L), QuestionGenerator.setNumber(42L))
    }

    private val lines = listOf(
        SheetLine("l1", "नीमा दोपहर में दो बजे स्कूल से लौटती है।", "ᱱᱤᱢᱟ ᱫᱚ ᱛᱤᱠᱤᱱ"),
        SheetLine("l2", "इस समय घर पर सिर्फ़ दादी होती हैं।", "ᱚᱱᱟ ᱚᱠᱛᱚ ᱥᱩᱢᱩᱝ"),
        SheetLine("l3", "दादी कहीं नहीं आती-जाती हैं।", "ᱜᱚᱲᱚᱢ ᱟᱭᱳ ᱫᱚ"),
        SheetLine("l4", "उनके घुटनों में दर्द रहता है।", "ᱩᱱᱤᱭᱟᱜ ᱜᱩᱱᱴᱷᱮ"),
        SheetLine("c1", "इस समय घर पर कौन होता है?", "ᱚᱱᱟ ᱚᱠᱛᱚ ᱚᱲᱟᱜ", kind = "check"),
    )

    @Test
    fun everyChoiceHasTheRightAnswerExactlyOnce() {
        val qs = QuestionGenerator.questions(lines, 50, Random(1))
        assertTrue(qs.isNotEmpty())
        for (q in qs) {
            if (q.kind == QuestionKind.OPEN) {
                assertTrue(q.options.isEmpty())
                continue
            }
            assertEquals(3, q.options.size)
            assertEquals(3, q.options.distinct().size)
            when (q.kind) {
                QuestionKind.CHOOSE_TARGET -> assertEquals(q.line.target, q.answer)
                QuestionKind.CHOOSE_HINDI -> assertEquals(q.line.hindi, q.answer)
                QuestionKind.FILL_BLANK -> {
                    assertTrue(q.line.hindi.contains(q.answer))
                    assertTrue(q.prompt.contains(QuestionGenerator.GAP))
                    assertTrue(!q.prompt.contains(q.answer + " ") || q.prompt != q.line.hindi)
                }
                else -> Unit
            }
        }
    }

    @Test
    fun neverMoreThanTheMaterialCanMakeAndNoRepeats() {
        val cap = QuestionGenerator.capacity(lines)
        val qs = QuestionGenerator.questions(lines, 1000, Random(2))
        assertEquals(cap, qs.size)
        assertEquals(qs.size, qs.map { it.line.id to it.kind }.distinct().size)
    }

    @Test
    fun aShortSheetCoversDifferentLinesFirst() {
        val qs = QuestionGenerator.questions(lines, lines.size, Random(3))
        assertEquals(lines.size, qs.map { it.line.id }.distinct().size)
    }

    @Test
    fun aNewSeedGivesANewSheet() {
        val a = QuestionGenerator.questions(lines, 8, Random(10)).map { it.line.id + it.kind + it.options }
        val b = QuestionGenerator.questions(lines, 8, Random(11)).map { it.line.id + it.kind + it.options }
        assertNotEquals(a, b)
        val again = QuestionGenerator.questions(lines, 8, Random(10)).map { it.line.id + it.kind + it.options }
        assertEquals(a, again)
    }

    @Test
    fun openQuestionsComeOnlyFromTheLessonsOwnChecks() {
        val qs = QuestionGenerator.questions(lines, 100, Random(4))
        qs.filter { it.kind == QuestionKind.OPEN }.forEach { assertEquals("check", it.line.kind) }
    }

    @Test
    fun pickIsDistinctAndCapped() {
        val picked = QuestionGenerator.pick(lines, 3, Random(5))
        assertEquals(3, picked.size)
        assertEquals(3, picked.map { it.id }.distinct().size)
        assertEquals(lines.size, QuestionGenerator.pick(lines, 99, Random(6)).size)
    }
}
