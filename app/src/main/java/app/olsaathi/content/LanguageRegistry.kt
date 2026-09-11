package app.olsaathi.content

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * One language the app can actually deliver, as described by packs.json.
 *
 * "Actually deliver" is the whole point of [available]. A language is only
 * offered when its pack has entries AND its font is bundled. Offering one
 * without its font would render every line as empty boxes, which is worse
 * than not offering it: a teacher who cannot read the script has no way to
 * tell a missing font from a broken translation.
 */
data class LanguageOption(
    val code: String,
    val english: String,
    val endonym: String,
    /** What the dropdown shows: the endonym when we have a checked one, else English. */
    val display: String,
    val script: String,
    val scriptLabel: String,
    val font: String,
    val fontBundled: Boolean,
    val entryCount: Int,
    val audioCount: Int,
    /**
     * How strong the build-time contamination guard was for this language.
     *
     * "strong"  — target script differs from the Hindi source, so an
     *             untranslated echo is impossible to miss. Santali.
     * "weak-shared-script" — target shares Devanagari with the source. A
     *             verbatim echo is caught, but fluent Hindi passed off as
     *             fluent Marathi is not. The app says so rather than
     *             pretending both cases are equal.
     */
    val guard: String,
    val available: Boolean,
) {
    /**
     * What the language dropdown shows: the endonym with its English name
     * beside it, e.g. "অসমীয়া (Assamese)".
     *
     * The endonym alone is correct and respectful, and it is also unreadable
     * to most of the people choosing from this list. A Hindi-medium teacher
     * picking a language for a class cannot tell Odia from Gujarati by their
     * own scripts, which is exactly the population this app is for. Showing
     * both keeps the language named in its own script without making the
     * menu a guessing game.
     *
     * When the two are the same, as for Santali whose pack carries no
     * endonym, the name is not repeated.
     */
    val menuLabel: String
        get() = if (display.equals(english, ignoreCase = true) || display.isBlank()) {
            english
        } else {
            "$display ($english)"
        }

    /**
     * The script to name in brackets after the language, or empty when saying
     * it adds nothing.
     *
     * "Santali (Ol Chiki)" tells a teacher something they need: the script is
     * not the one the name would suggest. "Tamil (Tamil)" tells them nothing
     * and reads like a bug, and most of the seventeen are in that second case
     * because the script is named after the language.
     */
    val scriptNote: String
        get() = if (scriptLabel.isBlank() || scriptLabel.equals(english, ignoreCase = true)) ""
                else scriptLabel

    val hasAudio: Boolean get() = audioCount > 0

    /** True when the build could not fully vouch for this language's script. */
    val guardIsWeak: Boolean get() = guard != "strong"
}

/**
 * Reads assets/pack/packs.json, the list of packs the APK actually carries.
 *
 * The manifest is generated from the pack files on disk by
 * tools/build_pack_indictrans.py, never hand-maintained, so it cannot drift
 * into claiming a language that did not ship. If the manifest is missing
 * entirely the app falls back to Santali alone, which is the behaviour before
 * multi-language existed and keeps an old pack working.
 */
object LanguageRegistry {

    private const val TAG = "LanguageRegistry"
    private const val MANIFEST = "pack/packs.json"

    /** Used when packs.json is absent, so a single-pack APK still runs. */
    const val FALLBACK_CODE = "sat"

    private var cached: List<LanguageOption>? = null

    fun all(context: Context): List<LanguageOption> {
        cached?.let { return it }
        val loaded = try {
            parse(context.assets.open(MANIFEST).bufferedReader(Charsets.UTF_8)
                .use { it.readText() })
        } catch (e: Exception) {
            Log.w(TAG, "No usable $MANIFEST, falling back to $FALLBACK_CODE only", e)
            emptyList()
        }
        cached = loaded
        return loaded
    }

    /** Only the languages that are safe to offer in a dropdown. */
    fun available(context: Context): List<LanguageOption> =
        all(context).filter { it.available }

    fun byCode(context: Context, code: String): LanguageOption? =
        all(context).firstOrNull { it.code == code }

    /**
     * The language to use when nothing is chosen yet, or when a stored choice
     * no longer ships.
     *
     * Prefers Santali, because it is the one this project did properly and the
     * one the problem statement names. Falls back to whatever is available if
     * a build ever drops it.
     */
    fun default(context: Context): String {
        val av = available(context)
        return when {
            av.any { it.code == FALLBACK_CODE } -> FALLBACK_CODE
            av.isNotEmpty() -> av.first().code
            else -> FALLBACK_CODE
        }
    }

    private fun parse(json: String): List<LanguageOption> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("languages") ?: return emptyList()
        val out = ArrayList<LanguageOption>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("code", "")
            if (code.isEmpty()) continue
            val english = o.optString("english", code)
            out.add(
                LanguageOption(
                    code = code,
                    english = english,
                    endonym = o.optString("endonym", ""),
                    display = o.optString("display", "").ifEmpty { english },
                    script = o.optString("script", ""),
                    scriptLabel = o.optString("scriptLabel", ""),
                    font = o.optString("font", ""),
                    fontBundled = o.optBoolean("fontBundled", false),
                    entryCount = o.optInt("entries", 0),
                    audioCount = o.optInt("audioEntries", 0),
                    guard = o.optString("guard", "strong"),
                    available = o.optBoolean("available", false),
                )
            )
        }
        return out
    }

    /** Test seam. The manifest is immutable at runtime, so this is only for tests. */
    internal fun resetForTest() {
        cached = null
    }

    internal fun parseForTest(json: String): List<LanguageOption> = parse(json)
}
