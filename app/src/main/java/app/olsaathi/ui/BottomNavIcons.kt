package app.olsaathi.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import app.olsaathi.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * The bottom tabs drawn with the emoji the design uses (🎙️ Teach,
 * 📚 Lessons, 🎨 Materials) instead of the stock framework icons.
 *
 * Emoji come from the device's colour emoji font, so they are rendered into
 * bitmaps once and the tint is switched off; a tint would flatten them into
 * single-colour blobs. Every screen with the tab bar calls this, so the tabs
 * look the same wherever the teacher is.
 */
object BottomNavIcons {

    private val icons = mapOf(
        R.id.nav_teach to "🎙️",
        R.id.nav_lessons to "📚",
        R.id.nav_worksheet to "🎨",
    )

    fun apply(nav: BottomNavigationView) {
        nav.itemIconTintList = null
        icons.forEach { (id, emoji) ->
            nav.menu.findItem(id)?.icon = render(nav.context, emoji)
        }
    }

    private fun render(context: Context, emoji: String): Drawable {
        val px = (24 * context.resources.displayMetrics.density).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = px * 0.9f
            textAlign = Paint.Align.CENTER
        }
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val baseline = px / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(emoji, px / 2f, baseline, paint)
        return BitmapDrawable(context.resources, bitmap)
    }
}
