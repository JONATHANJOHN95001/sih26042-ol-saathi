package app.olsaathi.ui

import android.content.Intent
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

        // Overflow menu → Check & Proof
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_check_proof -> {
                    startActivity(Intent(this, CheckAndProofActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Build lesson list from pack
        val lessonIds = pack.lessonIds()
        val phraseEntries = pack.entries(null).filter { it.kind == "phrase" }
        val items = mutableListOf<LessonItem>()

        if (phraseEntries.isNotEmpty()) {
            items.add(LessonItem(
                id = "__phrases__",
                title = "Classroom Phrases (${phraseEntries.size})",
                subtitle = "Hindi → Santali translation phrases",
                count = phraseEntries.size
            ))
        }

        for (lessonId in lessonIds) {
            val entries = pack.entries(lessonId).filter { it.kind == "lesson" }
            val checkEntries = pack.entries(lessonId).filter { it.kind == "check" }
            items.add(LessonItem(
                id = lessonId,
                title = lessonId.replace("-", " ").replaceFirstChar { it.uppercase() },
                subtitle = "${entries.size} sentences, ${checkEntries.size} questions",
                count = entries.size
            ))
        }

        binding.recyclerLessons.layoutManager = LinearLayoutManager(this)
        binding.recyclerLessons.adapter = LessonAdapter(items) { item ->
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

    data class LessonItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val count: Int,
    )

    class LessonAdapter(
        private val items: List<LessonItem>,
        private val onClick: (LessonItem) -> Unit,
    ) : RecyclerView.Adapter<LessonAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val text1: TextView = view.findViewById(android.R.id.text1)
            val text2: TextView = view.findViewById(android.R.id.text2)
            init {
                view.setOnClickListener {
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION) onClick(items[pos])
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.text1.text = item.title
            holder.text2.text = item.subtitle
        }

        override fun getItemCount() = items.size
    }

    companion object {
        const val EXTRA_LESSON_ID = "lesson_id"
    }
}
