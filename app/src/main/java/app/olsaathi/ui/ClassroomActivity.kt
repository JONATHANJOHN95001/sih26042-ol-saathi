package app.olsaathi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.olsaathi.BuildConfig
import app.olsaathi.OlSaathiApplication
import app.olsaathi.R
import app.olsaathi.audio.PackAudioPlayer
import app.olsaathi.content.Provenance
import app.olsaathi.content.Translation
import app.olsaathi.content.VerifiedContentPack
import app.olsaathi.databinding.ActivityClassroomBinding
import app.olsaathi.speech.HindiSpeechInput
import java.util.Locale

/**
 * The Teach screen — the live screen where a teacher speaks (or types)
 * a Hindi sentence and sees/hears it in Santali.
 *
 * Santali is the largest element on screen (30sp).
 * Latency is on the Proof screen, not here.
 * Overflow menu: Check & Proof.
 */
class ClassroomActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityClassroomBinding
    private lateinit var pack: VerifiedContentPack
    private lateinit var audioPlayer: PackAudioPlayer
    private var speechInput: HindiSpeechInput? = null
    private var currentTranslation: Translation? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var haveSantaliVoice = false
    /**
     * When speech recognition returned the Hindi now on screen, or 0 when it
     * did not come from speech.
     *
     * Set only by [translateAndDisplay] from its parameter, and cleared the
     * moment it is used, so it cannot outlive the utterance it belongs to. It
     * was previously assigned at each call site and never cleared, which meant
     * a teacher who spoke once and pressed play again ten minutes later
     * recorded ten minutes as a voice-to-voice latency.
     */
    private var speechResultMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassroomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack
        audioPlayer = PackAudioPlayer(this)
        tts = TextToSpeech(this, this)

        // Toolbar
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Overflow menu → Check & Proof
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_check_proof -> {
                    startActivity(Intent(this, CheckAndProofActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Load fonts — Ol Chiki is the largest element, load it onto the target
        binding.textTarget.typeface = android.graphics.Typeface.createFromAsset(
            assets, "fonts/NotoSansOlChiki-Regular.ttf"
        )
        binding.textSource.typeface = android.graphics.Typeface.createFromAsset(
            assets, "fonts/NotoSansDevanagari-Regular.ttf"
        )

        // Manual text input
        binding.btnManualTranslate.setOnClickListener {
            val text = binding.editHindiInput.text?.toString() ?: ""
            if (text.isNotBlank()) translateAndDisplay(text)
        }
        binding.editHindiInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = binding.editHindiInput.text?.toString() ?: ""
                if (text.isNotBlank()) translateAndDisplay(text)
                true
            } else false
        }

        // Turn the tablet around. Same screen the lesson player opens, given
        // only what a child needs: picture, Santali, Hindi. No provenance.
        binding.btnShowClass.setOnClickListener {
            val t = currentTranslation ?: return@setOnClickListener
            if (t.target.isBlank()) return@setOnClickListener
            startActivity(
                ShowClassActivity.intent(
                    context = this,
                    source = t.source,
                    target = t.target,
                    image = pack.entries().firstOrNull { it.id == t.entryId }?.image,
                    // Only audio the pack labels. That screen shows no
                    // provenance by design, so anything it plays has to have
                    // been vouched for before it gets there.
                    audio = if (t.hasAudio) pack.audioPath(t) else null,
                )
            )
        }

        // Play audio (pack WAV only)
        binding.btnPlayAudio.setOnClickListener {
            val t = currentTranslation ?: return@setOnClickListener
            val path = pack.audioPath(t)
            if (t.hasAudio && path != null && audioPlayer.hasAudio(path)) {
                // Consumed once. A replay of the same line is not a
                // voice-to-voice event and must not be recorded as one.
                val voiceStartMs = speechResultMs
                speechResultMs = 0
                audioPlayer.play(path,
                    onComplete = { runOnUiThread { binding.btnPlayAudio.isEnabled = true } },
                    onError = { err ->
                        runOnUiThread { Toast.makeText(this, err, Toast.LENGTH_SHORT).show() }
                    },
                    onReady = {
                        // First moment sound can leave the tablet. Only a line
                        // that arrived by voice counts: playing something the
                        // teacher typed measures nothing about voice to voice.
                        if (voiceStartMs > 0) {
                            val elapsed = System.currentTimeMillis() - voiceStartMs
                            (application as OlSaathiApplication).recordVoiceLatency(elapsed)
                        }
                    }
                )
            }
        }

        // Voice input — long-press for real mic, single-tap for mock (debug only)
        binding.btnVoiceInput.setOnLongClickListener {
            ensureAudioPermission()
            true
        }
        if (BuildConfig.DEBUG) {
            binding.btnVoiceInput.setOnClickListener {
                val prompt = MOCK_PROMPTS.random()
                binding.textSource.text = prompt
                // Stands in for a speech result, so the debug button exercises
                // the same measurement path the real microphone does.
                translateAndDisplay(prompt, spokenAtMs = System.currentTimeMillis())
            }
        } else {
            binding.btnVoiceInput.setOnClickListener { /* no-op in release */ }
        }

        // ── Bottom nav ────────────────────────────────────────────
        binding.bottomNav.selectedItemId = R.id.nav_teach
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_teach -> true // already here
                R.id.nav_lessons -> {
                    startActivity(Intent(this, LessonListActivity::class.java))
                    finish(); true
                }
                R.id.nav_worksheet -> {
                    startActivity(Intent(this, WorksheetActivity::class.java))
                    finish(); true
                }
                else -> false
            }
        }

        // If we have a lesson ID, load its first entry
        val lessonId = intent.getStringExtra(LessonListActivity.EXTRA_LESSON_ID)
        if (lessonId != null) {
            val first = pack.entries(lessonId).firstOrNull()
            if (first != null) translateAndDisplay(first.source)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            val satStatus = tts?.isLanguageAvailable(Locale("sat"))
                ?: TextToSpeech.LANG_NOT_SUPPORTED
            haveSantaliVoice = satStatus >= TextToSpeech.LANG_AVAILABLE
            tts?.language = Locale("hi", "IN")
        } else {
            ttsReady = false
            haveSantaliVoice = false
        }
        runOnUiThread { updateAudioStatus() }
    }

    private fun updateAudioStatus() {
        if (haveSantaliVoice) {
            binding.textAudioStatus.visibility = View.GONE
        } else {
            binding.textAudioStatus.text = getString(R.string.no_santali_voice)
            binding.textAudioStatus.visibility = View.VISIBLE
        }
    }

    /**
     * @param spokenAtMs when speech recognition produced this Hindi, or 0 when
     *   it came from the keyboard or from opening a lesson. Passed rather than
     *   assigned by each caller, so no path can leave a stale timestamp behind
     *   for the next playback to measure against.
     */
    private fun translateAndDisplay(hindi: String, spokenAtMs: Long = 0) {
        val startMs = System.currentTimeMillis()
        val translation = pack.lookup(hindi)
        val elapsed = System.currentTimeMillis() - startMs

        currentTranslation = translation
        speechResultMs = spokenAtMs
        // Two different numbers, kept apart. This one is the offline lookup and
        // lands near zero. The voice-to-voice span, which is the one the
        // 3-second ceiling is about, is measured in the play handler above.
        (application as OlSaathiApplication).recordLatency(elapsed)

        runOnUiThread {
            binding.textSource.text = translation.source.ifEmpty { hindi }
            binding.textTarget.text = translation.target.ifEmpty { "—" }
            binding.textProvenance.text = translation.provenanceLabel

            // Both halves have to agree before the button lights up: the wav
            // has to be in the APK and the pack has to record where the voice
            // came from. A wav with no recorded provenance stays unplayable,
            // because nothing on screen could then tell the teacher what they
            // are about to play to a class.
            val audioPath = pack.audioPath(translation)
            binding.btnPlayAudio.isEnabled =
                translation.hasAudio && audioPath != null && audioPlayer.hasAudio(audioPath)

            if (translation.hasAudio) {
                binding.textAudioProvenance.text = translation.audioProvenance.label
                binding.textAudioProvenance.visibility = View.VISIBLE
            } else {
                binding.textAudioProvenance.visibility = View.GONE
            }

            // Only offer to show the class when there is Santali to show. On a
            // miss the button disappears rather than opening an empty screen.
            binding.btnShowClass.visibility =
                if (translation.target.isNotBlank()) View.VISIBLE else View.GONE

            val colour = when (translation.provenance) {
                Provenance.HUMAN_VERIFIED -> ContextCompat.getColor(this, R.color.human_verified_blue)
                Provenance.VERIFIED -> ContextCompat.getColor(this, R.color.success_green)
                Provenance.TRANSLITERATED -> ContextCompat.getColor(this, R.color.warning_orange)
                Provenance.UNAVAILABLE -> ContextCompat.getColor(this, R.color.md_theme_outline)
                Provenance.SAMPLE -> ContextCompat.getColor(this, R.color.sample_red)
            }
            binding.textProvenance.setTextColor(colour)

            // Show reviewer name for HUMAN_VERIFIED
            if (translation.provenance == Provenance.HUMAN_VERIFIED &&
                translation.reviewerName.isNotEmpty()) {
                val date = try {
                    val parts = translation.reviewedOn.split("-")
                    if (parts.size == 3) "${parts[2].toInt()} ${monthName(parts[1].toInt())} ${parts[0]}"
                    else translation.reviewedOn
                } catch (e: Exception) { translation.reviewedOn }
                binding.textProvenance.text = "${translation.provenanceLabel}\nChecked by ${translation.reviewerName}, $date"
            } else if (pack.isSample) {
                binding.textProvenance.text = getString(R.string.sample_pack_warning)
            }

            updateAudioStatus()
        }
    }

    private fun ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            startVoiceInput()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput()
        } else {
            Toast.makeText(this, R.string.error_speech_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceInput() {
        if (speechInput == null) {
            speechInput = HindiSpeechInput(
                context = this,
                onResult = { text ->
                    // The clock starts here, the moment recognition returns
                    // Hindi, and stops when MediaPlayer is prepared.
                    val spokenAt = System.currentTimeMillis()
                    runOnUiThread {
                        binding.textSource.text = text
                        translateAndDisplay(text, spokenAtMs = spokenAt)
                    }
                },
                onError = { err ->
                    runOnUiThread {
                        Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                        binding.btnVoiceInput.text = getString(R.string.hold_to_speak)
                    }
                },
                onListeningChanged = { listening ->
                    runOnUiThread {
                        binding.btnVoiceInput.text = if (listening) getString(R.string.listening)
                        else getString(R.string.hold_to_speak)
                    }
                }
            )
        }
        if (speechInput?.isAvailable == true) {
            speechInput?.startListening()
        } else {
            Toast.makeText(this, R.string.error_speech_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechInput?.destroy()
        audioPlayer.release()
        tts?.stop()
        tts?.shutdown()
    }

    private fun monthName(m: Int): String = when (m) {
        1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
        5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
        9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
        else -> ""
    }

    companion object {
        private const val REQUEST_AUDIO = 1001
        private val MOCK_PROMPTS = listOf(
            "नमस्ते बच्चों, आज हम गिनती सीखेंगे",
            "सब बैठ जाओ।",
            "किताब खोलो।",
            "बहुत अच्छा!",
            "हाथ उठाओ।",
        )
    }
}
