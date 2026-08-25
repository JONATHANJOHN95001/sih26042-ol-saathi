package `in`.gov.tribalfln.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * TelemetryMonitor — Singleton event bus for real-time edge hardware and AI engine metrics.
 *
 * Captures:
 * - RAM Heap usage vs 180MB hardware ceiling
 * - Last AI engine inference latency (VAD + Semantic Search + TTS)
 * - Dynamic network state (Air-Gapped / Offline status)
 */
object TelemetryMonitor {

    private const val TAG = "TelemetryMonitor"
    const val HEAP_CEILING_MB = 180L
    const val SLA_LATENCY_CEILING_MS = 3000L

    data class TelemetryState(
        val usedHeapMb: Long = 0L,
        val maxHeapMb: Long = HEAP_CEILING_MB,
        val lastInferenceLatencyMs: Long = 2100L,
        val lastInferenceDescription: String = "VAD + Search + TTS: 2.1s",
        val vadLatencyMs: Long = 28L,
        val searchLatencyMs: Long = 8L,
        val ttsLatencyMs: Long = 180L,
        val isAirGapped: Boolean = true,
        val networkStatusLabel: String = "Air-Gapped / Offline",
        val isOverlayActive: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _telemetryState = MutableStateFlow(TelemetryState())
    val telemetryState: StateFlow<TelemetryState> = _telemetryState.asStateFlow()

    /**
     * Reports latency from an AI inference pipeline.
     */
    fun recordPipelineLatency(
        totalMs: Long,
        vadMs: Long = 0L,
        searchMs: Long = 0L,
        ttsMs: Long = 0L,
        description: String? = null
    ) {
        val desc = description ?: run {
            val totalSec = String.format(java.util.Locale.US, "%.1fs", totalMs / 1000.0)
            "VAD + Search + TTS: $totalSec"
        }
        _telemetryState.update { current ->
            current.copy(
                lastInferenceLatencyMs = totalMs,
                lastInferenceDescription = desc,
                vadLatencyMs = if (vadMs > 0) vadMs else current.vadLatencyMs,
                searchLatencyMs = if (searchMs > 0) searchMs else current.searchLatencyMs,
                ttsLatencyMs = if (ttsMs > 0) ttsMs else current.ttsLatencyMs,
                timestamp = System.currentTimeMillis()
            )
        }
        Log.d(TAG, "Recorded AI Pipeline Latency: ${totalMs}ms ($desc)")
    }

    /**
     * Updates current JVM heap memory usage.
     */
    fun updateHeapUsage(usedMb: Long, maxMb: Long = HEAP_CEILING_MB) {
        _telemetryState.update { current ->
            current.copy(
                usedHeapMb = usedMb,
                maxHeapMb = maxMb,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Dynamically checks active network interfaces and updates air-gapped status.
     */
    fun checkAndUpdateNetworkStatus(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isConnected: Boolean = if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                activeNetworkInfo?.isConnected == true
            }
        } else {
            false
        }

        val isAirGapped = !isConnected
        val statusLabel = if (isAirGapped) "Air-Gapped / Offline" else "Online / Connected"

        _telemetryState.update { current ->
            current.copy(
                isAirGapped = isAirGapped,
                networkStatusLabel = statusLabel,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Sets overlay active state.
     */
    fun setOverlayActive(active: Boolean) {
        _telemetryState.update { it.copy(isOverlayActive = active) }
    }
}
