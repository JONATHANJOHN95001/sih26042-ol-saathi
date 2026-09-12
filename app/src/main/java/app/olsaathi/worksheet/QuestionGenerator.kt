package app.olsaathi.worksheet

import app.olsaathi.content.Provenance
import kotlin.random.Random

/**
 * One line of teaching material, whatever it came from: a lesson or the
 * phrase deck in the pack, or a chapter the teacher imported from a PDF.
 * Worksheets and flashcards are built only from these, so every question and
 * every card on a sheet traces back to a line the teacher chose.
 */
data class SheetLine(
    val id: String,
    val hindi: String,
    val target: String,
    val en: String = "",
    /** Devanagari spelling of an Ol Chiki line, for reading aloud; may be empty. */
    val bridge: String = "",
    /** lesson, phrase, check (a comprehension question) or imported. */
    val kind: String = "lesson",
    val nipun: String = "",
    val nipunOutcome: String = "",
    val nipunDomain: String = "",
    val image: String? = null,
    val provenance: Provenance = Provenance.VERIFIED,
    /** Who produced the translation, printed with it. */
    val serviceName: String = "",
) {
    val provenanceLabel: String
        get() = if (serviceName.isNotEmpty() &&
            (provenance == Provenance.VERIFIED || provenance == Provenance.ONLINE_MACHINE ||
                provenance == Provenance.ON_DEVICE_MACHINE)) "${provenance.label} · $serviceName"
        else provenance.label
}

/** The kinds of question a line can be turned into. */
enum class QuestionKind(val english: String, val hindi: String) {
    /** Hindi given; choose its translation from three. */
    CHOOSE_TARGET("Choose the right translation", "सही अनुवाद चुनें"),
    /** Target line given; choose what it means in Hindi. */
    CHOOSE_HINDI("What does it mean in Hindi?", "हिंदी में इसका अर्थ क्या है?"),
    /** A word missing from the Hindi; choose it from three. */
    FILL_BLANK("Fill in the missing word", "खाली जगह भरें"),
    /** One of the lesson's own comprehension questions, answered in writing. */
    OPEN("Answer the question", "प्रश्न का उत्तर दें"),
}

/**
 * One printed question. [options] is empty for [QuestionKind.OPEN];
 * otherwise [answerIndex] points at the right option.
 */
data class Question(
    val kind: QuestionKind,
    val line: SheetLine,
    /** For FILL_BLANK, the Hindi with the missing word replaced by a gap. */
    val prompt: String,
    val options: List<String> = emptyList(),
    val answerIndex: Int = -1,
) {
    val answer: String get() = options.getOrNull(answerIndex) ?: ""
}

/**
 * Turns the selected material into questions, differently every time.
 *
 * Nothing is invented: a question is a line of the material, the wrong options
 * are other lines (or words from other lines) of the same material, and an
 * open question is one the lesson itself asks. What changes on every Generate
 * is which lines are used, their order, the kind of question each becomes and
 * the order of the options, all from the [Random] the caller passes, so a new
 * seed gives a new sheet and the same seed gives the same sheet back.
 */
object QuestionGenerator {

    const val GAP = "________"

    /**
     * The four-digit number printed on every page of a set, so two papers
     * from the same lesson can be told apart. Mixed from the whole seed: the
     * seed is a clock reading, and some clocks tick in steps of 100 ns or
     * more, so taking the seed modulo 9000 left only 90 (or 9) numbers.
     */
    fun setNumber(seed: Long): Int = Random(seed).nextInt(1000, 10000)

    /** How many different questions [lines] can make, for "you asked for N". */
    fun capacity(lines: List<SheetLine>): Int = candidates(lines).size

    /**
     * Up to [count] questions, mixed in kind, each (line, kind) used once.
     * Lines are spread out before any line is reused for a second kind.
     */
    fun questions(lines: List<SheetLine>, count: Int, rng: Random): List<Question> {
        if (count <= 0) return emptyList()
        val byLine = candidates(lines).groupBy { it.first.id }
            .mapValues { (_, v) -> v.map { it.second }.shuffled(rng).toMutableList() }
        val order = lines.filter { byLine.containsKey(it.id) }.shuffled(rng)
        val out = ArrayList<Question>()
        // Round robin over the lines: every line gives one question before
        // any gives a second, so a short sheet covers as much of the lesson
        // as it can.
        while (out.size < count) {
            var added = false
            for (line in order) {
                if (out.size >= count) break
                val kinds = byLine[line.id] ?: continue
                if (kinds.isEmpty()) continue
                build(kinds.removeAt(0), line, lines, rng)?.let { out.add(it); added = true }
            }
            if (!added) break
        }
        return out
    }

    /** Up to [count] distinct lines in a fresh random order. */
    fun pick(lines: List<SheetLine>, count: Int, rng: Random): List<SheetLine> =
        lines.shuffled(rng).take(count.coerceAtLeast(0))

    private fun candidates(lines: List<SheetLine>): List<Pair<SheetLine, QuestionKind>> {
        val usable = lines.filter { it.hindi.isNotBlank() && it.target.isNotBlank() }
        val statements = usable.filter { it.kind != "check" }
        val out = ArrayList<Pair<SheetLine, QuestionKind>>()
        for (line in usable) {
            if (line.kind == "check") {
                out.add(line to QuestionKind.OPEN)
                continue
            }
            // A choice needs two wrong answers from elsewhere in the material.
            if (statements.count { it.target != line.target } >= 2) {
                out.add(line to QuestionKind.CHOOSE_TARGET)
            }
            if (statements.count { it.hindi != line.hindi } >= 2) {
                out.add(line to QuestionKind.CHOOSE_HINDI)
            }
            if (blankable(line.hindi).isNotEmpty() && otherWords(line, statements).size >= 2) {
                out.add(line to QuestionKind.FILL_BLANK)
            }
        }
        return out
    }

    private fun build(kind: QuestionKind, line: SheetLine, lines: List<SheetLine>, rng: Random): Question? {
        val statements = lines.filter { it.kind != "check" && it.hindi.isNotBlank() && it.target.isNotBlank() }
        return when (kind) {
            QuestionKind.OPEN -> Question(kind, line, line.hindi)
            QuestionKind.CHOOSE_TARGET -> {
                val wrong = statements.map { it.target }.filter { it != line.target }
                    .distinct().shuffled(rng).take(2)
                if (wrong.size < 2) null else options(kind, line, line.hindi, line.target, wrong, rng)
            }
            QuestionKind.CHOOSE_HINDI -> {
                val wrong = statements.map { it.hindi }.filter { it != line.hindi }
                    .distinct().shuffled(rng).take(2)
                if (wrong.size < 2) null else options(kind, line, line.target, line.hindi, wrong, rng)
            }
            QuestionKind.FILL_BLANK -> {
                val words = blankable(line.hindi)
                if (words.isEmpty()) return null
                val word = words.random(rng)
                val wrong = otherWords(line, statements).filter { it != word }.shuffled(rng).take(2)
                if (wrong.size < 2) return null
                val gapped = replaceFirstWord(line.hindi, word)
                options(kind, line, gapped, word, wrong, rng)
            }
        }
    }

    private fun options(
        kind: QuestionKind, line: SheetLine, prompt: String, right: String, wrong: List<String>, rng: Random,
    ): Question {
        val all = (wrong + right).shuffled(rng)
        return Question(kind, line, prompt, all, all.indexOf(right))
    }

    /** Words worth blanking: real words, not particles or punctuation. */
    internal fun blankable(hindi: String): List<String> =
        hindi.split(' ').map { clean(it) }.filter { it.length >= 3 }.distinct()

    private fun otherWords(line: SheetLine, statements: List<SheetLine>): List<String> =
        statements.filter { it.id != line.id }.flatMap { blankable(it.hindi) }
            .filter { it !in blankable(line.hindi) }.distinct()

    private fun clean(word: String) = word.trim().trim('।', '॥', '?', '!', ',', '.', '"', '\'', '“', '”', ':', ';')

    private fun replaceFirstWord(sentence: String, word: String): String {
        val parts = sentence.split(' ').toMutableList()
        val i = parts.indexOfFirst { clean(it) == word }
        if (i < 0) return sentence
        parts[i] = parts[i].replace(word, GAP)
        return parts.joinToString(" ")
    }
}
