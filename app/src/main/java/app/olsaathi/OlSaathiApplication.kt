package app.olsaathi

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import app.olsaathi.content.VerifiedContentPack

/**
 * Application startup.
 *
 * N2: If the pack fails to load, throw at startup rather than
 * silently degrading. The app is useless without its translations.
 */
class OlSaathiApplication : Application() {

    lateinit var pack: VerifiedContentPack
        private set

    /** Last few round-trip latency measurements (ms), most recent last. */
    val latencyHistory = mutableListOf<Long>()

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
            pack = VerifiedContentPack.load(this)
            Log.i(TAG, "Pack loaded: ${pack.size} entries, " +
                    "translation service: ${pack.translationService}, " +
                    "generated: ${pack.generated}")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Could not load content pack", e)
            throw RuntimeException("Content pack failed to load. The app cannot function without translations.", e)
        }
    }

    /** Record a latency measurement. Keeps at most 20 entries. */
    fun recordLatency(ms: Long) {
        latencyHistory.add(ms)
        if (latencyHistory.size > 20) latencyHistory.removeAt(0)
    }

    companion object {
        private const val TAG = "OlSaathi"
    }
}
