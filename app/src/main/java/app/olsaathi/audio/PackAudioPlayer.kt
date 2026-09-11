package app.olsaathi.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File
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
 *
 * Lifecycle: every MediaPlayer this class creates is released before the
 * reference to it is dropped. stop() releases, because play() starts with
 * stop() — otherwise every line a teacher plays would abandon a player
 * holding a native codec and an audio session, and on a 2 GB tablet a
 * classroom session of dozens of playbacks would pile up until the
 * finalizer happened to run.
 */
class PackAudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Scratch file holding the current live-synthesised clip.
     *
     * Live audio arrives as bytes, and MediaPlayer wants a source it can seek,
     * so the bytes are spilled to the cache directory. Exactly one of these
     * exists at a time and it is deleted the moment it stops being needed:
     * the target device has 2 GB of storage pressure and a classroom session
     * is a great many sentences.
     */
    private var liveFile: File? = null

    // release() must leave the player dead for good: an Activity that
    // released in onDestroy can still receive a late play request, and
    // resurrecting a player then would leak a codec nobody will stop.
    private var released = false

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
        /** Playback rate. 0.75 is the classroom-repeat speed: slow enough for
         *  a child to hear each syllable, not so slow the vowels smear. Pitch
         *  is left alone, so the speaker still sounds like themselves. */
        speed: Float = 1f,
        /** Called the instant MediaPlayer is prepared and sound can leave the
         *  speaker.  Used by the latency measurement which starts when speech
         *  recognition returns and ends here. */
        onReady: () -> Unit = {}
    ) {
        if (released) {
            onError("Audio player has been released")
            return
        }
        stop()

        try {
            val afd = context.assets.openFd(assetPath)
            startPlayer(onComplete, onError, speed, onReady) {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
        } catch (e: IOException) {
            onError("Could not load audio: ${e.message}")
        } catch (e: SecurityException) {
            onError("Audio access denied: ${e.message}")
        }
    }

    /**
     * Play WAV bytes that were synthesised at runtime rather than shipped.
     *
     * This is what makes voice to voice possible for a sentence outside the
     * 53-phrase pack. Until it existed the app could translate an unfamiliar
     * sentence and then had no way to say it, so the answer appeared on screen
     * in a script the teacher cannot read and stayed silent.
     *
     * The bytes are written to the cache directory because MediaPlayer needs a
     * seekable source. The file is deleted on the next call and on [stop], so
     * at most one lives on disk at a time.
     *
     * [onReady] fires at the same instant it does for pack audio, the moment
     * MediaPlayer is prepared, so both paths are measured against the same
     * definition of "sound can leave the tablet".
     */
    fun playBytes(
        audio: ByteArray,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
        speed: Float = 1f,
        onReady: () -> Unit = {}
    ) {
        if (released) {
            onError("Audio player has been released")
            return
        }
        if (audio.isEmpty()) {
            onError("Empty audio")
            return
        }
        stop()

        try {
            val file = File.createTempFile("live-", ".wav", context.cacheDir)
            file.writeBytes(audio)
            liveFile = file
            startPlayer(onComplete, onError, speed, onReady) {
                setDataSource(file.absolutePath)
            }
        } catch (e: IOException) {
            onError("Could not play synthesised audio: ${e.message}")
        }
    }

    /**
     * Shared MediaPlayer setup for both sources.
     *
     * One copy rather than two, because the prepared-listener is where the
     * latency measurement is taken and the two paths drifting apart would
     * quietly make the pack number and the live number mean different things.
     */
    private inline fun startPlayer(
        crossinline onComplete: () -> Unit,
        crossinline onError: (String) -> Unit,
        speed: Float,
        crossinline onReady: () -> Unit,
        setSource: MediaPlayer.() -> Unit,
    ) {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setSource()

            setOnPreparedListener {
                onReady()
                // Assigning playbackParams can itself start the player on
                // some builds, so set it first and let the start() below
                // be the no-op rather than the other way round.
                if (speed != 1f) {
                    playbackParams = playbackParams.setSpeed(speed)
                }
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
    }

    /**
     * Stop and release the current player, if any. Safe to call when no
     * player exists and safe to call after release().
     *
     * The player is captured and the field cleared before release so the
     * player instance is released exactly once: the old release() read the
     * field after stop() had already nulled it, so no MediaPlayer was ever
     * released anywhere in this app.
     *
     * Release runs even when the player is in the Error state, where
     * isPlaying() itself throws — the two try blocks exist so one throwing
     * cannot skip the release.
     */
    fun stop() {
        liveFile?.delete()
        liveFile = null
        val player = mediaPlayer
        mediaPlayer = null
        if (player == null) return
        try {
            if (player.isPlaying) player.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Error stopping player: ${e.message}")
        }
        try {
            player.reset()
            player.release()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Error releasing player: ${e.message}")
        }
    }

    /** Release the current player and leave this object unusable. */
    fun release() {
        stop()
        released = true
    }

    companion object {
        private const val TAG = "PackAudioPlayer"
    }
}