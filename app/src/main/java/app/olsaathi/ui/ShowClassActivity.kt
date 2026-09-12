package app.olsaathi.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.olsaathi.OlSaathiApplication
import app.olsaathi.R
import app.olsaathi.audio.PackAudioPlayer
import app.olsaathi.databinding.ActivityShowClassBinding
import app.olsaathi.net.BhashiniClient
import app.olsaathi.speech.TargetVoice
import app.olsaathi.util.NetworkGuard
import app.olsaathi.worksheet.ScriptFonts

/**
 * The child-facing screen. The teacher turns the tablet around.
 *
 * Every other screen in this app is for the teacher: it has controls, labels,
 * provenance chips and navigation. None of that means anything to a six-year-old
 * who cannot read yet, and all of it competes with the one thing that does.
 *
 * So this screen strips everything. A picture, the Santali as large as the
 * screen allows, the Hindi underneath, and two small controls pushed into
 * corners. It should read like a page from a picture book rather than an app.
 *
 * Three decisions worth keeping:
 *
 *  - **The screen stays awake.** A teacher holds this up while talking to a
 *    class. Having it dim mid-sentence is the kind of small failure that ends
 *    a demo badly.
 *  - **Both orientations.** Every other activity is portrait-locked. A teacher
 *    turning a tablet to face a room usually turns it landscape, so locking it
 *    would fight the one gesture this screen exists for.
 *  - **Provenance is deliberately absent.** It is on every teacher-facing
 *    screen and on every printed card, because the adult must be able to judge
 *    the translation. The child cannot, and showing them "Machine translation"
 *    tells them nothing while taking space from what they can read.
 */
class ShowClassActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShowClassBinding
    private var audioPlayer: PackAudioPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShowClassBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Held up in front of a class. Do not let it sleep.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val source = intent.getStringExtra(EXTRA_SOURCE).orEmpty()
        val target = intent.getStringExtra(EXTRA_TARGET).orEmpty()
        val image = intent.getStringExtra(EXTRA_IMAGE)
        val audio = intent.getStringExtra(EXTRA_AUDIO)

        // N1 still applies here. If there is no Santali for this line, this
        // screen has nothing to show a child and must not be opened at all.
        if (target.isBlank()) {
            Toast.makeText(this, "Nothing to show for this line.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.textTarget.text = target
        // The current pack's script, not always Ol Chiki: this screen opened
        // Malayalam and Tamil lines in the Ol Chiki face too.
        val pack = (application as OlSaathiApplication).pack
        binding.textTarget.typeface =
            if (pack.font.isNotEmpty()) ScriptFonts.forTarget(this, pack.font) else olChikiTypeface()
        binding.textSource.text = source
        val gloss = intent.getStringExtra(EXTRA_GLOSS).orEmpty()
        binding.textGloss.text = "“$gloss”"
        binding.textGloss.visibility = if (gloss.isBlank()) View.GONE else View.VISIBLE

        showPicture(image)
        wirePlayback(audio, target, pack.languageCode)

        binding.btnClose.setOnClickListener { finish() }
    }

    /**
     * Ol Chiki is bundled because Android ships no font for it. Falling back to
     * the system face would render every character as an empty box, so a failure
     * here is worth surfacing rather than silently showing tofu to a classroom.
     */
    private fun olChikiTypeface(): Typeface? = try {
        Typeface.createFromAsset(assets, "fonts/NotoSansOlChiki-Regular.ttf")
    } catch (e: Exception) {
        Toast.makeText(this, "Ol Chiki font failed to load.", Toast.LENGTH_LONG).show()
        null
    }

    private fun showPicture(name: String?) {
        if (name.isNullOrEmpty()) return
        @Suppress("DEPRECATION")
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) return
        binding.imageCard.setImageResource(id)
        binding.imageCard.visibility = View.VISIBLE
    }

    /**
     * "Read aloud", always offered. The voice is chosen in the same order the
     * Teach screen uses: the pack's recorded clip, then a live Bhashini voice
     * when online, then the device's own offline voice. If none is available
     * the button says so rather than staying silent.
     */
    private fun wirePlayback(assetPath: String?, target: String, languageCode: String) {
        val player = PackAudioPlayer(this)
        audioPlayer = player
        val packClip = assetPath?.takeIf { it.isNotEmpty() && player.hasAudio(it) }
        val prefs = getSharedPreferences("olsaathi", MODE_PRIVATE)
        var speed = prefs.getFloat(ClassroomActivity.PREF_SPEED, 1f)
        // The speed chip cycles the same steps as the Teach screen and saves
        // to the same preference, so a slower pace set here carries back.
        fun showSpeed() {
            val label = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
            binding.btnSpeed.text = getString(R.string.btn_speed_format, label)
        }
        showSpeed()
        binding.btnSpeed.setOnClickListener {
            val steps = ClassroomActivity.SPEEDS
            speed = steps[(steps.indexOfFirst { it == speed }.coerceAtLeast(0) + 1) % steps.size]
            prefs.edit().putFloat(ClassroomActivity.PREF_SPEED, speed).apply()
            showSpeed()
        }
        voice = TargetVoice(this)

        binding.btnPlay.text = "🔊  Read aloud"
        binding.btnPlay.visibility = View.VISIBLE
        binding.btnPlay.setOnClickListener {
            binding.btnPlay.isEnabled = false
            val done = { runOnUiThread { binding.btnPlay.isEnabled = true } }
            val fail = { msg: String ->
                runOnUiThread {
                    binding.btnPlay.isEnabled = true
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            if (packClip != null) {
                player.play(packClip, onComplete = done, onError = fail, speed = speed)
                return@setOnClickListener
            }
            Thread {
                val live = if (NetworkGuard.isOnline(this)) BhashiniClient().synthesise(target, languageCode) else null
                runOnUiThread {
                    if (live != null) {
                        player.playBytes(live, onComplete = done, onError = fail, speed = speed)
                    } else if (voice?.speak(target, languageCode, onDone = done, rate = speed) != true) {
                        fail(getString(R.string.no_voice_for_line))
                    }
                }
            }.start()
        }
    }

    private var voice: TargetVoice? = null

    /**
     * Hide the status bar only.
     *
     * Hiding the navigation bar as well would be tidier, but it makes Android
     * throw its own "Viewing full screen, swipe down to exit" dialog the first
     * time, directly over the content. That is fine in an app and unacceptable
     * on the one screen a room full of people is looking at. Losing the status
     * bar gets almost all of the benefit and triggers no system dialog on any
     * version.
     */
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    override fun onDestroy() {
        audioPlayer?.release()
        voice?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_IMAGE = "image"
        private const val EXTRA_AUDIO = "audio"
        private const val EXTRA_GLOSS = "gloss"

        /**
         * Build the intent. Callers pass what the child needs to see and
         * nothing else, which keeps the teacher-facing metadata out by design
         * rather than by remembering not to add it.
         */
        fun intent(
            context: Context,
            source: String,
            target: String,
            image: String? = null,
            audio: String? = null,
            /** The pack's English meaning, shown small under the Hindi. */
            gloss: String? = null,
        ): Intent = Intent(context, ShowClassActivity::class.java).apply {
            putExtra(EXTRA_SOURCE, source)
            putExtra(EXTRA_TARGET, target)
            putExtra(EXTRA_IMAGE, image)
            putExtra(EXTRA_AUDIO, audio)
            putExtra(EXTRA_GLOSS, gloss)
        }
    }
}
