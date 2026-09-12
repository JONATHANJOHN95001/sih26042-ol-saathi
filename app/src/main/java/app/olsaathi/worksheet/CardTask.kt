package app.olsaathi.worksheet

import kotlin.random.Random

/**
 * What the children do with one flashcard, in English and Hindi, and the
 * published NIPUN Bharat outcome the task practises.
 *
 * The problem statement asks for "activity instructions" aligned to NIPUN
 * Bharat. These are the app's own fixed wording, written for a Class 1 room,
 * not machine translations, so they carry no translation label. A word task
 * names a word taken from the card's own Hindi line.
 */
data class CardTask(val english: String, val hindi: String, val nipun: String, val domain: String)

/**
 * Hands out one task per card, differently on every Generate. A deck mixes
 * speaking, pictures, sounds and writing: the kinds are dealt from a shuffled
 * pile, so no kind repeats until every kind that fits has been used.
 */
object CardTasks {

    private enum class Kind(
        val en: String,
        val hi: String,
        val nipun: String,
        val domain: String,
        val needsWord: Boolean = false,
        val needsPicture: Boolean = false,
    ) {
        // ECL1 4.1: uses own language or school language to express themselves.
        SAY_BOTH("Say it in Hindi, then in your home language.",
            "हिंदी में बोलें, फिर अपनी भाषा में।", "ECL1 4.1", "Oral Language Development"),
        NEW_SENTENCE("Say a new sentence with '%s'.",
            "'%s' से एक नया वाक्य बोलें।", "ECL1 4.1", "Oral Language Development", needsWord = true),
        ACT("Act it out, then tell a friend what it means.",
            "अभिनय करके दिखाएँ, फिर साथी को मतलब बताएँ।", "ECL1 4.1", "Oral Language Development"),

        // ECL1 4.6: relates the picture with the text to predict and understand.
        PICTURE("Look at the picture. What is happening? Now read.",
            "चित्र देखें, क्या हो रहा है? अब पढ़ें।", "ECL1 4.6", "Reading Comprehension", needsPicture = true),

        // ECL1 4.8: awareness of letters and sounds while reading.
        CLAP("Clap the sounds in '%s'.",
            "'%s' की ध्वनियों पर ताली बजाएँ।", "ECL1 4.8", "Decoding", needsWord = true),
        CIRCLE("Find '%s' in the line and circle it.",
            "वाक्य में '%s' ढूँढकर गोला लगाएँ।", "ECL1 4.8", "Decoding", needsWord = true),
        FIRST_SOUND("Which sound does '%s' begin with?",
            "'%s' किस ध्वनि से शुरू होता है?", "ECL1 4.8", "Decoding", needsWord = true),

        // ECL2 4.5: forms letters correctly.
        WRITE("Write '%s' in your notebook.",
            "'%s' को अपनी कॉपी में लिखें।", "ECL2 4.5", "Writing", needsWord = true),
    }

    /** The outcome codes a task can carry, for tests and the proof screen. */
    val codes: Set<String> get() = Kind.values().map { it.nipun }.toSet()

    /** One task for each of [lines], in order. */
    fun assign(lines: List<SheetLine>, rng: Random): List<CardTask> {
        val pile = ArrayList<Kind>()
        return lines.map { line ->
            val words = QuestionGenerator.blankable(line.hindi)
            val fits = { k: Kind -> (!k.needsWord || words.isNotEmpty()) && (!k.needsPicture || line.image != null) }
            if (pile.none(fits)) pile.addAll(Kind.values().toList().shuffled(rng))
            val kind = pile.firstOrNull(fits) ?: Kind.SAY_BOTH
            pile.remove(kind)
            val word = if (kind.needsWord) words.random(rng) else ""
            CardTask(
                english = if (kind.needsWord) kind.en.format(word) else kind.en,
                hindi = if (kind.needsWord) kind.hi.format(word) else kind.hi,
                nipun = kind.nipun,
                domain = kind.domain,
            )
        }
    }
}
