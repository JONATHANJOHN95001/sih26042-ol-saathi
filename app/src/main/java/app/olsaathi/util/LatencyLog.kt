package app.olsaathi.util

/**
 * Bounded ring buffer of latency readings, safe to write from a background
 * executor while the UI thread reads.
 *
 * The latency lists used to be bare ArrayLists on OlSaathiApplication:
 * recordLatency() adds and removes on TranslationRouter's executor while the
 * Check and Proof screen iterates on the main thread. The removeAt(0) past 20
 * entries is a structural modification, so a write mid-read surfaces as a
 * ConcurrentModificationException or a median computed over torn state — on
 * the exact screen this project is judged on. Every write here is
 * synchronised, and readers only ever see an immutable copy of the buffer.
 */
class LatencyLog(private val capacity: Int = DEFAULT_CAPACITY) {

    private val lock = Any()
    private val entries = ArrayList<Long>(capacity)

    /** Add a reading; the oldest is evicted once the log is full. */
    fun add(ms: Long) {
        synchronized(lock) {
            entries.add(ms)
            if (entries.size > capacity) entries.removeAt(0)
        }
    }

    /** Immutable copy of the current readings, oldest first. */
    fun snapshot(): List<Long> = synchronized(lock) { entries.toList() }

    companion object {
        private const val DEFAULT_CAPACITY = 20
    }
}