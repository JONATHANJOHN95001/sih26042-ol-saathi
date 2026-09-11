package app.olsaathi.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olsaathi.OlSaathiApplication
import app.olsaathi.R
import app.olsaathi.content.VerifiedContentPack
import app.olsaathi.databinding.ActivityLessonListBinding
import app.olsaathi.worksheet.ScriptFonts

/**
 * Lessons hub — the entry screen. Shows lessons and a "Classroom Phrases"
 * section. Tapping a lesson launches [LessonPlayerActivity].
 * Tapping phrases launches [ClassroomActivity] for lookup.
 *
 * Overflow menu: Check & Proof (merged preflight + proof).
 */
class LessonListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLessonListBinding
    private lateinit var pack: VerifiedContentPack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack

        // Started on top of this screen rather than replacing it as the
        // launcher, so a fault in onboarding leaves a working app underneath
        // instead of a first launch that goes nowhere.
        if (!OnboardingActivity.hasSeen(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        // Overflow menu → Check & Proof, Import PDF
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import_pdf -> {
                    startActivity(Intent(this, ImportLessonActivity::class.java))
                    true
                }
                R.id.action_onboarding -> {
                    startActivity(Intent(this, OnboardingActivity::class.java))
                    true
                }
                R.id.action_check_proof -> {
                    startActivity(Intent(this, CheckAndProofActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Any lesson PDF, turned into NIPUN-tagged worksheets and flashcards.
        // A visible button, because the overflow menu was the only way in.
        binding.btnAddPdf.setOnClickListener {
            startActivity(Intent(this, ImportLessonActivity::class.java))
        }

        rebuildLessonList()

        // Switching language here rebuilds the list in place. The lesson ids
        // are identical across packs, so the same lessons are listed; only the
        // script, the typeface and the language name change.
        LanguagePicker.bind(
            this,
            binding.languageBar.root,
            binding.languageBar.spinnerLanguage,
            binding.languageBar.textLanguageNote,
        ) {
            pack = (application as OlSaathiApplication).pack
            rebuildLessonList()
        }

        // ── Bottom nav ────────────────────────────────────────────
        binding.bottomNav.selectedItemId = R.id.nav_lessons
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_teach -> {
                    startActivity(Intent(this, ClassroomActivity::class.java))
                    finish(); true
                }
                R.id.nav_lessons -> true
                R.id.nav_worksheet -> {
                    startActivity(Intent(this, WorksheetActivity::class.java))
                    finish(); true
                }
                else -> false
            }
        }
    }

    /**
     * Build the lesson list from whichever pack is loaded.
     *
     * Called on create and again on every language switch. It used to run once
     * inline in onCreate, so the only way to see the lessons in another
     * language was to leave this screen, change language elsewhere, and come
     * back.
     */
    private fun rebuildLessonList() {
        val lessonIds = pack.lessonIds()
        val phraseEntries = pack.entries(null).filter { it.kind == "phrase" }
        val items = mutableListOf<LessonItem>()

        if (phraseEntries.isNotEmpty()) {
            items.add(LessonItem(
                id = "__phrases__",
                title = "Classroom Phrases (${phraseEntries.size})",
                subtitle = "Hindi → ${(application as app.olsaathi.OlSaathiApplication).pack.languageEnglish.ifEmpty { "regional" }} translation phrases",
                count = phraseEntries.size,
                target = phraseEntries.first().target,
                source = phraseEntries.first().source,
                nipun = phraseEntries.first().nipun,
            ))
        }

        for (lessonId in lessonIds) {
            val entries = pack.entries(lessonId).filter { it.kind == "lesson" }
            val checkEntries = pack.entries(lessonId).filter { it.kind == "check" }
            items.add(LessonItem(
                id = lessonId,
                title = lessonId.replace("-", " ").replaceFirstChar { it.uppercase() },
                subtitle = "${entries.size} sentences, ${checkEntries.size} questions",
                count = entries.size,
                target = entries.firstOrNull()?.target.orEmpty(),
                source = entries.firstOrNull()?.source.orEmpty(),
                nipun = entries.firstOrNull()?.nipun.orEmpty(),
            ))
        }

        binding.recyclerLessons.layoutManager = LinearLayoutManager(this)
        val targetTypeface = try {
            ScriptFonts.forTarget(this, pack.font)
        } catch (e: Exception) {
            // A pack with no bundled face still lists; it just renders in the
            // system fallback rather than taking the whole screen down.
            null
        }
        binding.recyclerLessons.adapter = LessonAdapter(items, targetTypeface) { item ->
            if (item.id == "__phrases__") {
                startActivity(Intent(this, ClassroomActivity::class.java))
            } else {
                val intent = Intent(this, LessonPlayerActivity::class.java).apply {
                    putExtra(LessonPlayerActivity.EXTRA_LESSON_ID, item.id)
                    putExtra(LessonPlayerActivity.EXTRA_LESSON_TITLE, item.title)
                }
                startActivity(intent)
            }
        }

    }

    data class LessonItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val count: Int,
        /** First real phrase of the lesson, shown as the card anchor. Never
         *  an invented title: no target script is written by hand. */
        val target: String = "",
        val source: String = "",
        /** Published NIPUN outcome code carried by this lesson. */
        val nipun: String = "",
    )

    class LessonAdapter(
        private val items: List<LessonItem>,
        private val targetTypeface: Typeface?,
        private val onClick: (LessonItem) -> Unit,
    ) : RecyclerView.Adapter<LessonAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val target: TextView = view.findViewById(R.id.textTarget)
            val hindi: TextView = view.findViewById(R.id.textHindi)
            val text1: TextView = view.findViewById(R.id.textTitle)
            val text2: TextView = view.findViewById(R.id.textSubtitle)
            val nipun: TextView = view.findViewById(R.id.textNipun)
            init {
                view.setOnClickListener {
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION) onClick(items[pos])
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lesson, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.text1.text = item.title
            holder.text2.text = item.subtitle
            // Each of these hides rather than showing an empty box, because a
            // lesson with no phrases yet must not render a blank anchor.
            holder.target.text = item.target
            holder.target.typeface = targetTypeface ?: Typeface.DEFAULT
            holder.target.visibility = if (item.target.isBlank()) View.GONE else View.VISIBLE
            holder.hindi.text = item.source
            holder.hindi.visibility = if (item.source.isBlank()) View.GONE else View.VISIBLE
            holder.nipun.text = "NIPUN " + item.nipun
            holder.nipun.visibility = if (item.nipun.isBlank()) View.GONE else View.VISIBLE
        }

        override fun getItemCount() = items.size
    }

    companion object {
        const val EXTRA_LESSON_ID = "lesson_id"
    }
}
