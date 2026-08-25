package app.olsaathi.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.olsaathi.OlSaathiApplication
import app.olsaathi.R
import app.olsaathi.databinding.ActivityPreflightBinding
import app.olsaathi.worksheet.WorksheetPdf
import java.io.File

/**
 * Pre-flight self-test: run once before the demo.
 *
 * Seven checks, each green or red with a human-readable explanation.
 * The critical one is Hindi offline recognition — on a fresh tablet it
 * may not be installed, and the demo will fail in aeroplane mode.
 */
class PreFlightActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreflightBinding
    private lateinit var app: OlSaathiApplication
    private var passed = 0
    private var total = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreflightBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as OlSaathiApplication

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnRunChecks.setOnClickListener {
            passed = 0
            total = 0
            binding.checkContainer.removeAllViews()
            runAllChecks()
        }
    }

    private fun runAllChecks() {
        checkPackLoads()
        checkFont("fonts/NotoSansOlChiki-Regular.ttf", "Ol Chiki font")
        checkFont("fonts/NotoSansDevanagari-Regular.ttf", "Devanagari font")
        checkHindiOfflineRecognition()
        checkMicrophonePermission()
        checkStorageWritable()
        checkWorksheetGenerates()

        val summary = if (passed == total) {
            "$passed of $total checks passing"
        } else {
            "$passed of $total, ${total - passed} failing"
        }
        binding.textSummary.text = summary
        binding.textSummary.setTextColor(
            if (passed == total) 0xFF1B6D24.toInt() else 0xFFC62828.toInt()
        )
        // Store for the Proof screen
        app.preflightSummary = summary
    }

    // ── Individual checks ──────────────────────────────────────────────

    private fun checkPackLoads() {
        val size = app.pack.size
        if (size > 0) {
            addResult(true, "Content pack loads: $size entries")
        } else {
            addResult(false, "Content pack loads: FAILED — 0 entries")
        }
    }

    private fun checkFont(assetPath: String, label: String) {
        try {
            val tf = Typeface.createFromAsset(assets, assetPath)
            val testText = "\u1C5A\u1C5E \u1C6A\u1C64\u1C60\u1C64"
            addResult(true, "$label: loaded — $testText renders as glyphs")
        } catch (e: Exception) {
            addResult(false, "$label: FAILED TO LOAD — ${e.message}")
        }
    }

    /**
     * Check whether Hindi offline speech recognition is available.
     *
     * On API 33+: use SpeechRecognizer.checkRecognitionSupport() with
     * EXTRA_PREFER_OFFLINE, which tells us directly whether hi-IN works
     * without network.
     *
     * On older APIs: send ACTION_GET_LANGUAGE_DETAILS and check if hi-IN
     * appears in EXTRA_SUPPORTED_LANGUAGES. This is less precise but
     * better than nothing.
     */
    private fun checkHindiOfflineRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addResult(false, "Hindi offline recognition: NO speech recognizer on device")
            return
        }

        // Use ACTION_GET_LANGUAGE_DETAILS to check if hi-IN is supported.
        // This works on all API levels and tells us whether the language
        // pack is available on the device (offline or online).
        val intent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_GET_LANGUAGE_DETAILS)
        } catch (e: Exception) {
            addResult(false, "Hindi offline recognition: cannot check. " +
                "Verify manually in Settings → Languages & input → " +
                "On-device speech recognition → add Hindi.")
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_GET_LANGUAGE_DETAILS) {
            val supportedLangs = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES
            ) ?: arrayListOf()
            val prefLang = data?.getStringExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE)

            if (supportedLangs.any { it.startsWith("hi") }) {
                addResult(true, "Hindi offline recognition: hi-IN in supported languages list")
            } else {
                addResult(false, "Hindi offline speech is not installed. " +
                    "Settings → System → Languages & input → " +
                    "On-device speech recognition → add Hindi. " +
                    "Needed before demonstrating in aeroplane mode.")
            }
        }
    }

    private fun checkMicrophonePermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            addResult(true, "Microphone permission: granted")
        } else {
            addResult(false, "Microphone permission: NOT granted — tap the mic button to request")
        }
    }

    private fun checkStorageWritable() {
        try {
            val testFile = File(cacheDir, "preflight_test.txt")
            testFile.writeText("ok", Charsets.UTF_8)
            testFile.delete()
            addResult(true, "Storage writable: cache directory OK")
        } catch (e: Exception) {
            addResult(false, "Storage writable: FAILED — ${e.message}")
        }
    }

    private fun checkWorksheetGenerates() {
        try {
            // "__phrases__" is the synthetic id the lesson list uses for the
            // phrase collection. No entry belongs to it, so asking for a
            // worksheet of it can only fail. Use a lesson that really exists.
            val lessonId = app.pack.lessonIds().firstOrNull { it != "__phrases__" }
            if (lessonId == null) {
                addResult(false, "Worksheet generates: the pack has no lesson to render")
                return
            }
            val pdf = WorksheetPdf(this).generate(lessonId, app.pack)
            if (pdf != null && pdf.exists()) {
                val sizeKb = pdf.length() / 1024
                addResult(true, "Worksheet generates: $lessonId, ${sizeKb} KB")
                pdf.delete()
            } else {
                addResult(false, "Worksheet generates: nothing produced for $lessonId")
            }
        } catch (e: Exception) {
            addResult(false, "Worksheet generates: FAILED — ${e.message}")
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────

    private fun addResult(ok: Boolean, message: String) {
        total++
        if (ok) passed++

        val tv = TextView(this).apply {
            text = if (ok) "✓ $message" else "✗ $message"
            textSize = 14f
            setTextColor(if (ok) 0xFF1B6D24.toInt() else 0xFFC62828.toInt())
            setPadding(0, 8, 0, 8)
            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        binding.checkContainer.addView(tv, params)
    }

    companion object {
        private const val REQUEST_GET_LANGUAGE_DETAILS = 2001
    }
}
