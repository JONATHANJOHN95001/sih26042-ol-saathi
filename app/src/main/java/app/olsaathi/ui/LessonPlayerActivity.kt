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
    private var lessonItemCount = 0  // number of "lesson" kind items (before checks)

    data class PlayerItem(
        val source: String,
        val target: String,
        val en: String,
        val provenance: Provenance,
        val reviewerName: String,
        val reviewedOn: String,
        val audioPath: String?,
        val isCheck: Boolean,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack
        audioPlayer = PackAudioPlayer(this)

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

        items = (lessonEntries + checkEntries).map { entry ->
            val translation = pack.lookup(entry.source)
            val audioPath = pack.audioPath(translation)
            PlayerItem(
                source = translation.source.ifEmpty { entry.source },
                target = translation.target,
                en = translation.en,
                provenance = translation.provenance,
                reviewerName = translation.reviewerName,
                reviewedOn = translation.reviewedOn,
                audioPath = audioPath,
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

        // ── Show first item ───────────────────────────────────────
        showItem(0, devaFont, olChikiFont)

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

        // ── Play button ───────────────────────────────────────────
        binding.btnPlay.setOnClickListener {
            val item = items[currentIndex]
            if (item.audioPath != null && audioPlayer.hasAudio(item.audioPath)) {
                audioPlayer.play(item.audioPath,
                    onComplete = { runOnUiThread { binding.btnPlay.isEnabled = true } },                    onError = { _ ->
                        runOnUiThread { binding.btnPlay.isEnabled = true }
                    }
                )
            }
        }
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
            val colour = when (item.provenance) {
                Provenance.HUMAN_VERIFIED -> ContextCompat.getColor(this, R.color.human_verified_blue)
                Provenance.VERIFIED -> ContextCompat.getColor(this, R.color.success_green)
                Provenance.TRANSLITERATED -> ContextCompat.getColor(this, R.color.warning_orange)
                Provenance.UNAVAILABLE -> ContextCompat.getColor(this, R.color.md_theme_outline)
                Provenance.SAMPLE -> ContextCompat.getColor(this, R.color.sample_red)
            }
            binding.textProvenance.text = item.provenance.label
            binding.textProvenance.setTextColor(colour)
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
        binding.btnPlay.isEnabled = item.audioPath != null && audioPlayer.hasAudio(item.audioPath)

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
        const val EXTRA_LESSON_ID = "lesson_id"
        const val EXTRA_LESSON_TITLE = "lesson_title"
    }
}
