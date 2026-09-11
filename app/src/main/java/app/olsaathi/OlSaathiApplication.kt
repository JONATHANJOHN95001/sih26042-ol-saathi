package app.olsaathi

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import app.olsaathi.content.LanguageOption
import app.olsaathi.content.LanguageRegistry
import app.olsaathi.content.VerifiedContentPack
import app.olsaathi.util.LatencyLog

/**
 * Application startup.
 *
 * N2: If the pack fails to load, throw at startup rather than
 * silently degrading. The app is useless without its translations.
 */
class OlSaathiApplication : Application() {

    lateinit var pack: VerifiedContentPack
        private set

    /**
     * The language currently on screen, e.g. "sat".
     *
     * Held here rather than in an activity because the teacher's choice has to
     * survive moving between the live screen, the lesson player and the
     * worksheet generator. A worksheet printed in a different language from
     * the one the teacher was just speaking is the exact confusion this
     * avoids.
     */
    var currentLanguage: String = LanguageRegistry.FALLBACK_CODE
        private set

    /** Languages this build can actually deliver, font and all. */
    val languages: List<LanguageOption> by lazy { LanguageRegistry.available(this) }

    /** Notified after a successful language switch, so open screens can redraw. */
    private val languageListeners = mutableListOf<(String) -> Unit>()

    /** Last few round-trip latency measurements (ms), most recent last. */
    /**
     * Pack lookup times, in milliseconds.
     *
     * This is a hash lookup and lands near zero. It is worth showing because it
     * is the offline claim made concrete, but it is NOT the deliverable, which
     * is voice in to voice out. Keeping the two in one list produced a median
     * blended out of dozens of near-zero lookups and a handful of real spans,
     * which flattered the number and measured nothing anyone asked about.
     */
    private val latencyLog = LatencyLog()

    /** Snapshot of the pack lookup times, most recent last. */
    val latencyHistory: List<Long> get() = latencyLog.snapshot()

    /**
     * Voice-to-voice times, in milliseconds: from the moment speech
     * recognition returns Hindi to the moment MediaPlayer is prepared and
     * Santali can leave the speaker. This is the one the 3-second ceiling
     * applies to. Empty until the pack ships audio, and saying so is better
     * than borrowing the lookup number.
     */
    private val voiceLatencyLog = LatencyLog()

    /** Snapshot of the voice-to-voice times, most recent last. */
    val voiceLatencyHistory: List<Long> get() = voiceLatencyLog.snapshot()

    private val liveVoiceLatencyLog = LatencyLog()

    /**
     * Voice-to-voice times for the live path: the same span as
     * [voiceLatencyHistory], but for a sentence that was not in the pack and
     * so had to be translated and synthesised over the network.
     *
     * Kept apart from the pack figure on purpose. Averaging a 200 ms local
     * playback with a 2.5 s network round trip produces a number that
     * describes neither, and the first question a judge asks is which of the
     * two the 3-second claim refers to.
     */
    val liveVoiceLatencyHistory: List<Long> get() = liveVoiceLatencyLog.snapshot()

    /** Pre-flight summary from the last run, or null if never run. */
    var preflightSummary: String? = null
        set(value) { field = value }

    /**
     * Timestamp when this process was created (Application class init).
     * Used as the start point for cold-start measurement.
     */
    private val processStartMs: Long = System.currentTimeMillis()

    /**
     * Cold start duration in milliseconds: Application.onCreate to
     * first Activity.onResume. Recorded once by the lifecycle callback
     * and never updated after that.
     *
     * Read this from Check & Proof. Do not compute it there.
     */
    var coldStartMs: Long = 0L
        private set

    override fun onCreate() {
        super.onCreate()

        // Register lifecycle callback to capture cold start exactly once.
        // The first Activity.onResume marks the end of cold start.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (coldStartMs == 0L) {
                    coldStartMs = System.currentTimeMillis() - processStartMs
                    Log.i(TAG, "Cold start: ${coldStartMs}ms (${activity.javaClass.simpleName})")
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // N2: Never swallow a failure. If the pack is missing or
        // malformed, the app cannot function. Surface it immediately.
        try {
            currentLanguage = resolveStartupLanguage()
            pack = VerifiedContentPack.load(this, currentLanguage)
            Log.i(TAG, "Pack loaded: ${pack.languageCode}, ${pack.size} entries, " +
                    "translation service: ${pack.translationService}, " +
                    "generated: ${pack.generated}")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Could not load content pack", e)
            throw RuntimeException("Content pack failed to load. The app cannot function without translations.", e)
        }
    }

    /**
     * Which language to open with: the teacher's last choice, if it still ships.
     *
     * A stored code can outlive the pack that backed it, because a later build
     * may drop a language. Falling back to the default is the only safe move:
     * honouring a stale code would throw at startup and brick the app over a
     * preference.
     */
    private fun resolveStartupLanguage(): String {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_LANGUAGE, null)
        val offered = LanguageRegistry.available(this).map { it.code }
        return when {
            stored != null && stored in offered -> stored
            else -> LanguageRegistry.default(this)
        }
    }

    /**
     * Switch the app to another language and remember the choice.
     *
     * Returns false and changes nothing if the pack will not load, so a broken
     * language cannot take the running screen down with it. The caller is
     * expected to tell the teacher rather than fail silently.
     */
    fun switchLanguage(code: String): Boolean {
        if (code == currentLanguage) return true
        return try {
            val loaded = VerifiedContentPack.load(this, code)
            pack = loaded
            currentLanguage = code
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, code).apply()
            Log.i(TAG, "Language switched to $code (${loaded.size} entries)")
            languageListeners.toList().forEach { it(code) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not switch to $code", e)
            false
        }
    }

    /** Register a redraw callback. Returns a handle the caller must unregister. */
    fun onLanguageChanged(listener: (String) -> Unit): (String) -> Unit {
        languageListeners.add(listener)
        return listener
    }

    fun removeLanguageListener(listener: (String) -> Unit) {
        languageListeners.remove(listener)
    }

    /** The full description of the language on screen, or null if unlisted. */
    fun currentLanguageOption(): LanguageOption? =
        LanguageRegistry.byCode(this, currentLanguage)

    /** Record a pack lookup time. Keeps at most 20 entries. */
    fun recordLatency(ms: Long) {
        latencyLog.add(ms)
    }

    /** Record a voice-to-voice time. Keeps at most 20 entries. */
    fun recordLiveVoiceLatency(ms: Long) {
        liveVoiceLatencyLog.add(ms)
    }

    /** Record a voice-to-voice time. Keeps at most 20 entries. */
    fun recordVoiceLatency(ms: Long) {
        voiceLatencyLog.add(ms)
    }

    companion object {
        private const val TAG = "OlSaathi"

        /** Where the teacher's language choice is remembered between runs. */
        private const val PREFS = "olsaathi.prefs"
        private const val KEY_LANGUAGE = "language"
    }
}
