package app.olsaathi.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import app.olsaathi.net.BhashiniClient
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Push-to-talk Hindi: record while the button is held, then ask Bhashini.
 *
 * Android's [android.speech.SpeechRecognizer] is only as good as whichever
 * recogniser the phone ships. On a OnePlus Nord 5 that was Google's
 * on-device service, which cannot do Hindi without an offline pack and which
 * has no online mode, so "Hold to Speak Hindi" failed every time. Recording
 * the audio ourselves and sending it to Bhashini's Hindi ASR works on any
 * phone with a microphone and a network.
 *
 * Audio is 16 kHz mono 16-bit PCM, the format the ASR model was probed with.
 * Recording is capped at [MAX_SECONDS] so a stuck button cannot fill memory
 * on a 2 GB tablet, and anything shorter than [MIN_MS] is treated as a tap.
 *
 * The caller must hold RECORD_AUDIO before calling [start].
 */
class BhashiniSpeechInput(
    private val client: BhashiniClient = BhashiniClient(),
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStateChanged: (State) -> Unit = {},
) {
    enum class State { IDLE, RECORDING, TRANSCRIBING }

    @Volatile private var recording = false

    val isConfigured: Boolean get() = client.isConfigured

    @SuppressLint("MissingPermission") // checked by the caller before start()
    fun start() {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            onError("The microphone is not available.")
            return
        }
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4
            )
        } catch (e: Exception) {
            onError("The microphone could not be opened.")
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            onError("The microphone is busy.")
            return
        }

        recording = true
        onStateChanged(State.RECORDING)
        Thread({
            val pcm = ByteArrayOutputStream()
            val buf = ByteArray(minBuf)
            val maxBytes = RATE * 2 * MAX_SECONDS
            try {
                rec.startRecording()
                while (recording && pcm.size() < maxBytes) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) pcm.write(buf, 0, n)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Recording failed: ${e.message}")
            } finally {
                try { rec.stop() } catch (_: Exception) {}
                rec.release()
                recording = false
            }

            val bytes = pcm.toByteArray()
            if (bytes.size < RATE * 2 * MIN_MS / 1000) {
                onStateChanged(State.IDLE)
                onError("Keep holding the button while you speak.")
                return@Thread
            }
            onStateChanged(State.TRANSCRIBING)
            val t0 = System.currentTimeMillis()
            val text = client.transcribe(wav(bytes))
            Log.i(TAG, "Bhashini ASR: ${bytes.size / (RATE * 2.0)} s of audio in ${System.currentTimeMillis() - t0} ms")
            onStateChanged(State.IDLE)
            if (text.isNullOrBlank()) {
                onError("Could not recognise the Hindi. Check the internet connection and try again.")
            } else {
                onResult(text)
            }
        }, "bhashini-asr").start()
    }

    /** Stop recording; the clip is then sent. Safe to call when not recording. */
    fun stop() {
        recording = false
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcm.size)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        out.putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        out.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcm.size)
        out.put(pcm)
        return out.array()
    }

    companion object {
        private const val TAG = "BhashiniSpeechInput"
        private const val RATE = 16000
        private const val MAX_SECONDS = 15
        private const val MIN_MS = 400
    }
}
