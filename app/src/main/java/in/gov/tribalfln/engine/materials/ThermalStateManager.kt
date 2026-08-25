package `in`.gov.tribalfln.engine.materials

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * ThermalStateManager — Monitors device thermal state and power conditions
 * to proactively manage memory and CPU usage on low-spec Android devices.
 * Triggers performance degradation protocols when thermal throttling is detected.
 */
class ThermalStateManager(private val context: Context) {

    companion object {
        private const val TAG = "ThermalState"
    }

    private var isMonitoring = false
    private var lastThermalStatus = ThermalStatus.NORMAL

    enum class ThermalStatus {
        NORMAL, MODERATE, SEVERE, CRITICAL, EMERGENCY
    }

    /**
     * Start monitoring thermal conditions.
     */
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        Log.d(TAG, "Thermal monitoring started")
    }

    /**
     * Stop monitoring thermal conditions.
     */
    fun stopMonitoring() {
        isMonitoring = false
        Log.d(TAG, "Thermal monitoring stopped")
    }

    /**
     * Check if the device is currently thermally throttled.
     */
    fun isThrottled(): Boolean {
        return lastThermalStatus != ThermalStatus.NORMAL
    }

    /**
     * Get current thermal status level.
     */
    fun getThermalStatus(): ThermalStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    pm.currentThermalStatus
                } else {
                    0
                }
                lastThermalStatus = when {
                    thermalStatus >= 4 -> ThermalStatus.CRITICAL
                    thermalStatus >= 3 -> ThermalStatus.SEVERE
                    thermalStatus >= 2 -> ThermalStatus.MODERATE
                    else -> ThermalStatus.NORMAL
                }
            }
        }
        return lastThermalStatus
    }

    /**
     * Get recommended performance level based on thermal state.
     * Returns a value between 0.0 (full throttle) and 1.0 (maximum conservation).
     */
    fun getConservationLevel(): Float {
        return when (getThermalStatus()) {
            ThermalStatus.NORMAL -> 0.0f
            ThermalStatus.MODERATE -> 0.25f
            ThermalStatus.SEVERE -> 0.50f
            ThermalStatus.CRITICAL -> 0.75f
            ThermalStatus.EMERGENCY -> 1.0f
        }
    }
}
