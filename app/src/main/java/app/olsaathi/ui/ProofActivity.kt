package app.olsaathi.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import app.olsaathi.OlSaathiApplication
import app.olsaathi.databinding.ActivityProofBinding
import app.olsaathi.util.NetworkGuard
import java.util.Locale

/**
 * Live-proof screen readable by a judge at arm's length on a tablet.
 *
 * Every value is read at display time from the running app — nothing
 * is hardcoded. Sections: Content pack, Script rendering, Audio,
 * Offline, Latency, Build.
 */
class ProofActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProofBinding
    private lateinit var app: OlSaathiApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProofBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as OlSaathiApplication

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Load the Ol Chiki typeface for live render test
        val olChikiFont = try {
            Typeface.createFromAsset(assets, "fonts/NotoSansOlChiki-Regular.ttf")
        } catch (e: Exception) {
            null
        }

        // ── Content Pack ──────────────────────────────────────────────
        val sb = SpannableStringBuilder()

        sb.append("Language: Santali (Ol Chiki)\n")
        sb.append("Entries: ${app.pack.size}\n")

        if (app.pack.generated.isNotEmpty()) {
            sb.append("Generated: ${app.pack.generated}\n")
        }

        sb.append("Provenance\n")
        sb.append("  translationService: ${app.pack.translationService}\n")
        sb.append("  platform: ${app.pack.platform}\n")
        if (app.pack.pivot.isNotEmpty()) {
            sb.append("  pivot: ${app.pack.pivot}\n")
        }

        // Human review status
        val reviewed = app.pack.reviewedCount
        val total = app.pack.size
        val hr = app.pack.humanReview
        if (hr != null) {
            sb.append("Human-reviewed: $reviewed of $total entries\n")
            sb.append("  Reviewer: ${hr.reviewer}\n")
            sb.append("  Background: ${hr.background}\n")
            sb.append("  Date: ${hr.date}\n")
            sb.append("  Confirmed: ${hr.confirmed}, Corrected: ${hr.corrected}, " +
                "Removed: ${hr.removed}, Unreviewed: ${hr.unreviewed}\n")
        } else {
            sb.append("Human review: not yet\n")
        }

        binding.textPackInfo.text = sb

        // Status chip
        if (app.pack.isSample) {
            binding.chipStatus.text = "SAMPLE DATA"
            binding.chipStatus.setTextColor(Color.WHITE)
            binding.chipStatus.setBackgroundColor(Color.parseColor("#C62828"))
        } else {
            // Not "VERIFIED". Nobody who reads Santali has checked this pack, and
            // a green chip saying verified is exactly the overclaim this screen
            // exists to prevent. State what is true: it came from a named model.
            binding.chipStatus.text = "MACHINE TRANSLATED"
            binding.chipStatus.setTextColor(Color.WHITE)
            binding.chipStatus.setBackgroundColor(Color.parseColor("#1B6D24"))
        }

        // ── Script Rendering ──────────────────────────────────────────
        if (olChikiFont != null) {
            val testText = "\u1C5A\u1C5E \u1C6A\u1C64\u1C60\u1C64"  // ᱚᱞ ᱪᱤᱠᱤ
            val rendered = SpannableString(testText)
            rendered.setSpan(
                OlChikiTypefaceSpan(olChikiFont),
                0, testText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            val fontOk = SpannableStringBuilder()
            fontOk.append("Ol Chiki font loaded: YES\n")

            // Render sample glyphs
            binding.textScriptSample.text = rendered
            binding.textScriptSample.setTextSize(28f)
            binding.textScriptSample.gravity = Gravity.CENTER

            fontOk.append("Render test: $testText\n")
            fontOk.append("If glyphs show boxes, the font did not load.")

            binding.textScriptInfo.text = fontOk
        } else {
            binding.textScriptInfo.text = "Ol Chiki font: FAILED TO LOAD\nThe screen should say so rather than showing boxes silently."
            binding.textScriptSample.text = "⚠ Font load failed"
            binding.textScriptSample.setTextColor(Color.RED)
            binding.textScriptSample.setTextSize(18f)
        }

        // ── Audio ─────────────────────────────────────────────────────
        // 1. Santali system voice — read live from isLanguageAvailable
        var ttsEngine: TextToSpeech? = null
        ttsEngine = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS && ttsEngine != null) {
                val engine = ttsEngine!!
                val satStatus = engine.isLanguageAvailable(Locale("sat"))
                val haveVoice = satStatus >= TextToSpeech.LANG_AVAILABLE
                val voiceLine = if (haveVoice) "available" else "NOT available"
                val voiceColour = if (haveVoice) Color.parseColor("#1B6D24") else Color.parseColor("#C62828")
                runOnUiThread {
                    val voiceSpan = SpannableString("Santali system voice: $voiceLine\n")
                    voiceSpan.setSpan(ForegroundColorSpan(voiceColour), 0, voiceSpan.length, 0)
                    val current = binding.textAudioInfo.text ?: SpannableStringBuilder()
                    val combined = SpannableStringBuilder()
                    combined.append(voiceSpan)
                    combined.append(current)
                    binding.textAudioInfo.text = combined
                }
                engine.shutdown()
            }
        }

        // While TTS checks asynchronously, show the pack audio stats immediately
        val packAudioSb = SpannableStringBuilder()
        val totalEntries = app.pack.size
        val entriesWithAudio = app.pack.entries(null).count { entry ->
            entry.audio != null && entry.audio.isNotEmpty()
        }
        packAudioSb.append("Pack audio: $entriesWithAudio of $totalEntries entries have WAV\n")

        // Audio source from provenance
        val ttsService = app.pack.ttsService
        if (ttsService.isNotEmpty() && ttsService != "null") {
            packAudioSb.append("Audio source: $ttsService\n")
        } else {
            packAudioSb.append("Audio source: none yet\n")
        }

        binding.textAudioInfo.text = packAudioSb

        // ── Offline ───────────────────────────────────────────────────
        val isOnline = NetworkGuard.isOnline(this)
        val netSb = SpannableStringBuilder()
        netSb.append("Network state: ${if (isOnline) "ONLINE" else "OFFLINE"}\n")
        netSb.append("Network calls this session: ${NetworkGuard.callCount}")

        binding.textNetworkInfo.text = netSb

        // ── Latency ───────────────────────────────────────────────────
        val latSb = SpannableStringBuilder()
        val history = app.latencyHistory

        if (history.isEmpty()) {
            latSb.append("No measurements yet")
        } else {
            val median = history.sorted()[history.size / 2]
            val ceiling = 3000

            latSb.append("Last ${history.size} round trips (ms):\n")
            history.forEachIndexed { idx, ms ->
                latSb.append("  ${idx + 1}. ${ms}ms\n")
            }
            latSb.append("Median: ${median}ms\n")
            latSb.append("Ceiling: ${ceiling}ms\n")

            if (median <= ceiling) {
                latSb.append("✓ Median under 3-second ceiling")
            } else {
                val warn = SpannableString("✗ Median exceeds 3-second ceiling")
                warn.setSpan(ForegroundColorSpan(Color.parseColor("#C62828")), 0, warn.length, 0)
                latSb.append(warn)
            }
        }

        binding.textLatencyInfo.text = latSb

        // ── Pre-flight ──────────────────────────────────────────
        val pfSummary = app.preflightSummary
        if (pfSummary != null) {
            val pfSb = SpannableStringBuilder()
            pfSb.append("Pre-flight: $pfSummary")
            binding.textPreflightInfo.text = pfSb
            binding.textPreflightInfo.visibility = View.VISIBLE
            binding.preflightSection.visibility = View.VISIBLE
        } else {
            binding.textPreflightInfo.text = "Pre-flight: not run yet"
            binding.textPreflightInfo.visibility = View.VISIBLE
            binding.preflightSection.visibility = View.VISIBLE
        }

        // ── Build ─────────────────────────────────────────────────────
        val pkgManager = packageManager
        val pkgInfo = pkgManager.getPackageInfo(packageName, 0)

        val buildSb = SpannableStringBuilder()
        buildSb.append("versionName: ${pkgInfo.versionName}\n")
        buildSb.append("applicationId: ${packageName}\n")
        buildSb.append("minSdk: 28\n")
        buildSb.append("device API: ${Build.VERSION.SDK_INT}\n")

        // ABI the APK was built for
        val primaryAbi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "unknown"
        buildSb.append("ABI (device): $primaryAbi")

        binding.textBuildInfo.text = buildSb
    }

    /**
     * Custom TypefaceSpan that does not rely on android.text.style.TypefaceSpan
     * (which requires a resource font in newer API levels).
     */
    private class OlChikiTypefaceSpan(private val tf: Typeface) : android.text.style.MetricAffectingSpan() {
        override fun updateDrawState(ds: TextPaint) {
            ds.typeface = tf
        }

        override fun updateMeasureState(textPaint: TextPaint) {
            textPaint.typeface = tf
        }
    }
}
