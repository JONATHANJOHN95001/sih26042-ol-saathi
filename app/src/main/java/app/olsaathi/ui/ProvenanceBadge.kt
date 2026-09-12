package app.olsaathi.ui

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.olsaathi.R
import app.olsaathi.content.Provenance

/**
 * Render a provenance label as a tinted pill.
 *
 * Provenance is the one thing this app asks a teacher to read before trusting
 * a line, so it should not look like the caption underneath it. Coloured text
 * on a warm paper surface was doing exactly that: at 11sp the colour is the
 * only signal, and colour alone fails for anyone who cannot separate the
 * green from the blue. A filled shape gives the label an edge and a weight
 * that survives being glanced at across a classroom.
 *
 * The fill is the provenance colour at low alpha and the text is the same
 * colour at full strength, so the two can never disagree about which state is
 * being shown.
 */
fun TextView.showProvenanceBadge(label: String, colour: Int) {
    text = label
    setTextColor(colour)
    setBackgroundResource(R.drawable.bg_provenance_pill)
    backgroundTintList = ColorStateList.valueOf(
        ColorUtils.setAlphaComponent(colour, PILL_FILL_ALPHA)
    )
    val h = (resources.displayMetrics.density * 10f).toInt()
    val v = (resources.displayMetrics.density * 5f).toInt()
    setPadding(h, v, h, v)
}

/**
 * Colour for a provenance state.
 *
 * Kept in one place because these colours are a vocabulary: blue means a human
 * checked it, green means the pack produced it, red means it is sample data.
 * Two activities picking their own greens would quietly break that.
 */
fun provenanceColour(context: android.content.Context, provenance: Provenance): Int =
    androidx.core.content.ContextCompat.getColor(
        context,
        when (provenance) {
            Provenance.HUMAN_VERIFIED -> R.color.human_verified_blue
            Provenance.VERIFIED -> R.color.success_green
            Provenance.ONLINE_MACHINE -> R.color.online_machine_teal
            Provenance.ON_DEVICE_MACHINE -> R.color.online_machine_teal
            Provenance.TRANSLITERATED -> R.color.warning_orange
            Provenance.UNAVAILABLE -> R.color.md_theme_outline
            Provenance.SAMPLE -> R.color.sample_red
        }
    )

/** Low enough to stay a background, high enough to read as a filled shape. */
private const val PILL_FILL_ALPHA = 38
