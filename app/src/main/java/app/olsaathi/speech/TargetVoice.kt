package app.olsaathi.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Speaks the target language with the device's own synthesiser.
 *
 * ## Why this exists
 *
 * The app had exactly two ways to make a sound: a WAV compiled into the APK,
 * of which there are none, and a clip synthesised over the network, which
 * needs a Bhashini key nobody has. So voice to voice could not be demonstrated
 * at all.
 *
 * There is a third source that was sitting unused. Android's own TTS engine
 * ships voices for several of the languages in the pack, and where the voice
 * data has been downloaded it works **offline, on device, with no key and no
 * network**. For those languages this is strictly the best option available:
 * it is faster than a round trip, it survives a dead venue wifi, and it costs
 * nothing.
 *
 * It is not available for every language. Santali in particular has no Android
 * voice, which is why the app was written around not having one. This class
 * therefore never assumes: it maps a pack code to a locale and then **asks the
 * device**, and every caller must handle "no".
 *
 * ## What its output is, and is not
 *
 * A device voice reading a machine translation is still a machine reading text
 * nobody has checked. It sounds fluent, which makes it more persuasive than it
 * has earned, so [app.olsaathi.content.AudioProvenance.DEVICE_TTS] says
 * "unchecked" on screen beside it. Fluency is not accuracy.
 */
class TargetVoice(context: Context) {

    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    /** Called once the engine has finished starting up, on the main thread. */
    var onReady: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) Log.w(TAG, "TextToSpeech unavailable, status $status")
            onReady?.invoke()
        }
    }

    /**
     * True when this device can actually speak [languageCode] right now.
     *
     * [TextToSpeech.LANG_MISSING_DATA] deliberately counts as false. The
     * engine knows the language but has not downloaded the voice, and asking
     * it to speak produces silence rather than an error, which would look
     * exactly like a broken play button.
     */
    fun hasVoice(languageCode: String): Boolean = offlineVoiceFor(languageCode) != null

    /**
     * The voice to use for [languageCode], or null when none will work offline.
     *
     * Asking isLanguageAvailable alone is not enough, and this was found on a
     * real engine rather than guessed. Google TTS answers LANG_AVAILABLE for
     * Tamil even when its only Tamil voice is a network voice, so the Teach
     * screen offered "Device voice" playback, and then with no internet the
     * engine logged "We timed out and used all of our retries" and nothing came
     * out of the speaker. At a venue with no signal that is a play button that
     * silently does nothing.
     *
     * So a voice counts only if it is installed on the device and does not need
     * the network. When the voice data has not been downloaded the honest answer
     * is "no voice", and the app says so instead of offering a button that fails.
     */
    private fun offlineVoiceFor(languageCode: String): Voice? {
        val engine = tts ?: return null
        if (!ready) return null
        return offlineVoiceIn(engine, languageCode)
    }

    /**
     * Speak [text] in [languageCode].
     *
     * [onStart] fires the instant audio begins, which is the same moment
     * MediaPlayer's prepared listener marks for the other two audio paths, so
     * all three feed the same voice-to-voice measurement on equal terms.
     *
     * Returns false when nothing will be spoken, so the caller can leave the
     * play button disabled rather than lighting up a control that does
     * nothing.
     */
    fun speak(
        text: String,
        languageCode: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        /** Synthesis failed after it started. Defaults to [onDone] so callers
         *  that only re-enable a button keep working unchanged. */
        onError: () -> Unit = onDone,
        /** Speech rate, 1.0 normal. Matches the playback-speed setting so
         *  the device voice slows down with the recorded clips. */
        rate: Float = 1f,
    ): Boolean {
        val engine = tts ?: return false
        if (text.isBlank()) return false
        val voice = offlineVoiceFor(languageCode) ?: return false

        return try {
            // Pin the exact offline voice. Setting a language and letting the
            // engine choose is how a network voice got picked.
            engine.voice = voice
            engine.setSpeechRate(rate)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = onStart()
                override fun onDone(utteranceId: String?) = onDone()

                @Deprecated("Required by the base class", ReplaceWith(""))
                override fun onError(utteranceId: String?) = onError()

                override fun onError(utteranceId: String?, errorCode: Int) = onError()
            })
            val id = "olsaathi-" + System.currentTimeMillis()
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id) == TextToSpeech.SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "speak failed for $languageCode: ${e.message}")
            false
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop failed: ${e.message}")
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "TargetVoice"

        /**
         * Pack code to Android locale.
         *
         * The packs use three-letter codes and Android wants the two-letter
         * tag for most of these. The map is deliberately generous: listing a
         * language here is not a claim that a voice exists for it, only that
         * this is the locale to ask about. [hasVoice] does the asking, and the
         * device is the authority.
         *
         * Santali is mapped and will simply answer no on every device shipping
         * today. That is the correct outcome and it is why the whole app is
         * built to work without a voice.
         */
        private val LOCALES = mapOf(
            "asm" to Locale("as", "IN"),
            "ben" to Locale("bn", "IN"),
            "brx" to Locale("brx", "IN"),
            "doi" to Locale("doi", "IN"),
            "gom" to Locale("kok", "IN"),
            "guj" to Locale("gu", "IN"),
            "hin" to Locale("hi", "IN"),
            "kan" to Locale("kn", "IN"),
            "mai" to Locale("mai", "IN"),
            "mal" to Locale("ml", "IN"),
            "mar" to Locale("mr", "IN"),
            "mni" to Locale("mni", "IN"),
            "npi" to Locale("ne", "NP"),
            "ory" to Locale("or", "IN"),
            "pan" to Locale("pa", "IN"),
            "sat" to Locale("sat", "IN"),
            "tam" to Locale("ta", "IN"),
            "tel" to Locale("te", "IN"),
        )

        /** The locale to ask the engine about, or null when we have no mapping. */
        fun localeFor(languageCode: String): Locale? = LOCALES[languageCode.lowercase()]

        /**
         * An installed, network-free voice for [languageCode] in [engine], or
         * null. Shared so that every screen saying whether a voice exists gives
         * the same answer; [offlineVoiceFor] explains why a network voice does
         * not count.
         */
        fun offlineVoiceIn(engine: TextToSpeech, languageCode: String): Voice? {
            val locale = localeFor(languageCode) ?: return null
            return try {
                if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return null
                val wanted = iso3(locale)
                engine.voices?.firstOrNull { v ->
                    iso3(v.locale) == wanted &&
                        !v.isNetworkConnectionRequired &&
                        v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
                }
            } catch (e: Exception) {
                Log.w(TAG, "voice lookup failed for $languageCode: ${e.message}")
                null
            }
        }

        /** Three-letter language code, the form voices and packs can agree on. */
        private fun iso3(locale: Locale): String =
            try {
                locale.isO3Language
            } catch (e: Exception) {
                locale.language
            }
    }
}
