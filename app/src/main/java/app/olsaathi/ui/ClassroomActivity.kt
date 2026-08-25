package app.olsaathi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.speech.tts.TextToSpeech
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
 * The demo screen. A teacher speaks a Hindi sentence (or taps a phrase)
 * and sees/hears it in Santali within measured milliseconds.
 *
 * Phase 2: lookup and display
 * Phase 3: audio playback (pack WAVs only — no synthetic Santali TTS)
 * Phase 4: voice input
 * Phase 4b: Hindi TTS for source playback (labelled clearly)
 */
class ClassroomActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityClassroomBinding
    private lateinit var pack: VerifiedContentPack
    private lateinit var audioPlayer: PackAudioPlayer
    private var speechInput: HindiSpeechInput? = null
    private var currentTranslation: Translation? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    /** True when the device has a Santali voice engine. */
    private var haveSantaliVoice = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassroomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack
        audioPlayer = PackAudioPlayer(this)

        // Initialise TTS — used only for Hindi source playback, never Santali
        tts = TextToSpeech(this, this)

        val lessonId = intent.getStringExtra(LessonListActivity.EXTRA_LESSON_ID)

        // Toolbar
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Load font onto target text
        binding.textTarget.typeface = android.graphics.Typeface.createFromAsset(
            assets, "fonts/NotoSansOlChiki-Regular.ttf"
        )
        binding.textSource.typeface = android.graphics.Typeface.createFromAsset(
            assets, "fonts/NotoSansDevanagari-Regular.ttf"
        )

        // Manual text input
        binding.btnManualTranslate.setOnClickListener {
            val text = binding.editHindiInput.text?.toString() ?: ""
            if (text.isNotBlank()) {
                translateAndDisplay(text)
            }
        }

        binding.editHindiInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = binding.editHindiInput.text?.toString() ?: ""
                if (text.isNotBlank()) {
                    translateAndDisplay(text)
                }
                true
            } else false
        }

        // Play audio button (pack WAV only — pre-rendered by Bhashini)
        binding.btnPlayAudio.setOnClickListener {
            val t = currentTranslation ?: return@setOnClickListener
            val path = pack.audioPath(t)
            if (path != null && audioPlayer.hasAudio(path)) {
                audioPlayer.play(path,
                    onComplete = { runOnUiThread { binding.btnPlayAudio.isEnabled = true } },
                    onError = { err ->
                        runOnUiThread {
                            Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // Voice input button — long-press for real mic, single-tap for mock (debug only)
        binding.btnVoiceInput.setOnLongClickListener {
            ensureAudioPermission()
            true
        }
        if (BuildConfig.DEBUG) {
            binding.btnVoiceInput.setOnClickListener {
                // Debug-only: inject a mock Hindi prompt for emulator testing.
                // This path does not exist in release builds.
                val prompt = MOCK_PROMPTS.random()
                binding.textSource.text = prompt
                translateAndDisplay(prompt)
            }
        } else {
            // Release: single tap does nothing — long-press only
            binding.btnVoiceInput.setOnClickListener { /* no-op in release */ }
        }

        // Worksheet button
        binding.btnWorksheet.setOnClickListener {
            val intent = Intent(this, WorksheetActivity::class.java)
            startActivity(intent)
        }

        // Phrase list — lesson lines, then assessment questions
        val lessonEntries = pack.entries(lessonId)
        val checkEntries = pack.entries(lessonId).filter { it.kind == "check" }
        val allEntries = lessonEntries + checkEntries
        binding.recyclerPhrases.layoutManager = LinearLayoutManager(this)
        binding.recyclerPhrases.adapter = PhraseAdapter(allEntries) { entry ->
            translateAndDisplay(entry.source)
        }

        // If we have a lesson, show its first entry
        if (lessonId != null && allEntries.isNotEmpty()) {
            translateAndDisplay(allEntries.first().source)
        }
    }

    /**
     * TTS OnInit callback.
     *
     * We check two things:
     *   1. Is the engine ready at all? (ttsReady)
     *   2. Does this device have a Santali voice? (haveSantaliVoice)
     *
     * We never set tts.language to anything for Santali playback.
     * If there is no Santali voice, we never call speak() with
     * translation.target. Period.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            // Check if Santali is available on this device
            val satStatus = tts?.isLanguageAvailable(Locale("sat"))
                ?: TextToSpeech.LANG_NOT_SUPPORTED
            haveSantaliVoice = satStatus >= TextToSpeech.LANG_AVAILABLE
            // Set Hindi for source playback only
            tts?.language = Locale("hi", "IN")
        } else {
            ttsReady = false
            haveSantaliVoice = false
        }
        // Show audio truth on screen
        runOnUiThread { updateAudioStatus() }
    }

    /**
     * Show the real audio situation. No pretence.
     */
    private fun updateAudioStatus() {
        if (haveSantaliVoice) {
            binding.textAudioStatus.visibility = View.GONE
        } else {
            binding.textAudioStatus.text = getString(R.string.no_santali_voice)
            binding.textAudioStatus.visibility = View.VISIBLE
        }
    }

    private fun translateAndDisplay(hindi: String) {
        val startMs = System.currentTimeMillis()
        val translation = pack.lookup(hindi)
        val elapsed = System.currentTimeMillis() - startMs

        currentTranslation = translation

        // Record latency for the Proof screen
        (application as OlSaathiApplication).recordLatency(elapsed)

        runOnUiThread {
            binding.textSource.text = translation.source.ifEmpty { hindi }
            binding.textTarget.text = translation.target.ifEmpty { "—" }
            binding.textProvenance.text = translation.provenance.label
            binding.textLatency.text = getString(R.string.latency_format, elapsed)

            // Audio button: only enabled when asset WAV exists
            val audioPath = pack.audioPath(translation)
            binding.btnPlayAudio.isEnabled = audioPath != null && audioPlayer.hasAudio(audioPath)

            // Colour the provenance label
            val colour = when (translation.provenance) {
                Provenance.VERIFIED -> ContextCompat.getColor(this, R.color.success_green)
                Provenance.TRANSLITERATED -> ContextCompat.getColor(this, R.color.warning_orange)
                Provenance.UNAVAILABLE -> ContextCompat.getColor(this, R.color.md_theme_outline)
                Provenance.SAMPLE -> ContextCompat.getColor(this, R.color.sample_red)
            }
            binding.textProvenance.setTextColor(colour)

            // A coloured label is easy to overlook while presenting. If the
            // pack is placeholder content, say so where nobody can miss it.
            if (pack.isSample) {
                binding.textProvenance.text = getString(R.string.sample_pack_warning)
            }

            // N1: NEVER speak translation.target through a Hindi engine.
            // If a Santali voice existed, we would use it — but it does not.
            // Audio comes from pre-rendered pack WAVs only.

            updateAudioStatus()
        }
    }

    private fun ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO
            )
        } else {
            startVoiceInput()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
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
                    runOnUiThread {
                        binding.textSource.text = text
                        translateAndDisplay(text)
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
                        binding.btnVoiceInput.text = if (listening) {
                            getString(R.string.listening)
                        } else {
                            getString(R.string.hold_to_speak)
                        }
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

    companion object {
        private const val REQUEST_AUDIO = 1001

        /** Debug-only mock prompts — not reachable in release builds. */
        private val MOCK_PROMPTS = listOf(
            "नमस्ते बच्चों, आज हम गिनती सीखेंगे",
            "सब बैठ जाओ।",
            "किताब खोलो।",
            "बहुत अच्छा!",
            "हाथ उठाओ।",
        )
    }

    // ── Phrase list adapter ──────────────────────────────────────────────

    class PhraseAdapter(
        private val entries: List<VerifiedContentPack.PackEntry>,
        private val onClick: (VerifiedContentPack.PackEntry) -> Unit,
    ) : RecyclerView.Adapter<PhraseAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val textHindi: TextView = view.findViewById(android.R.id.text1)
            val textEn: TextView = view.findViewById(android.R.id.text2)
            init {
                view.setOnClickListener {
                    val pos = adapterPosition
                    if (pos != RecyclerView.NO_POSITION) onClick(entries[pos])
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = entries[position]
            holder.textHindi.text = entry.source
            holder.textEn.text = entry.en
            holder.textHindi.typeface = android.graphics.Typeface.createFromAsset(
                holder.itemView.context.assets, "fonts/NotoSansDevanagari-Regular.ttf"
            )
        }

        override fun getItemCount() = entries.size
    }
}
