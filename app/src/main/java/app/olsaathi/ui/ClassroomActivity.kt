package app.olsaathi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
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
import app.olsaathi.content.AudioProvenance
import app.olsaathi.content.TranslationRouter
import app.olsaathi.databinding.ActivityClassroomBinding
import app.olsaathi.speech.HindiSpeechInput
import app.olsaathi.speech.BhashiniSpeechInput
import app.olsaathi.speech.TargetVoice
import app.olsaathi.worksheet.ScriptFonts
import java.util.Locale

/**
 * The Teach screen — the live screen where a teacher speaks (or types)
 * a Hindi sentence and sees/hears it in Santali.
 *
 * Santali is the largest element on screen (30sp).
 * Latency is on the Proof screen, not here.
 * Overflow menu: Check & Proof.
 */
class ClassroomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassroomBinding
    private lateinit var pack: VerifiedContentPack
    private lateinit var audioPlayer: PackAudioPlayer
    private var speechInput: HindiSpeechInput? = null
    private var currentTranslation: Translation? = null
    private var targetVoice: TargetVoice? = null

    /**
     * True when this device can speak the language currently selected.
     *
     * Recomputed on every language switch, because it genuinely differs: the
     * device has a Tamil voice and no Santali one, and a flag decided once at
     * startup would carry the wrong answer the moment the teacher changed
     * language.
     */
    private var haveTargetVoice = false
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
    private lateinit var router: TranslationRouter
    @Volatile private var translateSeq = 0

    /**
     * WAV bytes synthesised for the line currently on screen, when that line
     * came from the network rather than the pack.
     *
     * Cleared on every new utterance. A sentence the teacher has moved on from
     * must never be spoken to a class, which is the worst failure this app
     * has, so this is guarded by [translateSeq] exactly as the text is.
     */
    private var liveAudio: ByteArray? = null

    /**
     * Whether a line that arrived by voice should speak itself.
     *
     * On by default, because the deliverable is a voice-to-voice time and
     * making a teacher press play after every sentence measures their reaction
     * speed rather than the system. Off is a real need though: a tablet that
     * talks back in a room where another class is being taught is a problem,
     * and until now there was no way to stop it.
     *
     * Remembered across sessions. A teacher who turned it off did so for a
     * reason that will still be true tomorrow.
     */
    private var autoplayEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassroomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pack = (application as OlSaathiApplication).pack
        router = TranslationRouter(this, pack)
        audioPlayer = PackAudioPlayer(this)
        targetVoice = TargetVoice(this).also { voice ->
            voice.onReady = {
                runOnUiThread {
                    haveTargetVoice = voice.hasVoice(pack.languageCode)
                    updateAudioStatus()
                    refreshPlayButton()

                // Bring the answer into view.
                //
                // The target is the largest thing on this screen and it lives
                // inside a scroll view, so on a short screen, or once the
                // autoplay switch took a row below it, it sat below the fold:
                // the app would speak Tamil aloud while showing the teacher an
                // apparently empty panel. Scrolling to the label rather than
                // to the text keeps the "TAMIL" heading on screen above it, so
                // it is obvious what is being read.
                binding.scrollTranslation.post {
                    binding.scrollTranslation.smoothScrollTo(0, binding.textTargetLabel.top)
                }
                }
            }
        }

        // Toolbar
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Overflow menu â†’ Check & Proof
        autoplayEnabled = getSharedPreferences("olsaathi", MODE_PRIVATE)
            .getBoolean(PREF_AUTOPLAY, true)
        binding.toolbar.menu.findItem(R.id.action_autoplay)?.isChecked = autoplayEnabled
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_check_proof -> {
                    startActivity(Intent(this, CheckAndProofActivity::class.java))
                    true
                }
                R.id.action_autoplay -> {
                    // A setting, so it lives in the menu rather than taking a
                    // row on the screen. The row it used to take was the row
                    // the translation itself needed: the target panel is a
                    // scroll view squeezed by everything below it, and on a
                    // 1920-tall screen it had 257px, which showed the Hindi
                    // and hid the answer.
                    autoplayEnabled = !autoplayEnabled
                    item.isChecked = autoplayEnabled
                    getSharedPreferences("olsaathi", MODE_PRIVATE)
                        .edit().putBoolean(PREF_AUTOPLAY, autoplayEnabled).apply()
                    if (!autoplayEnabled) {
                        audioPlayer.stop()
                        targetVoice?.stop()
                    }
                    true
                }
                else -> false
            }
        }

        // The target script is whatever the loaded pack says it is. This was
        // hardcoded to Ol Chiki, which rendered every other language as empty
        // boxes the moment a language dropdown existed. The source is always
        // Devanagari, because the teacher always reads Hindi.
        applyTargetTypeface()
        binding.textSource.typeface = ScriptFonts.forSource(this)

        // The language bar. Switching reloads the pack, swaps the typeface and
        // re-runs the lookup, so the sentence on screen follows the teacher
        // into the new language instead of being stranded in the old one.
        LanguagePicker.bind(
            this,
            binding.languageBar.root,
            binding.languageBar.spinnerLanguage,
            binding.languageBar.textLanguageNote,
        ) {
            pack = (application as OlSaathiApplication).pack
            // The router holds the pack it was built with. Reassigning `pack`
            // alone left it translating into the first language forever: pick
            // Malayalam, get Santali. So it is rebuilt around the new pack.
            router.shutdown()
            router = TranslationRouter(this, pack)
            liveAudio = null
            // The device has a Tamil voice and no Santali one, so this genuinely
            // changes with the dropdown and cannot be decided once at startup.
            haveTargetVoice = targetVoice?.hasVoice(pack.languageCode) == true
            updateAudioStatus()
            applyTargetTypeface()
            val showing = binding.textSource.text?.toString() ?: ""
            if (showing.isNotBlank()) translateAndDisplay(showing)
        }

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

        // Play audio: a live-synthesised clip when there is one, else the
        // pack WAV. Consumed once — a replay of the same line is not a
        // voice-to-voice event and must not be recorded as one.
        binding.btnPlayAudio.setOnClickListener {
            val voiceStartMs = speechResultMs
            speechResultMs = 0
            playCurrent(voiceStartMs)
        }

        // Voice input: a real press-and-hold. Holding past HOLD_MS starts the
        // microphone and letting go stops it; a quick tap is not speech (in a
        // debug build it feeds a mock prompt through the same path instead).
        binding.btnVoiceInput.setOnTouchListener { v, ev -> onVoiceTouch(v, ev) }

        // Playback speed, for every source: pack clip, live clip, device voice.
        playbackSpeed = getSharedPreferences("olsaathi", MODE_PRIVATE)
            .getFloat(PREF_SPEED, 1f).let { saved -> if (SPEEDS.any { it == saved }) saved else 1f }
        showSpeed()
        binding.btnSpeed.setOnClickListener {
            val i = SPEEDS.indexOfFirst { it == playbackSpeed }
            playbackSpeed = SPEEDS[(i + 1) % SPEEDS.size]
            getSharedPreferences("olsaathi", MODE_PRIVATE)
                .edit().putFloat(PREF_SPEED, playbackSpeed).apply()
            showSpeed()
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

    /**
     * Speak the line on screen, from whichever source it has.
     *
     * [voiceStartMs] is the moment speech recognition returned, or 0 when the
     * line was typed. Only a line that arrived by voice is measured: playing
     * something the teacher typed measures nothing about voice to voice.
     *
     * The two paths report into two different series. They are the same span
     * by definition, speech result to first sound, but one of them crosses a
     * network and the other does not, and blending them would describe
     * neither.
     */
    /**
     * Decide what, if anything, can speak the line on screen, and say so.
     *
     * Three sources, in order of how much they have been vouched for:
     *
     *  1. a WAV in the pack, which passed the build-time checks
     *  2. a clip synthesised over the network for this exact sentence
     *  3. the device's own voice, which needs no key and no network
     *
     * The chip beside the button always names the one that would actually
     * play, because a teacher deciding whether to put a sound in front of
     * children needs to know which of the three they are about to hear.
     */
    private fun refreshPlayButton() {
        val t = currentTranslation
        if (t == null) {
            binding.btnPlayAudio.isEnabled = false
            binding.textAudioProvenance.visibility = View.GONE
            return
        }

        val audioPath = pack.audioPath(t)
        val packPlayable = t.hasAudio && audioPath != null && audioPlayer.hasAudio(audioPath)
        val livePlayable = liveAudio != null
        val devicePlayable = haveTargetVoice && t.target.isNotBlank()

        binding.btnPlayAudio.isEnabled = packPlayable || livePlayable || devicePlayable

        val source = when {
            packPlayable -> t.audioProvenance
            livePlayable -> AudioProvenance.LIVE_TTS
            devicePlayable -> AudioProvenance.DEVICE_TTS
            else -> AudioProvenance.NONE
        }
        if (source == AudioProvenance.NONE) {
            binding.textAudioProvenance.visibility = View.GONE
        } else {
            binding.textAudioProvenance.text =
                t.copy(audioProvenance = source).audioProvenanceLabel
            binding.textAudioProvenance.visibility = View.VISIBLE
        }
    }

    private fun playCurrent(voiceStartMs: Long) {
        val t = currentTranslation ?: return
        val app = application as OlSaathiApplication

        val live = liveAudio
        if (live != null) {
            audioPlayer.playBytes(live,
                speed = playbackSpeed,
                onComplete = { runOnUiThread { binding.btnPlayAudio.isEnabled = true } },
                onError = { err ->
                    runOnUiThread { Toast.makeText(this, err, Toast.LENGTH_SHORT).show() }
                },
                onReady = {
                    if (voiceStartMs > 0) {
                        app.recordLiveVoiceLatency(System.currentTimeMillis() - voiceStartMs)
                    }
                }
            )
            return
        }

        val path = pack.audioPath(t)
        if (t.hasAudio && path != null && audioPlayer.hasAudio(path)) {
            audioPlayer.play(path,
                speed = playbackSpeed,
                onComplete = { runOnUiThread { binding.btnPlayAudio.isEnabled = true } },
                onError = { err ->
                    runOnUiThread { Toast.makeText(this, err, Toast.LENGTH_SHORT).show() }
                },
                onReady = {
                    if (voiceStartMs > 0) {
                        app.recordVoiceLatency(System.currentTimeMillis() - voiceStartMs)
                    }
                }
            )
            return
        }

        // Nothing recorded and nothing synthesised, but the device may be able
        // to read it aloud. For the languages Android covers this is the whole
        // reason voice to voice works at all today, and it works offline.
        if (haveTargetVoice && t.target.isNotBlank()) {
            // The moment audio began is taken at onStart, the same instant
            // MediaPlayer's prepared listener marks for the other two paths, but
            // it is only recorded once the utterance has actually finished. An
            // utterance can start and then fail, and a sound that never reached
            // the speaker must not enter the voice-to-voice median.
            var startedAt = 0L
            targetVoice?.speak(
                text = t.target,
                languageCode = pack.languageCode,
                rate = playbackSpeed,
                onStart = { startedAt = System.currentTimeMillis() },
                onDone = {
                    if (voiceStartMs > 0 && startedAt > 0) {
                        app.recordVoiceLatency(startedAt - voiceStartMs)
                    }
                    runOnUiThread { binding.btnPlayAudio.isEnabled = true }
                },
                onError = {
                    // Say so. Silently re-enabling the button looks exactly like
                    // a line that played, to a teacher who did not hear it.
                    runOnUiThread {
                        binding.btnPlayAudio.isEnabled = true
                        Toast.makeText(
                            this,
                            "This device could not speak that line.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
            )
        }
    }

    /**
     * Ask for a spoken form of a line that came back from the network.
     *
     * Without this the live path could translate an unfamiliar sentence and
     * then had no way to say it: the answer appeared in a script the teacher
     * cannot read and stayed silent, so voice to voice worked for the 53 pack
     * phrases and nothing else.
     *
     * A line that arrived by voice speaks itself as soon as it is ready.
     * Requiring a button press after every sentence would measure the
     * teacher's reaction time rather than the system, and the deliverable is
     * about the system. A typed line never autoplays, because [speechResultMs]
     * is 0 for it.
     */
    private fun requestLiveVoice(translation: Translation, requestId: Int, voiceStartMs: Long) {
        router.synthesise(translation.target) { audio ->
            if (audio == null || audio.isEmpty()) {
                // No clip came back. The text is still real, and the device may
                // be able to read it, so offer that rather than leaving a live
                // translation mute.
                runOnUiThread {
                    if (requestId != translateSeq) return@runOnUiThread
                    refreshPlayButton()
                    if (autoplayEnabled && voiceStartMs > 0 && binding.btnPlayAudio.isEnabled) {
                        speechResultMs = 0
                        playCurrent(voiceStartMs)
                    }
                }
                return@synthesise
            }
            runOnUiThread {
                // The teacher has moved on. Discard it rather than speaking a
                // sentence that is no longer on screen.
                if (requestId != translateSeq) return@runOnUiThread

                liveAudio = audio
                // Name the voice, not the translator. A pack line's
                // serviceName is the model that translated it, and the label
                // once read "Machine voice, live · AI4Bharat IndicTrans2" for
                // speech that came from Bhashini.
                val spoken = translation.copy(
                    audioProvenance = AudioProvenance.LIVE_TTS,
                    audioServiceName = "Bhashini",
                )
                currentTranslation = spoken
                binding.textAudioProvenance.text = spoken.audioProvenanceLabel
                binding.textAudioProvenance.visibility = View.VISIBLE
                binding.btnPlayAudio.isEnabled = true
                updateAudioStatus()

                if (autoplayEnabled && voiceStartMs > 0) {
                    speechResultMs = 0
                    playCurrent(voiceStartMs)
                }
            }
        }
    }

    private fun updateAudioStatus() {
        if (haveTargetVoice || liveAudio != null) {
            binding.textAudioStatus.visibility = View.GONE
        } else {
            val name = (application as OlSaathiApplication).currentLanguageOption()?.english
                ?: pack.languageEnglish.ifEmpty { "—" }
            // "Audio comes from the pack" is only true when the pack has audio
            // for this line. For Santali it has none, and saying otherwise sent
            // a teacher looking for a recording that does not exist.
            // Before any line is on screen there is no line to ask, so ask the
            // pack: with nothing translated yet this once claimed Santali had
            // no recordings while all 53 shipped.
            val packHasIt = currentTranslation?.hasAudio
                ?: pack.entries().any { !it.audio.isNullOrBlank() }
            binding.textAudioStatus.text = getString(
                if (packHasIt) R.string.no_target_voice_format else R.string.no_target_voice_no_pack_format,
                name
            )
            binding.textAudioStatus.visibility = View.VISIBLE
        }
    }

    /**
     * @param spokenAtMs when speech recognition produced this Hindi, or 0 when
     *   it came from the keyboard or from opening a lesson. Passed rather than
     *   assigned by each caller, so no path can leave a stale timestamp behind
     *   for the next playback to measure against.
     */
    /**
     * Point the big target TextView at the current pack's script, and rename
     * every label that used to say "Santali" out loud.
     *
     * The dropdown is worthless if the screen around it still claims one
     * language: a teacher on Marathi seeing "Play Santali" has been told
     * something false by the app itself.
     */
    private fun applyTargetTypeface() {
        binding.textTarget.typeface = ScriptFonts.forTarget(this, pack.font)

        val option = (application as OlSaathiApplication).currentLanguageOption()
        val name = option?.english ?: pack.languageEnglish.ifEmpty { "—" }
        binding.textTargetLabel.text =
            if (option != null && option.scriptNote.isNotEmpty())
                getString(R.string.target_label_format, name, option.scriptNote)
            else name
        binding.btnPlayAudio.text = getString(R.string.btn_play_format, name)
    }

    private fun translateAndDisplay(hindi: String, spokenAtMs: Long = 0) {
        val requestId = ++translateSeq
        val startMs = System.currentTimeMillis()
        
        router.translate(hindi) { translation ->
            // Cheap short-circuit on background thread
            if (requestId != translateSeq) return@translate
            
            // Measure offline lookup latency on first (offline) callback only
            if (translation.provenance != Provenance.ONLINE_MACHINE) {
                val elapsed = System.currentTimeMillis() - startMs
                // Two different numbers, kept apart. This one is the offline lookup and
                // lands near zero. The voice-to-voice span, which is the one the
                // 3-second ceiling is about, is measured in the play handler above.
                (application as OlSaathiApplication).recordLatency(elapsed)
            }

            runOnUiThread {
                // Authoritative race guard: must be inside runOnUiThread
                if (requestId != translateSeq) return@runOnUiThread
                
                // Assign shared state only after guard passes, on main thread
                currentTranslation = translation
                speechResultMs = spokenAtMs

                // The offline result always lands first, so this is the new
                // utterance arriving: anything synthesised for the previous
                // one is now wrong and must not be reachable by the play
                // button.
                if (translation.provenance != Provenance.ONLINE_MACHINE) {
                    liveAudio = null
                }

                binding.textSource.text = translation.source.ifEmpty { hindi }
                binding.textTarget.text = translation.target.ifEmpty { "—" }
                binding.textProvenance.text = translation.provenanceLabel

                // Both halves have to agree before the button lights up: the wav
                // has to be in the APK and the pack has to record where the voice
                // came from. A wav with no recorded provenance stays unplayable,
                // because nothing on screen could then tell the teacher what they
                // are about to play to a class.
                refreshPlayButton()

                // A line that arrived by voice and can be spoken right now
                // says itself, without waiting for a button. Only the device
                // voice and the pack can do that at this point; a live clip
                // has not been asked for yet and autoplays from its own
                // callback when it lands.
                if (autoplayEnabled && spokenAtMs > 0 && binding.btnPlayAudio.isEnabled) {
                    speechResultMs = 0
                    playCurrent(spokenAtMs)
                }

                // Only offer to show the class when there is Santali to show. On a
                // miss the button disappears rather than opening an empty screen.
                binding.btnShowClass.visibility =
                    if (translation.target.isNotBlank()) View.VISIBLE else View.GONE

                binding.textProvenance.showProvenanceBadge(
                    translation.provenanceLabel,
                    provenanceColour(this, translation.provenance)
                )

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

                // Get a live voice for a line from the network, and for a pack
                // line that nothing on the device can speak: no pack audio and
                // no device voice left the play button disabled. That second
                // case is every Santali line. A line the pack or the device
                // can already speak is left alone, so nothing plays twice.
                // The router itself refuses without a key or a network.
                if (translation.target.isNotBlank() &&
                    (translation.provenance == Provenance.ONLINE_MACHINE ||
                        !binding.btnPlayAudio.isEnabled)) {
                    requestLiveVoice(translation, requestId, spokenAtMs)
                }
            }
        }
    }

    private var playbackSpeed = 1f
    private var holdStarter: Runnable? = null
    private var holdActive = false
    private var bhashiniInput: BhashiniSpeechInput? = null

    private fun showSpeed() {
        val label = if (playbackSpeed == playbackSpeed.toInt().toFloat())
            playbackSpeed.toInt().toString() else playbackSpeed.toString()
        binding.btnSpeed.text = getString(R.string.btn_speed_format, label)
    }

    private fun onVoiceTouch(v: View, ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                holdActive = false
                val r = Runnable { holdActive = true; beginHold() }
                holdStarter = r
                v.postDelayed(r, HOLD_MS)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                holdStarter?.let { v.removeCallbacks(it) }
                holdStarter = null
                if (holdActive) {
                    bhashiniInput?.stop()
                } else if (ev.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    v.performClick()
                    onVoiceTap()
                }
                holdActive = false
            }
        }
        return true
    }

    private fun onVoiceTap() {
        if (BuildConfig.DEBUG) {
            val prompt = MOCK_PROMPTS.random()
            binding.textSource.text = prompt
            // Stands in for a speech result, so the debug button exercises
            // the same measurement path the real microphone does.
            translateAndDisplay(prompt, spokenAtMs = System.currentTimeMillis())
        } else {
            Toast.makeText(this, R.string.hold_to_speak_hint, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Start listening. Online, record and send to Bhashini, which works on
     * any phone; offline, fall back to Android's recogniser, which works only
     * where the Hindi offline pack is installed.
     */
    private fun beginHold() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            holdActive = false
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        if (router.mode() == TranslationRouter.Mode.ONLINE_AVAILABLE) {
            val input = bhashiniInput ?: BhashiniSpeechInput(
                onResult = { text ->
                    val spokenAt = System.currentTimeMillis()
                    runOnUiThread {
                        binding.textSource.text = text
                        translateAndDisplay(text, spokenAtMs = spokenAt)
                    }
                },
                onError = { err ->
                    runOnUiThread { Toast.makeText(this, err, Toast.LENGTH_SHORT).show() }
                },
                onStateChanged = { state ->
                    runOnUiThread {
                        binding.btnVoiceInput.text = when (state) {
                            BhashiniSpeechInput.State.RECORDING -> getString(R.string.listening)
                            BhashiniSpeechInput.State.TRANSCRIBING -> getString(R.string.recognising)
                            BhashiniSpeechInput.State.IDLE -> getString(R.string.hold_to_speak)
                        }
                    }
                },
            ).also { bhashiniInput = it }
            input.start()
        } else {
            holdActive = false
            startVoiceInput()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.mic_allowed_hold, Toast.LENGTH_SHORT).show()
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
        router.shutdown()
        liveAudio = null
        bhashiniInput?.stop()
        speechInput?.destroy()
        audioPlayer.release()
        targetVoice?.shutdown()
        targetVoice = null
    }

    private fun monthName(m: Int): String = when (m) {
        1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"
        5 -> "May"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
        9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
        else -> ""
    }

    companion object {
        private const val PREF_AUTOPLAY = "autoplay_enabled"
        /** Shared with ShowClassActivity, so the read-out uses the same speed. */
        const val PREF_SPEED = "playback_speed"
        val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f)
        private const val HOLD_MS = 250L

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
