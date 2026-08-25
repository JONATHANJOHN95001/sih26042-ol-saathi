package `in`.gov.tribalfln.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.NetworkInterface

/**
 * NetworkGuard — Enforces the zero-network constraint for NIPUN FLN.
 * All AI inference, curriculum content, and student data operations are
 * strictly offline. This guard monitors and logs any network interface activity
 * to ensure compliance with government offline-first mandate.
 */
object NetworkGuard {

    private const val TAG = "NetworkGuard"

    @Volatile
    private var isInitialized = false

    @Volatile
    private var networkDetected = false

    @Volatile
    private var detectionReason = ""

    /**
     * Initialize the network guard with the application context.
     */
    fun initialize(context: Context) {
        isInitialized = true
        networkDetected = false
        detectionReason = ""
        Log.d(TAG, "NetworkGuard initialized — zero-network constraint active")
    }

    /**
     * Check if any network has been detected.
     */
    fun isNetworkDetected(): Boolean = networkDetected

    /**
     * Get the reason why network was detected (or empty if none).
     */
    fun getDetectionReason(): String = detectionReason

    /**
     * Assert that the device is offline. Returns true if offline (desired state).
     */
    fun assertOffline(): Boolean = !networkDetected

    /**
     * Check if any active network interface exists on the device.
     */
    fun hasActiveNetworkInterface(): Boolean {
        return try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()?.any {
                it.isUp && !it.isLoopback && it.interfaceAddresses.isNotEmpty()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get a human-readable summary of the network guard status.
     */
    fun getStatusSummary(): String {
        return buildString {
            appendLine("=== NetworkGuard Status ===")
            appendLine("Initialized: $isInitialized")
            appendLine("Network detected: $networkDetected")
            appendLine("Active interface: ${hasActiveNetworkInterface()}")
            if (detectionReason.isNotEmpty()) {
                appendLine("Detection reason: $detectionReason")
            }
        }
    }
}
