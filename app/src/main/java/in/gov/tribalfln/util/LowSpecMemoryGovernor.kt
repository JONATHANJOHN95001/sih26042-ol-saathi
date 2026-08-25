package `in`.gov.tribalfln.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log

/**
 * LowSpecMemoryGovernor — Enforces the 180MB heap ceiling and manages
 * memory aggressively on low-spec Android Go devices (2GB RAM).
 * Provides tiered GC triggers and OOM emergency recovery.
 */
class LowSpecMemoryGovernor(private val context: Context) {

    companion object {
        private const val TAG = "MemoryGovernor"
        const val CEILING_MB = 180L
        const val CEILING_BYTES = CEILING_MB * 1024 * 1024
        private const val WARNING_THRESHOLD = 0.70
        private const val CRITICAL_THRESHOLD = 0.85
        private const val EMERGENCY_THRESHOLD = 0.95
    }

    enum class MemoryPressure {
        NONE, WARNING, CRITICAL, EMERGENCY
    }

    /**
     * Get current heap usage in megabytes.
     */
    fun getCurrentHeapMB(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
    }

    /**
     * Get the current memory pressure level.
     */
    fun getPressureLevel(): MemoryPressure {
        val usedMB = getCurrentHeapMB()
        val ratio = usedMB.toDouble() / CEILING_MB
        return when {
            ratio >= EMERGENCY_THRESHOLD -> MemoryPressure.EMERGENCY
            ratio >= CRITICAL_THRESHOLD -> MemoryPressure.CRITICAL
            ratio >= WARNING_THRESHOLD -> MemoryPressure.WARNING
            else -> MemoryPressure.NONE
        }
    }

    /**
     * Check if heap is within the 180MB ceiling.
     */
    fun isWithinCeiling(): Boolean = getCurrentHeapMB() <= CEILING_MB

    /**
     * Enforce the heap ceiling by triggering GC if above threshold.
     * Returns true if GC was triggered.
     */
    fun enforceCeiling(): Boolean {
        val usedMB = getCurrentHeapMB()
        if (usedMB > CEILING_MB * CRITICAL_THRESHOLD / 100 * 100) {
            Log.w(TAG, "Heap at ${usedMB}MB / ${CEILING_MB}MB — triggering GC")
            System.gc()
            return true
        }
        return false
    }

    /**
     * Emergency OOM recovery: aggressive GC and bitmap pool flush.
     * Returns MB reclaimed.
     */
    fun emergencyRecovery(): Long {
        val before = getCurrentHeapMB()
        Log.e(TAG, "OOM emergency recovery — heap at ${before}MB")
        System.gc()
        System.gc()
        BitmapPoolManager.clearPool()
        val after = getCurrentHeapMB()
        val reclaimed = before - after
        Log.e(TAG, "Recovery reclaimed ${reclaimed}MB (now ${after}MB)")
        return reclaimed
    }

    /**
     * Get a detailed memory status report.
     */
    fun getStatusReport(): String {
        val heapMB = getCurrentHeapMB()
        val pressure = getPressureLevel()
        val rt = Runtime.getRuntime()
        val freeMB = rt.freeMemory() / (1024 * 1024)
        val totalMB = rt.totalMemory() / (1024 * 1024)
        val maxMB = rt.maxMemory() / (1024 * 1024)

        return buildString {
            appendLine("=== MemoryGovernor Status ===")
            appendLine("Heap: ${heapMB}MB / ${CEILING_MB}MB ceiling")
            appendLine("Runtime: ${freeMB}MB free / ${totalMB}MB total / ${maxMB}MB max")
            appendLine("Pressure: $pressure")
            appendLine("Bitmap pool: ${BitmapPoolManager.getStats().toLogString()}")
        }
    }
}
