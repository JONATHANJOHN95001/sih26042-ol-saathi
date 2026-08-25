package app.olsaathi

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()

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
