package app.olsaathi.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olsaathi.OlSaathiApplication
import app.olsaathi.R
import app.olsaathi.audio.PackAudioPlayer
import app.olsaathi.content.VerifiedContentPack
import app.olsaathi.databinding.ActivityLessonListBinding
import app.olsaathi.worksheet.ScriptFonts
import app.olsaathi.worksheet.SheetMaterial

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

    /** Plays a card's first line from the offline pack. */
    private lateinit var player: PackAudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack
        player = PackAudioPlayer(this)

        // White ⋮ on the forest header; the light theme's dark default
        // vanished against it. The language band takes the design's cream.
        binding.toolbar.overflowIcon?.mutate()?.setTint(android.graphics.Color.WHITE)
        binding.languageBar.root.setBackgroundColor(0xFFF4ECE1.toInt())

        // Started on top of this screen rather than replacing it as the
        // launcher, so a fault in onboarding leaves a working app underneath
        // instead of a first launch that goes nowhere.

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
        binding.btnProof.setOnClickListener {
            startActivity(Intent(this, CheckAndProofActivity::class.java))
        }

        rebuildLessonList()
        buildTextbookShelf()

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
        BottomNavIcons.apply(binding.bottomNav)
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
                audio = phraseEntries.first().audio,
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
                audio = entries.firstOrNull()?.audio,
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
        binding.recyclerLessons.adapter = LessonAdapter(
            items, targetTypeface,
            hasAudio = { path -> player.hasAudio(path) },
            onListen = { path -> player.play(path) },
        ) { item ->
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

    // ── NCERT textbook shelf ──────────────────────────────────────────────

    /**
     * The NCERT books for the class the teacher picks, from the 2026-27 list
     * in assets/ncert. The books are NCERT copyright and are not in the app:
     * "NCERT e-book" opens NCERT's own free e-textbooks (internet), and "Add a
     * chapter PDF" imports a chapter the teacher has, which then becomes
     * material for question sets and flashcards on the Materials screen.
     */
    private fun buildTextbookShelf() {
        val json = runCatching {
            org.json.JSONObject(assets.open("ncert/textbooks_class1_5_2026_27.json")
                .bufferedReader(Charsets.UTF_8).readText())
        }.getOrNull() ?: return
        val classes = json.getJSONArray("classes")
        val prefs = getSharedPreferences("olsaathi", MODE_PRIVATE)
        val chosen = prefs.getInt(PREF_CLASS, 1).coerceIn(1, 5)
        val dp = resources.displayMetrics.density

        val chips = binding.rowClassChips
        chips.removeAllViews()
        for (n in 1..5) {
            chips.addView(TextView(this).apply {
                text = n.toString()
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (n == chosen) 0xFFFFFFFF.toInt() else 0xFF292524.toInt())
                setBackgroundResource(R.drawable.bg_home_lang_tab)
                isSelected = n == chosen
                contentDescription = "Class $n"
                setOnClickListener {
                    prefs.edit().putInt(PREF_CLASS, n).apply()
                    buildTextbookShelf()
                }
            }, android.widget.LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()).apply {
                marginStart = (6 * dp).toInt()
            })
        }

        val cls = (0 until classes.length()).map { classes.getJSONObject(it) }
            .firstOrNull { it.getInt("class") == chosen } ?: return
        val books = cls.getJSONArray("books")
        binding.textShelfNote.text = "Class $chosen (कक्षा $chosen): ${books.length()} books. " +
            "Download a chapter from NCERT; it joins the lesson list in Materials for worksheets and flashcards."
        val saved = SheetMaterial.listImported(this, pack.languageCode).map { it.second }
        val row = binding.rowTextbooks
        row.removeAllViews()
        for (i in 0 until books.length()) {
            val b = books.getJSONObject(i)
            val v = layoutInflater.inflate(R.layout.item_textbook, row, false)
            val subject = b.getString("subject")
            val (emoji, tint) = when {
                subject.startsWith("English") -> "📘" to 0xFFDBEAFE.toInt()
                subject.startsWith("Hindi") -> "📙" to 0xFFFDE3D6.toInt()
                subject.startsWith("Math") -> "🔢" to 0xFFE3F4EA.toInt()
                subject.startsWith("The World") -> "🌏" to 0xFFE0F2FE.toInt()
                subject.startsWith("Arts") -> "🎨" to 0xFFFCE7F3.toInt()
                else -> "🤸" to 0xFFFEF3C7.toInt()
            }
            v.findViewById<TextView>(R.id.textBookCover).apply {
                text = emoji
                background.mutate().setTint(tint)
            }
            // The class is on the chip and the note above; here it wrapped
            // "The World Around Us" to two lines and put that card out of line.
            v.findViewById<TextView>(R.id.textBookSubject).text = subject.uppercase()
            val title = b.getString("title")
            v.findViewById<TextView>(R.id.textBookTitle).text =
                title + (b.optString("titleHi").takeIf { it.isNotEmpty() }?.let { "  $it" } ?: "")
            val hindiMedium = b.optJSONObject("hindiMedium")
            v.findViewById<TextView>(R.id.textBookHindi).apply {
                text = hindiMedium?.let { "Hindi medium: " + it.optString("titleHi").ifEmpty { it.getString("title") } }
                    ?: b.getString("full")
            }
            v.findViewById<View>(R.id.btnBookOpen).setOnClickListener {
                // NCERT's own free e-textbook page; the teacher picks the class
                // and book there. Needs internet, and says so on the button.
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ncert.nic.in/textbook.php")))
                }
            }
            // A Hindi book, or the Hindi-medium edition of one, comes from
            // NCERT chapter by chapter. An English-only book cannot feed a
            // Hindi-to-mother-tongue app, and its card says so.
            val ownCode = b.optString("ncert")
            val code = ownCode.ifEmpty { hindiMedium?.optString("ncert").orEmpty() }
            val source = if (ownCode.isNotEmpty()) title else hindiMedium?.optString("title") ?: title
            val book = "$source (Class $chosen)"
            val have = saved.count { it.startsWith("$book · ") }
            v.findViewById<TextView>(R.id.textBookStatus).apply {
                visibility = if (have > 0) View.VISIBLE else View.GONE
                text = "✓ $have " + (if (have == 1) "chapter" else "chapters") + " in Materials"
            }
            v.findViewById<TextView>(R.id.btnBookChapter).apply {
                if (code.isEmpty()) {
                    text = "English book: Ol Saathi reads Hindi"
                    isEnabled = false
                    alpha = 0.5f
                } else {
                    setOnClickListener { showChapterPicker(book, code) }
                }
            }
            row.addView(v)
        }
    }

    override fun onResume() {
        super.onResume()
        // A chapter downloaded since the shelf was drawn shows as downloaded.
        buildTextbookShelf()
    }

    /**
     * Ask NCERT which chapters [code] has and how big each is, then let the
     * teacher pick one. The chapters are probed with HEAD requests, so no file
     * is fetched until one is chosen, and the size is shown because one Khel
     * Yoga chapter is 61 MB. The list ends at the first chapter that is not there.
     */
    private fun showChapterPicker(book: String, code: String) {
        val asking = AlertDialog.Builder(this)
            .setTitle(book)
            .setMessage("Asking NCERT for this book's chapters...")
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        lifecycleScope.launch {
            val sizes = withContext(Dispatchers.IO) {
                coroutineScope {
                    (1..MAX_CHAPTERS).map { n -> async { chapterSize(chapterUrl(code, n)) } }.awaitAll()
                }
            }
            if (!asking.isShowing) return@launch
            asking.dismiss()
            val found = sizes.takeWhile { it != null }.map { it!! }
            if (found.isEmpty() || found.all { it == 0L }) {
                AlertDialog.Builder(this@LessonListActivity)
                    .setTitle(book)
                    .setMessage("Could not reach NCERT. Downloading a chapter needs the internet; " +
                        "chapters already in Materials work offline.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            val have = SheetMaterial.listImported(this@LessonListActivity, pack.languageCode).map { it.second }.toSet()
            val labels = found.mapIndexed { i, bytes ->
                val name = "Chapter ${i + 1}"
                (if ("$book · $name" in have) "✓  " else "") + name + "  ·  " +
                    (if (bytes > 0) "%.1f MB".format(bytes / 1e6) else "size unknown")
            }.toTypedArray()
            AlertDialog.Builder(this@LessonListActivity)
                .setTitle("$book · NCERT")
                .setItems(labels) { _, i ->
                    startActivity(Intent(this@LessonListActivity, ImportLessonActivity::class.java)
                        .putExtra(ImportLessonActivity.EXTRA_URL, chapterUrl(code, i + 1))
                        .putExtra(ImportLessonActivity.EXTRA_BOOK, book)
                        .putExtra(ImportLessonActivity.EXTRA_CHAPTER, "Chapter ${i + 1}"))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun chapterUrl(code: String, n: Int) = "https://ncert.nic.in/textbook/pdf/%s%02d.pdf".format(code, n)

    /**
     * Bytes in one NCERT chapter file; null when NCERT says there is no such
     * chapter; 0 when it did not answer. Its server drops the odd request, so
     * a chapter is asked about three times before it counts as unanswered,
     * and an unanswered chapter never cuts the list short.
     */
    private fun chapterSize(url: String): Long? {
        repeat(3) {
            try {
                val c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                try {
                    when (c.responseCode) {
                        200 -> return c.contentLengthLong.coerceAtLeast(1L)
                        404 -> return null
                    }
                } finally {
                    c.disconnect()
                }
            } catch (_: Exception) {
                // Asked again below.
            }
        }
        return 0L
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
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
        /** Pack clip for [target], or null when the line has no recording. */
        val audio: String? = null,
    )

    class LessonAdapter(
        private val items: List<LessonItem>,
        private val targetTypeface: Typeface?,
        private val hasAudio: (String) -> Boolean,
        private val onListen: (String) -> Unit,
        private val onClick: (LessonItem) -> Unit,
    ) : RecyclerView.Adapter<LessonAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val target: TextView = view.findViewById(R.id.textTarget)
            val hindi: TextView = view.findViewById(R.id.textHindi)
            val text1: TextView = view.findViewById(R.id.textTitle)
            val text2: TextView = view.findViewById(R.id.textSubtitle)
            val nipun: TextView = view.findViewById(R.id.textNipun)
            val listen: View = view.findViewById(R.id.btnListen)
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
            // Only offered when the clip is really in the APK.
            val clip = item.audio?.takeIf { it.isNotBlank() && hasAudio(it) }
            holder.listen.visibility = if (clip == null) View.GONE else View.VISIBLE
            holder.listen.setOnClickListener { clip?.let(onListen) }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        const val EXTRA_LESSON_ID = "lesson_id"

        /** The class (1 to 5) whose NCERT books the shelf shows. */
        const val PREF_CLASS = "teacher_class"

        /** No Class 1 to 5 book has more chapters than Sarangi 2's 26. */
        private const val MAX_CHAPTERS = 30
    }
}
