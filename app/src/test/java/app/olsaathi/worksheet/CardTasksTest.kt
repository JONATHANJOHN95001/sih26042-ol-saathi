package app.olsaathi.worksheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CardTasksTest {

    private val lines = listOf(
        SheetLine("l1", "दादी कहीं नहीं आती-जाती हैं।", "t1", image = "ic_card_grandmother"),
        SheetLine("l2", "नीमा दोपहर में स्कूल से लौटती है।", "t2", image = "ic_card_school"),
        SheetLine("l3", "शाम को नीमा खेलने जाती है।", "t3"),
        SheetLine("l4", "उनके घुटनों में दर्द रहता है।", "t4", image = "ic_card_knee"),
        SheetLine("l5", "नीमा दौड़कर चप्पल लाई।", "t5"),
        SheetLine("l6", "वह नीमा का इंतज़ार करती है।", "t6", image = "ic_card_wait"),
        SheetLine("l7", "दोनों मैदान की ओर चल पड़ीं।", "t7"),
        SheetLine("l8", "एक दिन दादी बोलीं।", "t8", image = "ic_card_talk"),
    )

    @Test
    fun everyCardGetsATaskWithAPublishedCode() {
        val tasks = CardTasks.assign(lines, Random(7))
        assertEquals(lines.size, tasks.size)
        val published = setOf("ECL1 4.1", "ECL1 4.6", "ECL1 4.8", "ECL2 4.5")
        tasks.forEach { assertTrue(it.nipun, it.nipun in published) }
        assertEquals(published, CardTasks.codes)
    }

    @Test
    fun aWordTaskNamesAWordFromItsOwnLine() {
        repeat(20) { seed ->
            CardTasks.assign(lines, Random(seed)).forEachIndexed { i, t ->
                val quoted = Regex("'([^']+)'").find(t.hindi)?.groupValues?.get(1) ?: return@forEachIndexed
                assertTrue("$quoted not in ${lines[i].hindi}", lines[i].hindi.contains(quoted))
                assertTrue(t.english.contains("'$quoted'"))
            }
        }
    }

    @Test
    fun thePictureTaskOnlyGoesOnCardsWithAPicture() {
        repeat(30) { seed ->
            CardTasks.assign(lines, Random(seed)).forEachIndexed { i, t ->
                if (t.nipun == "ECL1 4.6") assertTrue(lines[i].image != null)
            }
        }
    }

    @Test
    fun aDeckMixesTheKindsAndANewSeedDealsANewDeck() {
        val a = CardTasks.assign(lines, Random(1))
        // Eight cards, eight kinds: dealt from a pile, none repeats.
        assertTrue(a.map { it.english.substringBefore('\'') }.distinct().size >= 7)
        val decks = (1..10).map { CardTasks.assign(lines, Random(it.toLong())) }.distinct()
        assertTrue(decks.size > 5)
        assertEquals(a, CardTasks.assign(lines, Random(1)))
    }
}
