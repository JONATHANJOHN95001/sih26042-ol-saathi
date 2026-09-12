package app.olsaathi.ui

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.olsaathi.OlSaathiApplication
import app.olsaathi.databinding.ActivityOfflineSetupBinding
import app.olsaathi.databinding.ItemSetupStepBinding
import app.olsaathi.mt.OfflineTranslator
import app.olsaathi.speech.OfflineHindiModel
import app.olsaathi.util.NetworkGuard
import com.google.android.material.button.MaterialButton

/**
 * "Get ready to teach offline" (design screen E).
 *
 * The three things the classroom needs with no internet, each read from the
 * tablet rather than assumed: the lesson pack (always in the APK), Android's
 * offline Hindi speech model, and the on-device translator. The two downloads
 * start from here with real progress, and a step the tablet cannot do (an
 * old Android, too little memory) says so instead of offering a button.
 */
class OfflineSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineSetupBinding

    private enum class State { READY, AVAILABLE, WORKING, UNAVAILABLE }

    private var speechState = State.WORKING
    private var translatorState = State.WORKING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfflineSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnSetupLater.setOnClickListener { finish() }
        binding.btnSetupDone.setOnClickListener { finish() }

        showPack()
        checkSpeech()
        checkTranslator()
    }

    // ── 1. Lesson pack ───────────────────────────────────────────────────

    private fun showPack() {
        val app = application as OlSaathiApplication
        val pack = app.pack
        val name = app.currentLanguageOption()?.english ?: pack.languageEnglish
        val lines = pack.entries().size
        val audio = pack.audioCount
        with(binding.rowPack) {
            textStepIcon.text = "📦"
            textStepTitle.text = "Lesson pack · on this tablet"
            textStepChip.text = name
            textStepDesc.text = "$lines $name lines, " +
                (if (audio > 0) "$audio with recorded audio. " else "text only. ") +
                "Built into the app, nothing to download."
        }
        setState(binding.rowPack, State.READY, "✓ Ready")
    }

    // ── 2. Offline Hindi speech ──────────────────────────────────────────

    private fun checkSpeech() {
        with(binding.rowSpeech) {
            textStepIcon.text = "🎙️"
            textStepTitle.text = "Offline Hindi speech"
            // Android sets the size, and it differs by device: 61 MB on a
            // Galaxy Tab A8, 113 MB on the API 35 emulator.
            textStepChip.text = "60 to 115 MB, set by Android"
            textStepDesc.text = "Lets hold-to-speak understand Hindi with no internet. " +
                "Provided by Android on this tablet."
        }
        setState(binding.rowSpeech, State.WORKING, "Checking…")
        OfflineHindiModel.ensure(this, download = false) { status ->
            when (status) {
                OfflineHindiModel.Status.INSTALLED ->
                    setState(binding.rowSpeech, State.READY, "✓ Ready")
                OfflineHindiModel.Status.PENDING ->
                    setState(binding.rowSpeech, State.WORKING, "Downloading…")
                OfflineHindiModel.Status.MISSING, OfflineHindiModel.Status.DOWNLOAD_REQUESTED ->
                    offer(binding.rowSpeech, "Download") { downloadSpeech() }
                OfflineHindiModel.Status.UNSUPPORTED, OfflineHindiModel.Status.UNAVAILABLE,
                OfflineHindiModel.Status.ERROR -> {
                    binding.rowSpeech.textStepDesc.text =
                        "This tablet cannot recognise Hindi offline. Hold-to-speak uses the " +
                            "internet here; typing Hindi always works."
                    setState(binding.rowSpeech, State.UNAVAILABLE, "Not on this tablet")
                }
            }
            speechState = stateOf(binding.rowSpeech)
            summarise()
        }
    }

    private fun downloadSpeech() {
        if (!online()) return
        setState(binding.rowSpeech, State.WORKING, "Downloading…")
        binding.rowSpeech.progressStep.visibility = View.VISIBLE
        OfflineHindiModel.ensure(
            this,
            download = true,
            onProgress = { pct -> runOnUiThread { binding.rowSpeech.progressStep.progress = pct } },
            onDownloaded = { ok ->
                runOnUiThread {
                    binding.rowSpeech.progressStep.visibility = View.GONE
                    if (ok) setState(binding.rowSpeech, State.READY, "✓ Ready")
                    else setState(binding.rowSpeech, State.WORKING, "Queued by Android")
                    summarise()
                }
            },
        ) { status ->
            if (status == OfflineHindiModel.Status.INSTALLED) {
                binding.rowSpeech.progressStep.visibility = View.GONE
                setState(binding.rowSpeech, State.READY, "✓ Ready")
                summarise()
            }
        }
    }

    // ── 3. Offline translator ────────────────────────────────────────────

    private fun checkTranslator() {
        with(binding.rowTranslator) {
            textStepIcon.text = "🔤"
            textStepTitle.text = "Offline translator"
            textStepChip.text = "about ${OfflineTranslator.DOWNLOAD_MB} MB · best on Wi-Fi"
            textStepDesc.text = "Translates sentences that are not in the lesson pack on this " +
                "tablet (AI4Bharat IndicTrans2), labelled \"Machine translation, on this tablet\"."
        }
        when {
            OfflineTranslator.isInstalled(this) ->
                setState(binding.rowTranslator, State.READY, "✓ Ready")
            !OfflineTranslator.deviceCanRun(this) -> {
                binding.rowTranslator.textStepDesc.text =
                    "This tablet has too little memory to run it. New sentences are " +
                        "translated when there is internet."
                setState(binding.rowTranslator, State.UNAVAILABLE, "Not on this tablet")
            }
            else -> offer(binding.rowTranslator, "Download") { downloadTranslator() }
        }
        translatorState = stateOf(binding.rowTranslator)
        summarise()
    }

    private fun downloadTranslator() {
        if (!online()) return
        setState(binding.rowTranslator, State.WORKING, "Downloading…")
        val bar: ProgressBar = binding.rowTranslator.progressStep
        bar.visibility = View.VISIBLE
        OfflineTranslator.download(
            this,
            onProgress = { pct ->
                runOnUiThread {
                    bar.progress = pct
                    binding.rowTranslator.textStepStatus.text = "$pct%"
                }
            },
            onDone = { ok ->
                runOnUiThread {
                    bar.visibility = View.GONE
                    if (ok) {
                        setState(binding.rowTranslator, State.READY, "✓ Ready")
                        OfflineTranslator.warmUp(this)
                    } else {
                        offer(binding.rowTranslator, "Try again") { downloadTranslator() }
                    }
                    translatorState = stateOf(binding.rowTranslator)
                    summarise()
                }
            },
        )
    }

    // ── Shared ───────────────────────────────────────────────────────────

    private fun online(): Boolean {
        if (NetworkGuard.isOnline(this)) return true
        android.widget.Toast.makeText(
            this, "Connect to Wi-Fi to download this.", android.widget.Toast.LENGTH_LONG
        ).show()
        return false
    }

    private fun offer(row: ItemSetupStepBinding, label: String, action: () -> Unit) {
        setState(row, State.AVAILABLE, "Not downloaded")
        row.btnStep.text = label
        row.btnStep.visibility = View.VISIBLE
        row.btnStep.setOnClickListener { action() }
    }

    private fun setState(row: ItemSetupStepBinding, state: State, label: String) {
        row.root.tag = state
        row.textStepStatus.text = label
        row.textStepStatus.setTextColor(
            when (state) {
                State.READY -> 0xFF14532D.toInt()
                State.AVAILABLE -> 0xFF9C441D.toInt()
                State.WORKING -> 0xFFB84310.toInt()
                State.UNAVAILABLE -> 0xFF78716C.toInt()
            }
        )
        if (state != State.AVAILABLE) row.btnStep.visibility = View.GONE
    }

    private fun stateOf(row: ItemSetupStepBinding) = row.root.tag as? State ?: State.WORKING

    /** "2 of 3 ready", counting only steps this tablet can actually do. */
    private fun summarise() {
        val rows = listOf(binding.rowPack, binding.rowSpeech, binding.rowTranslator)
        val possible = rows.count { stateOf(it) != State.UNAVAILABLE }
        val ready = rows.count { stateOf(it) == State.READY }
        binding.textSetupSummary.text = "$ready of $possible ready"
    }
}
