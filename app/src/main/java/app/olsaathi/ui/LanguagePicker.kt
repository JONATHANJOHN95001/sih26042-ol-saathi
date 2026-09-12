package app.olsaathi.ui

import android.app.Activity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import app.olsaathi.OlSaathiApplication
import app.olsaathi.content.LanguageOption

/**
 * Wires the shared language bar to the app's current language.
 *
 * One helper rather than two copies, because the Teach screen and the
 * worksheet screen must agree: a teacher who picks Marathi to speak and then
 * prints a Santali worksheet has been failed by the app, not by themselves.
 * Both screens read and write the same value on the Application.
 *
 * WHAT THE NOTE SAYS, AND WHY IT IS NOT OPTIONAL
 * ---------------------------------------------
 * Two facts about a language are invisible to a teacher who does not read the
 * script, and both change what they should trust:
 *
 *   - whether the language has any audio at all, since a silent play button
 *     otherwise looks like a bug rather than an honest gap
 *   - whether the build's script guard was the strong kind
 *
 * Santali gets the strong guard: its script differs from the Hindi source, so
 * an untranslated line is impossible to miss. The Devanagari languages do not,
 * because an echo of the Hindi is still valid Devanagari. Saying so in one
 * small line is the difference between a dropdown that informs and one that
 * merely looks impressive.
 */
object LanguagePicker {

    /**
     * Populate [spinner] with the available languages and keep the app in sync.
     *
     * @param onChanged called after a successful switch, so the screen can
     *        redraw whatever it is showing in the old language.
     */
    fun bind(
        activity: Activity,
        root: View,
        spinner: Spinner,
        note: TextView?,
        onChanged: (String) -> Unit,
    ) {
        val app = activity.application as OlSaathiApplication
        val options = app.languages

        // A build with one pack has nothing to choose between. Showing a
        // one-item dropdown invites a teacher to look for languages that are
        // not there, so the whole bar hides itself instead. Hiding via the
        // spinner's parent would leave the note row behind, floating with no
        // control above it.
        if (options.size <= 1) {
            root.visibility = View.GONE
            return
        }
        root.visibility = View.VISIBLE

        val adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item,
            options.map { it.menuLabel }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter

        val startIndex = options.indexOfFirst { it.code == app.currentLanguage }
        if (startIndex >= 0) {
            spinner.setSelection(startIndex, false)
            note?.let { showNote(it, options[startIndex]) }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = options.getOrNull(position) ?: return
                if (chosen.code == app.currentLanguage) {
                    note?.let { showNote(it, chosen) }
                    return
                }
                if (app.switchLanguage(chosen.code)) {
                    note?.let { showNote(it, chosen) }
                    onChanged(chosen.code)
                } else {
                    // Put the spinner back where it was. Leaving it showing a
                    // language the app did not actually load is the worst
                    // outcome: everything below would be the old language
                    // under the new label.
                    Toast.makeText(
                        activity,
                        "Could not load ${chosen.menuLabel}. Staying on the current language.",
                        Toast.LENGTH_LONG
                    ).show()
                    val back = options.indexOfFirst { it.code == app.currentLanguage }
                    if (back >= 0) spinner.setSelection(back, false)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // A tap opens the full "Teach in which language?" sheet instead of
        // the bare dropdown. Choosing there selects the same spinner row, so
        // the switch runs through the listener above and nothing else changes.
        // Not performClick(): on a Spinner that opens the old dropdown too.
        @Suppress("ClickableViewAccessibility")
        spinner.setOnTouchListener { _, ev ->
            if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) openSheet(activity, spinner)
            true
        }
        root.setOnClickListener { openSheet(activity, spinner) }
    }

    /** Open the language sheet for [spinner]; public so a header chip can too. */
    fun openSheet(activity: Activity, spinner: Spinner) {
        val app = activity.application as OlSaathiApplication
        val options = app.languages
        LanguageSheet.show(activity, options, app.currentLanguage) { chosen ->
            val index = options.indexOfFirst { it.code == chosen.code }
            if (index >= 0) spinner.setSelection(index)
        }
    }

    private fun showNote(view: TextView, option: LanguageOption) {
        val parts = mutableListOf<String>()
        if (!option.hasAudio) {
            parts.add("No audio in ${option.english} yet")
        }
        if (option.guardIsWeak) {
            parts.add("shares ${option.scriptLabel} with Hindi, so the script check is weaker here")
        }
        if (parts.isEmpty()) {
            view.visibility = View.GONE
        } else {
            view.text = parts.joinToString(" · ")
            view.visibility = View.VISIBLE
        }
    }
}
