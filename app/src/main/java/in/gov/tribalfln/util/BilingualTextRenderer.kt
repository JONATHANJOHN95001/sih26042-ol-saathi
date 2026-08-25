package `in`.gov.tribalfln.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import `in`.gov.tribalfln.engine.TribalPhonemeMatcher

/**
 * BilingualTextRenderer — Renders dual-script (Devanagari + Ol Chiki)
 * text with automatic font fallback for NIPUN FLN educational materials.
 * Supports Santhali (Ol Chiki), Ho (Warang Citi), and Mundari scripts.
 */
class BilingualTextRenderer(private val context: Context) {

    companion object {
        private const val TAG = "BilingualTextRenderer"

        private const val OL_CHIKI_START = 0x1C50
        private const val OL_CHIKI_END = 0x1C6D
        private const val DEVANAGARI_START = 0x0900
        private const val DEVANAGARI_END = 0x097F

        /**
         * Script types detected by the renderer.
         */
        enum class Script {
            OL_CHIKI, DEVANAGARI, LATIN, WHITESPACE, UNKNOWN
        }

        private val _olChikiAlphabetCache: String by lazy {
            buildString {
                for (cp in OL_CHIKI_START..OL_CHIKI_END) {
                    append(cp.toChar())
                }
            }
        }

        /**
         * Detect the script of a single character.
         */
        fun detectScript(ch: Char): Script {
            val code = ch.code
            return when {
                code in OL_CHIKI_START..OL_CHIKI_END -> Script.OL_CHIKI
                code in DEVANAGARI_START..DEVANAGARI_END -> Script.DEVANAGARI
                ch.isWhitespace() -> Script.WHITESPACE
                (code in 0x0041..0x005A) || (code in 0x0061..0x007A) || (code in 0x0030..0x0039) -> Script.LATIN
                else -> Script.UNKNOWN
            }
        }

        /**
         * Get the appropriate Typeface for rendering a given script.
         */
        fun typefaceFor(script: Script): Typeface? {
            return when (script) {
                Script.OL_CHIKI -> {
                    try {
                        Typeface.create("sans-serif", Typeface.NORMAL)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load Ol Chiki typeface: ${e.message}")
                        Typeface.DEFAULT
                    }
                }
                Script.DEVANAGARI -> Typeface.create("sans-serif", Typeface.NORMAL)
                Script.LATIN -> Typeface.DEFAULT
                else -> Typeface.DEFAULT
            }
        }

        /**
         * Get the complete Ol Chiki alphabet as a String.
         */
        fun getOlChikiAlphabet(): String = _olChikiAlphabetCache

        /**
         * Check if the given text consists entirely of Ol Chiki characters
         * and whitespace.
         */
        fun isPureOlChiki(text: String): Boolean {
            if (text.isEmpty()) return false
            for (ch in text) {
                val code = ch.code
                if (code !in OL_CHIKI_START..OL_CHIKI_END && !ch.isWhitespace()) {
                    return false
                }
            }
            return true
        }

        /**
         * Check if the Ol Chiki font asset is available on the device.
         */
        fun isOlChikiAvailable(): Boolean {
            // Ol Chiki is supported on Android 12+ via system font
            return android.os.Build.VERSION.SDK_INT >= 31
        }
    }

    /**
     * Render bilingual text (Hindi + Tribal) onto a Canvas.
     */
    fun renderBilingualText(
        canvas: Canvas,
        hindiText: String,
        tribalText: String,
        x: Float,
        y: Float,
        paint: Paint
    ) {
        // Render Hindi (Devanagari) line
        paint.typeface = typefaceFor(Script.DEVANAGARI)
        canvas.drawText(hindiText, x, y, paint)

        // Render Tribal (Ol Chiki) line below
        paint.typeface = typefaceFor(Script.OL_CHIKI)
        canvas.drawText(tribalText, x, y + paint.textSize * 1.4f, paint)

        // Reset typeface
        paint.typeface = Typeface.DEFAULT
    }
}
