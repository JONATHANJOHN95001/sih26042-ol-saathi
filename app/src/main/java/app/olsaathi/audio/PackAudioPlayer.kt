package app.olsaathi.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.IOException

/**
 * Plays a WAV file from the assets/pack/audio/ directory.
 *
 * Phase 3 requirement: A play button next to the output. Missing
 * audio disables the button rather than erroring.
 *
 * N2: No catch-all swallowing. If the asset is missing, the
 * button is simply disabled. If playback fails, the error is logged
 * and surfaced — not silently degraded.
 */
class PackAudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    /** Check if an audio asset exists. */
    fun hasAudio(assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).close()
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Play a WAV from assets.
     *
     * @param assetPath Path relative to assets/, e.g. "pack/audio/p01.wav"
     * @param onComplete Called when playback finishes
     * @param onError Called if playback fails
     */
    fun play(
        assetPath: String,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
        /** Called the instant MediaPlayer is prepared and sound can leave the
         *  speaker.  Used by the latency measurement which starts when speech
         *  recognition returns and ends here. */
        onReady: () -> Unit = {}
    ) {
        stop()

        try {
            val afd = context.assets.openFd(assetPath)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                setOnPreparedListener {
                    onReady()
                    start()
                }
                setOnCompletionListener {
                    onComplete()
                }
                setOnErrorListener { _, what, extra ->
                    onError("Audio playback error: $what/$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: IOException) {
            onError("Could not load audio: ${e.message}")
        } catch (e: SecurityException) {
            onError("Audio access denied: ${e.message}")
        }
    }

    fun stop() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
                reset()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Error stopping player: ${e.message}")
            }
        }
        mediaPlayer = null
    }

    fun release() {
        stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        private const val TAG = "PackAudioPlayer"
    }
}
