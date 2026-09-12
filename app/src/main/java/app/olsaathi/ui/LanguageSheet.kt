package app.olsaathi.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olsaathi.R
import app.olsaathi.content.LanguageOption
import app.olsaathi.worksheet.ScriptFonts

/**
 * "Teach in which language?", the full language chooser (design screen A).
 *
 * Replaces the plain dropdown. A Hindi-medium teacher picking a language for
 * a class needs more than a name: whether the language has recorded audio,
 * which script it is written in, and how many lines are on the tablet. All of
 * that comes from the pack manifest, so the sheet cannot promise audio or
 * lines a pack does not have.
 *
 * It only chooses. The switch itself still goes through [LanguagePicker]'s
 * existing path, so every screen redraws exactly as it did before.
 */
object LanguageSheet {

    /** Santali's pack carries no endonym; this is its own name in Ol Chiki. */
    private const val SANTALI_OL_CHIKI = "ᱥᱟᱱᱛᱟᱲᱤ"

    fun show(
        activity: Activity,
        options: List<LanguageOption>,
        currentCode: String,
        onChosen: (LanguageOption) -> Unit,
    ) {
        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_language_sheet, null)
        dialog.setContentView(view)

        // Santali first: it is the language this app was built for.
        val ordered = options.sortedWith(compareBy({ it.code != "sat" }, { !it.hasAudio }, { it.english }))
        var selected = ordered.firstOrNull { it.code == currentCode } ?: ordered.first()

        val subtitle = view.findViewById<TextView>(R.id.textSheetSubtitle)
        val withAudio = ordered.count { it.hasAudio }
        subtitle.text = "${ordered.size} languages on this tablet · $withAudio with recorded audio"

        val selectedLabel = view.findViewById<TextView>(R.id.textSheetSelected)
        fun showSelected() {
            selectedLabel.text = "Selected: ${selected.english}"
        }
        showSelected()

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerSheet)
        val widthDp = activity.resources.configuration.screenWidthDp
        recycler.layoutManager = GridLayoutManager(activity, when {
            widthDp >= 1000 -> 3
            widthDp >= 600 -> 2
            else -> 1
        })
        val adapter = Adapter(activity, ordered, { selected.code }) { option ->
            selected = option
            showSelected()
        }
        recycler.adapter = adapter

        view.findViewById<TextView>(R.id.editSheetSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                adapter.filter(q)
            }
        })

        view.findViewById<View>(R.id.btnSheetClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnSheetCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnSheetConfirm).setOnClickListener {
            dialog.dismiss()
            if (selected.code != currentCode) onChosen(selected)
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val dm = activity.resources.displayMetrics
            setLayout((dm.widthPixels * 0.92f).toInt(), (dm.heightPixels * 0.88f).toInt())
            setDimAmount(0.45f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        view.clipToOutline = true
        view.background = activity.getDrawable(R.drawable.bg_lang_sheet)
    }

    private class Adapter(
        private val activity: Activity,
        private val all: List<LanguageOption>,
        private val selectedCode: () -> String,
        private val onPick: (LanguageOption) -> Unit,
    ) : RecyclerView.Adapter<Adapter.VH>() {

        private var shown = all

        fun filter(q: String) {
            shown = if (q.isEmpty()) all else all.filter {
                it.english.contains(q, ignoreCase = true) ||
                    endonym(it).contains(q) ||
                    it.scriptLabel.contains(q, ignoreCase = true)
            }
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val english: TextView = v.findViewById(R.id.textLangEnglish)
            val endonym: TextView = v.findViewById(R.id.textLangEndonym)
            val region: TextView = v.findViewById(R.id.textLangRegion)
            val script: TextView = v.findViewById(R.id.textLangScript)
            val audio: TextView = v.findViewById(R.id.textLangAudio)
            val lines: TextView = v.findViewById(R.id.textLangLines)
            val check: View = v.findViewById(R.id.imageLangCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_language_card, parent, false))

        override fun getItemCount() = shown.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val o = shown[position]
            h.english.text = o.english
            val own = endonym(o)
            h.endonym.text = if (own.isBlank() || own == o.english) "" else "($own)"
            // The endonym is in the language's own script; Android has no Ol
            // Chiki face, so it takes the pack's bundled font.
            h.endonym.typeface = runCatching { ScriptFonts.forTarget(activity, o.font) }.getOrNull()
            h.region.visibility = if (o.code == "sat") View.VISIBLE else View.GONE
            h.script.text = "${o.scriptLabel} script"
            if (o.hasAudio) {
                h.audio.text = "🔊 Recorded audio"
                h.audio.setTextColor(0xFF14532D.toInt())
                h.audio.setBackgroundResource(R.drawable.bg_lang_audio_yes)
            } else {
                h.audio.text = "Text only"
                h.audio.setTextColor(0xFF57534E.toInt())
                h.audio.setBackgroundResource(R.drawable.bg_lang_tag)
            }
            h.lines.text = "${o.entryCount} lines"
            val isSel = o.code == selectedCode()
            h.itemView.isSelected = isSel
            h.check.isSelected = isSel
            h.itemView.setOnClickListener {
                onPick(o)
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }
        }

        private fun endonym(o: LanguageOption) =
            if (o.code == "sat" && o.endonym.isBlank()) SANTALI_OL_CHIKI else o.endonym
    }
}
