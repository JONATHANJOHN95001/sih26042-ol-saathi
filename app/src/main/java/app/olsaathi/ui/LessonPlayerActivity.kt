package app.olsaathi.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.olsaathi.OlSaathiApplication
import app.olsaathi.R
import app.olsaathi.audio.PackAudioPlayer
import app.olsaathi.content.AudioProvenance
import app.olsaathi.content.Provenance
import app.olsaathi.content.Translation
import app.olsaathi.content.VerifiedContentPack
import app.olsaathi.databinding.ActivityLessonPlayerBinding
import java.util.Locale

/**
 * Line-by-line lesson walker.
 *
 * Given a lesson ID, walks through its lines one at a time. After the
 * last lesson line, transitions to comprehension questions (kind=="check").
 * No marking, no scores — the teacher asks aloud and the class answers.
 */
class LessonPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLessonPlayerBinding
    private lateinit var pack: VerifiedContentPack
    private lateinit var audioPlayer: PackAudioPlayer
    private lateinit var items: List<PlayerItem>
    private var currentIndex = 0

    /** Slow repeat stays on between phrases: a class working through a lesson
     *  at 0.75x does not want to re-arm it on every line. */
    private var slowMode = false
    private var lessonItemCount = 0  // number of "lesson" kind items (before checks)

    data class PlayerItem(
        val source: String,
        val target: String,
        val en: String,
        val provenance: Provenance,
        /** The label as shown, service name included. See Translation.provenanceLabel. */
        val provenanceLabel: String,
        val reviewerName: String,
        val reviewedOn: String,
        val audioPath: String?,
        val audioProvenance: AudioProvenance,
        /** Drawable name for the picture the class sees. */
        val image: String?,
        val isCheck: Boolean,
    ) {
        /**
         * The same two-part test [Translation.hasAudio] makes: a file to play
         * and a record of where the voice came from. Either one alone is not
         * enough to put sound in front of a class.
         */
        val hasAudio: Boolean get() =
            audioPath != null && audioProvenance != AudioProvenance.NONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack
        audioPlayer = PackAudioPlayer(this)

        // This header read "SANTALI · OL CHIKI" in the layout. With a
        // language dropdown that is a claim the app cannot keep, so it now
        // names whichever pack is loaded.
        val option = (application as OlSaathiApplication).currentLanguageOption()
        val name = option?.english ?: pack.languageEnglish.ifEmpty { "Translation" }
        binding.textTargetLabel.text =
            if (option != null && option.scriptNote.isNotEmpty())
                getString(R.string.target_label_format, name, option.scriptNote).uppercase()
            else name.uppercase()

        val lessonId = intent.getStringExtra(EXTRA_LESSON_ID) ?: run { finish(); return }
        val lessonTitle = intent.getStringExtra(EXTRA_LESSON_TITLE)
            ?: lessonId.replace("-", " ").replaceFirstChar { it.uppercase() }

        // Load typefaces
        val olChikiFont = Typeface.createFromAsset(assets, "fonts/NotoSansOlChiki-Regular.ttf")
        val devaFont = Typeface.createFromAsset(assets, "fonts/NotoSansDevanagari-Regular.ttf")

        // Toolbar
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // ── Build items list ──────────────────────────────────────

        // Lesson entries, sorted numerically by suffix (l1, l2, ... l10)
        val lessonEntries = pack.entries(lessonId)
            .filter { it.kind == "lesson" }
            .sortedBy { extractLineNumber(it.id) }

        // Check entries, sorted by suffix
        val checkEntries = pack.entries(lessonId)
            .filter { it.kind == "check" }
            .sortedBy { extractLineNumber(it.id) }

        lessonItemCount = lessonEntries.size

        val app = application as OlSaathiApplication
        items = (lessonEntries + checkEntries).map { entry ->
            val startMs = System.currentTimeMillis()
            // Lesson content is shipped content and always in the pack by definition.
            // Keep on pack.lookup() rather than routing through TranslationRouter.
            val translation = pack.lookup(entry.source)
            val elapsed = System.currentTimeMillis() - startMs
            app.recordLatency(elapsed)
            val audioPath = pack.audioPath(translation)
            PlayerItem(
                source = translation.source.ifEmpty { entry.source },
                target = translation.target,
                en = translation.en,
                provenance = translation.provenance,
                provenanceLabel = translation.provenanceLabel,
                reviewerName = translation.reviewerName,
                reviewedOn = translation.reviewedOn,
                audioPath = audioPath,
                audioProvenance = translation.audioProvenance,
                image = entry.image,
                isCheck = entry.kind == "check",
            )
        }

        if (items.isEmpty()) {
            binding.textHindi.text = "No lesson content in the pack."
            binding.btnNext.isEnabled = false
            return
        }

        // Set title
        binding.toolbar.title = lessonTitle

        // ── Show first item, or the line we were on before a language switch ──
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0).coerceIn(0, items.size - 1)
        showItem(startIndex, devaFont, olChikiFont)

        // Changing language reloads the same lesson from the new pack. The
        // lesson ids and line counts are identical across packs, so the class
        // stays on the line it was on and only the script changes. This is
        // done by relaunching rather than patching state in place: the fonts,
        // the item list and every audio path are all derived from the pack at
        // create time, and rebuilding half of them is how a screen ends up
        // showing one language in the text and another in the audio.
        LanguagePicker.bind(
            this,
            binding.languageBar.root,
            binding.languageBar.spinnerLanguage,
            binding.languageBar.textLanguageNote,
        ) {
            startActivity(
                Intent(this, LessonPlayerActivity::class.java).apply {
                    putExtra(EXTRA_LESSON_ID, lessonId)
                    putExtra(EXTRA_LESSON_TITLE, lessonTitle)
                    putExtra(EXTRA_START_INDEX, currentIndex)
                }
            )
            finish()
            overridePendingTransition(0, 0)
        }

        // ── Navigation ────────────────────────────────────────────
        binding.btnBack.setOnClickListener {
            if (currentIndex > 0) {
                showItem(currentIndex - 1, devaFont, olChikiFont)
            }
        }

        binding.btnNext.setOnClickListener {
            if (currentIndex < items.size - 1) {
                showItem(currentIndex + 1, devaFont, olChikiFont)
            } else {
                // Last item: finish
                finish()
            }
        }

        // ── Show the class ────────────────────────────────────────
        // Hands the current line to the child-facing screen and nothing else.
        // No provenance, no counters, no controls: the teacher turns the
        // tablet around and a six-year-old looks at a picture book page.
        binding.btnShowClass.setOnClickListener {
            val item = items[currentIndex]
            startActivity(
                ShowClassActivity.intent(
                    context = this,
                    source = item.source,
                    target = item.target,
                    image = item.image,
                    // Only labelled audio crosses to the child screen, which
                    // shows no provenance of its own.
                    audio = if (item.hasAudio) item.audioPath else null,
                )
            )
        }

        // ── Play button ───────────────────────────────────────────
        binding.btnPlay.setOnClickListener {
            val item = items[currentIndex]
            if (item.hasAudio && item.audioPath != null && audioPlayer.hasAudio(item.audioPath)) {
                audioPlayer.play(item.audioPath,
                    onComplete = { runOnUiThread { binding.btnPlay.isEnabled = true } },
                    onError = { _ ->
                        runOnUiThread { binding.btnPlay.isEnabled = true }
                    },
                    speed = if (slowMode) SLOW_SPEED else 1f
                )
            }
        }

        binding.btnSlow.setOnClickListener {
            slowMode = !slowMode
            refreshSlowButton()
        }
        refreshSlowButton()
    }

    /** Filled while slow repeat is on, so the state is visible without a label. */
    private fun refreshSlowButton() {
        binding.btnSlow.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (slowMode) R.color.md_theme_primaryContainer
                else android.R.color.transparent
            )
        )
    }

    private fun showItem(index: Int, devaFont: Typeface, olChikiFont: Typeface) {
        currentIndex = index
        val item = items[index]
        val isCheck = item.isCheck

        // ── Header ────────────────────────────────────────────────
        if (isCheck) {
            val checkIndex = index - lessonItemCount
            val totalChecks = items.size - lessonItemCount
            binding.textCounter.text = "Check ${checkIndex + 1} / $totalChecks"
            binding.textSectionLabel.text = "ASK THE CLASS"
            binding.textSectionLabel.setTextColor(Color.parseColor("#C62828"))
        } else {
            binding.textCounter.text = "${index + 1} / $lessonItemCount"
            binding.textSectionLabel.text = "HINDI"
            binding.textSectionLabel.setTextColor(ContextCompat.getColor(this, R.color.md_theme_onSurfaceVariant))
        }

        // ── Progress dots ─────────────────────────────────────────
        binding.progressDots.removeAllViews()
        for (i in 0 until items.size) {
            val dot = View(this).apply {
                val size = if (i == currentIndex) 24.dp else 8.dp
                layoutParams = LinearLayout.LayoutParams(size, 8.dp).apply {
                    marginStart = if (i == currentIndex) 4.dp else 2.dp
                    marginEnd = if (i == currentIndex) 4.dp else 2.dp
                }
                setBackgroundColor(if (i == currentIndex) {
                    ContextCompat.getColor(context, R.color.md_theme_primary)
                } else {
                    ContextCompat.getColor(context, R.color.md_theme_outlineVariant)
                })
                clipToOutline = true
            }
            binding.progressDots.addView(dot)
        }

        // ── Hindi card ────────────────────────────────────────────
        binding.textHindi.text = item.source
        binding.textHindi.typeface = devaFont
        binding.textHindi.textSize = 20f

        // ── Santali card ──────────────────────────────────────────
        if (item.target.isNotEmpty()) {
            binding.textTarget.text = item.target
            binding.textTarget.typeface = olChikiFont
            binding.textTarget.textSize = 26f
            binding.textTarget.visibility = View.VISIBLE

            // Provenance pill
            binding.textProvenance.showProvenanceBadge(
                item.provenanceLabel,
                provenanceColour(this, item.provenance)
            )
            binding.textProvenance.visibility = View.VISIBLE

            // Reviewer info
            if (item.provenance == Provenance.HUMAN_VERIFIED && item.reviewerName.isNotEmpty()) {
                val date = try {
                    val parts = item.reviewedOn.split("-")
                    if (parts.size == 3) "${parts[2].toInt()} ${monthName(parts[1].toInt())} ${parts[0]}"
                    else item.reviewedOn
                } catch (e: Exception) { item.reviewedOn }
                binding.textReviewer.text = "Checked by ${item.reviewerName}, $date"
                binding.textReviewer.visibility = View.VISIBLE
            } else {
                binding.textReviewer.visibility = View.GONE
            }
        } else {
            binding.textTarget.text = "Not in the offline pack"
            binding.textTarget.typeface = Typeface.DEFAULT
            binding.textTarget.textSize = 16f
            binding.textTarget.setTextColor(ContextCompat.getColor(this, R.color.md_theme_outline))
            binding.textProvenance.visibility = View.GONE
            binding.textReviewer.visibility = View.GONE
        }

        // ── Play button ───────────────────────────────────────────
        // Enabled only when the wav exists and the pack says where the voice
        // came from, so the button and the label beneath it cannot disagree.
        binding.btnPlay.isEnabled =
            item.hasAudio && item.audioPath != null && audioPlayer.hasAudio(item.audioPath)

        if (item.hasAudio) {
            binding.textAudioProvenance.text = item.audioProvenance.label
            binding.textAudioProvenance.visibility = View.VISIBLE
        } else {
            binding.textAudioProvenance.visibility = View.GONE
        }

        // ── Navigation buttons ────────────────────────────────────
        binding.btnBack.visibility = if (currentIndex > 0) View.VISIBLE else View.INVISIBLE

        if (currentIndex == items.size - 1) {
            // Last item: show Finish
            binding.btnNext.text = "Finish ✓"
        } else if (currentIndex == lessonItemCount - 1 && lessonItemCount < items.size) {
            // Last lesson line: transition to questions
            binding.btnNext.text = "Questions →"
        } else {
            binding.btnNext.text = if (isCheck) "Next question →" else "Next →"
        }
    }

    /** Extract numeric suffix from IDs like "neema-dadi.l10" → 10 */
    private fun extractLineNumber(id: String): Int {
        val dotIdx = id.lastIndexOf('.')
        if (dotIdx < 0 || dotIdx >= id.length - 1) return Int.MAX_VALUE
        val suffix = id.substring(dotIdx + 1)
        val numStr = suffix.filter { it.isDigit() }
        return numStr.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun monthName(m: Int): String = when (m) {
        1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
        5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
        9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
        else -> ""
    }

    /** Extension for dp to px conversion */
    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.release()
    }

    companion object {
        /** Classroom repeat speed. Slow enough to hear each syllable,
         *  not so slow the vowels smear. */
        const val SLOW_SPEED = 0.75f
        const val EXTRA_LESSON_ID = "lesson_id"
        const val EXTRA_LESSON_TITLE = "lesson_title"

        /**
         * Which line to open on, used when the screen relaunches itself after
         * a language switch so the class does not get sent back to line one.
         */
        const val EXTRA_START_INDEX = "start_index"
    }
}
