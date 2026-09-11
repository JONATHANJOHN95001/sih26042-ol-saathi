package app.olsaathi.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import app.olsaathi.OlSaathiApplication
import app.olsaathi.R
import app.olsaathi.databinding.ActivityOnboardingBinding

/**
 * First-run introduction: what this app is for, how it is driven, and what it
 * does without a network.
 *
 * Deliberately not the launcher. [LessonListActivity] starts this on top of
 * itself the first time it runs, so a fault here leaves the app usable behind
 * it rather than bricking the first launch. It is also skippable from step one
 * and reachable again from the overflow menu, so a demo can replay it without
 * clearing app data.
 *
 * The copy differs from the design mockups in two places, both on purpose.
 * The mockups list Ho, Mundari and Kudmali as available languages, and the
 * shipped packs contain none of the three. The mockups also say audio
 * recordings live on the tablet, and no Santali audio has been recorded yet.
 * Onboarding is the first thing a teacher reads, so it is the last place this
 * app should overstate what it has.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var step = 0

    private data class Step(
        val brandSub: String,
        val image: Int,
        val eyebrow: String,
        val headline: String,
        val headlineHi: String,
        val body: String,
        val bodyHi: String,
        val highlights: String,
    )

    private val steps by lazy {
        // Named from the packs that actually shipped, so this line cannot
        // drift from what the language dropdown offers. The mockups listed Ho,
        // Mundari and Kudmali; none of the three has a pack.
        val available = (application as OlSaathiApplication).languages
        val current = (application as OlSaathiApplication).currentLanguageOption()
        // The endonym is blank for most packs, and display falls back to the
        // English name, so the naive version printed "Santali • Santali".
        // Only show the native name when there actually is a distinct one.
        val lead = current?.let {
            val native = it.endonym.takeIf { e -> e.isNotBlank() && e != it.english }
            if (native != null && it.scriptNote.isNotEmpty()) "${it.english} • $native (${it.scriptNote})"
            else if (native != null) "${it.english} • $native"
            else if (it.scriptNote.isNotEmpty()) "${it.english} (${it.scriptNote})"
            else it.english
        } ?: "Santali"
        val languages = if (available.size > 1)
            "$lead\n+ ${available.size - 1} more Indian languages, chosen on the Teach screen"
        else lead
        listOf(
            Step(
                brandSub = "Jharkhand Primary Education Deployment",
                image = R.drawable.ob_1,
                eyebrow = "चरण 1 • STEP 1 OF 3",
                headline = "Teach in their language",
                headlineHi = "बच्चों की अपनी मातृभाषा में पढ़ाएं",
                body = "OL SAATHI helps teachers bridge classroom communication by " +
                    "translating textbook concepts into the mother tongues children " +
                    "speak at home.",
                bodyHi = "अब हर बच्चा पाठ को बिना किसी झिझक के अपनी प्राथमिक भाषा में " +
                    "आसानी से समझेगा।",
                highlights = languages,
            ),
            Step(
                brandSub = "Live Classroom Translation",
                image = R.drawable.ob_2,
                eyebrow = "चरण 2 • STEP 2 OF 3",
                headline = "Speak, translate and teach",
                headlineHi = "बोलें, अनुवाद करें और सरलता से पढ़ाएं",
                body = "Hold the microphone button and speak in Hindi. The tablet shows " +
                    "the sentence in the mother-tongue script, then you turn it round to " +
                    "face the class.",
                bodyHi = "बिना किसी तकनीकी ज्ञान के, बस एक बटन दबाएं और कक्षा से तुरंत जुड़ें।",
                highlights = "1  Speak Hindi  ·  माइक दबाकर बोलें\n" +
                    "2  See the script  ·  तुरंत अनुवाद देखें\n" +
                    "3  Show the class  ·  बच्चों को दिखाएं",
            ),
            Step(
                brandSub = "Air-Gapped Classroom Learning",
                image = R.drawable.ob_3,
                eyebrow = "चरण 3 • STEP 3 OF 3",
                headline = "Everything works offline",
                headlineHi = "सब कुछ बिना इंटरनेट के काम करता है",
                body = "Lessons and translations live on this tablet permanently. There " +
                    "is no sign-in, no SIM card and no server to reach. Every line says " +
                    "on screen where it came from, so you always know what you are " +
                    "teaching.",
                bodyHi = "कोई डेटा पैक या इंटरनेट सिग्नल की आवश्यकता नहीं है। आपकी कक्षा कभी नहीं रुकेगी।",
                highlights = "100% offline  ·  No SIM or Wi-Fi needed\n" +
                    "Pre-loaded lessons  ·  Print and export anytime\n" +
                    "Every line shows where its translation came from",
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSkip.setOnClickListener { finishOnboarding() }
        binding.btnBack.setOnClickListener { if (step > 0) show(step - 1) }
        binding.btnNext.setOnClickListener {
            if (step < steps.lastIndex) show(step + 1) else finishOnboarding()
        }
        show(0)
    }

    private fun show(index: Int) {
        step = index
        val s = steps[index]
        binding.textBrandSub.text = s.brandSub
        binding.imageStep.setImageResource(s.image)
        binding.textEyebrow.text = s.eyebrow
        binding.textHeadline.text = s.headline
        binding.textHeadlineHi.text = s.headlineHi
        binding.textBody.text = s.body
        binding.textBodyHi.text = s.bodyHi
        binding.textHighlights.text = s.highlights
        binding.textCounter.text = "${index + 1} of ${steps.size}"
        binding.btnBack.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
        binding.btnNext.text =
            if (index == steps.lastIndex) "Get Started (शुरू करें)" else "Next (आगे बढ़ें)"
    }

    /** Mark it seen and get out of the way. The screen behind is already up. */
    private fun finishOnboarding() {
        markSeen(this)
        finish()
    }

    companion object {
        private const val PREFS = "olsaathi_onboarding"
        private const val KEY_SEEN = "seen"

        fun hasSeen(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SEEN, false)

        fun markSeen(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SEEN, true).apply()
        }
    }
}
