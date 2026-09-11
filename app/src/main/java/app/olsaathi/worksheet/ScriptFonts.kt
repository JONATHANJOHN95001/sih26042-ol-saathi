package app.olsaathi.worksheet

import android.content.Context
import android.graphics.Typeface
import android.util.Log

/**
 * Typefaces for the scripts the packs are written in, loaded once and reused.
 *
 * WHY THIS EXISTS
 * ---------------
 * The worksheet and flashcard generators used to name Ol Chiki directly,
 * because there was exactly one target language. With a language dropdown the
 * typeface is a property of the pack on screen, not of the PDF class, and it
 * cannot be resolved when the generator is constructed because the pack is
 * not known until generate() is called.
 *
 * WHY IT THROWS
 * -------------
 * Android falls back to a default font when asked for a glyph it does not
 * have, and the result is a page of empty boxes that still prints, still
 * looks like a worksheet, and is completely unusable to a child. The original
 * code chose to throw rather than let that happen, and this keeps that choice:
 * a missing font is a build error, and it should stop the PDF rather than
 * quietly produce a broken one.
 *
 * The manifest is the first line of defence, since it marks a language
 * unavailable when its font is not bundled. This is the second.
 */
object ScriptFonts {

    private const val TAG = "ScriptFonts"

    /** Used when a pack predates the `font` field. */
    const val DEFAULT_TARGET_FONT = "NotoSansOlChiki-Regular.ttf"

    /** The Hindi source is always Devanagari, whatever the target is. */
    const val SOURCE_FONT = "NotoSansDevanagari-Regular.ttf"

    private val cache = mutableMapOf<String, Typeface>()

    /**
     * Load a font from assets/fonts by file name.
     *
     * @throws RuntimeException if the asset is missing or unreadable, naming
     *         the file, so the failure says which font to add rather than
     *         leaving someone to guess from a page of boxes.
     */
    fun load(context: Context, assetName: String): Typeface {
        cache[assetName]?.let { return it }
        val tf = try {
            Typeface.createFromAsset(context.assets, "fonts/$assetName")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: could not load font $assetName", e)
            throw RuntimeException(
                "$assetName failed to load from assets/fonts. " +
                    "The document cannot render this script without it.", e
            )
        }
        cache[assetName] = tf
        return tf
    }

    /** The typeface for a pack's target language, honouring its `font` field. */
    fun forTarget(context: Context, packFont: String): Typeface =
        load(context, packFont.ifEmpty { DEFAULT_TARGET_FONT })

    /** The typeface for the Hindi source text. */
    fun forSource(context: Context): Typeface = load(context, SOURCE_FONT)
}
