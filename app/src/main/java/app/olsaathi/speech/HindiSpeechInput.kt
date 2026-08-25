package app.olsaathi.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Wraps Android's [SpeechRecognizer] to accept Hindi voice input.
 *
 * Phase 4 requirement: Push to talk, feed recognised text into the
 * same lookup. Recognition is constrained to a known phrase set via
 * [EXTRA_PARTIAL_RESULTS] so the output is more honest about what
 * this system can do.
 *
 * The caller must check [isAvailable] before creating an instance.
 * If the device has no speech recognition, this class does not exist.
 *
 * N2: ERROR_NO_MATCH surfaces as an error to the caller, never as
 * silence and never as a guessed phrase.
 */
class HindiSpeechInput(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    /**
     * Timestamp (millis) when speech ended. Used to measure the round-trip
     * from end-of-speech to recognised text arriving on screen.
     */
    private var speechEndTimestamp: Long = 0L

    /** Check if speech recognition is available on this device. */
    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isAvailable) {
            onError("Speech recognition not available on this device")
            return
        }

        if (isListening) {
            stopListening()
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

        isListening = true
        onListeningChanged(true)
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        isListening = false
        onListeningChanged(false)
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended")
            speechEndTimestamp = System.currentTimeMillis()
            isListening = false
            onListeningChanged(false)
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No match found in the phrase pack"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_NETWORK -> "No offline Hindi model and no network. Install the Hindi speech pack."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                // Observed on a real device: requesting offline hi-IN when the
                // Hindi language pack has not been downloaded returns 12, and
                // "Speech error: 12" tells the teacher nothing they can act on.
                // These two constants need API 33, so compare by value.
                12 -> "Hindi offline speech is not installed on this tablet. " +
                      "Settings, then System, then Languages and input, then " +
                      "On-device speech recognition, and add Hindi."
                13 -> "This device does not support Hindi speech recognition."
                else -> "Speech error: $error"
            }
            // N2: Surface the error, never swallow it.
            // N1: ERROR_NO_MATCH → caller shows UNAVAILABLE, not a guess.
            Log.e(TAG, "Speech error $error: $message")
            isListening = false
            onListeningChanged(false)
            onError(message)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()

            // Timing: measure from end of speech to recognised text arriving
            val elapsedMs = if (speechEndTimestamp > 0) {
                System.currentTimeMillis() - speechEndTimestamp
            } else {
                0L
            }

            Log.i(TAG, "Recognition round-trip: ${elapsedMs}ms from end-of-speech to result")

            if (best != null) {
                Log.d(TAG, "Best match: '$best' (${best.length} chars)")
                onResult(best)
            } else {
                // N2: Surface the failure. N1: No guessed phrase.
                Log.w(TAG, "No results in bundle")
                onError("No match found in the phrase pack")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "HindiSpeechInput"
    }
}
