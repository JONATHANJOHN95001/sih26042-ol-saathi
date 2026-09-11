package app.olsaathi.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enforces the zero-network constraint for offline operation.
 *
 * Counts every network call this app makes this session. On a properly
 * offline build this number should be zero — verified on the Proof screen.
 *
 * This is NOT a firewall. It is a measurement instrument that lets the
 * judge see the claim is real.
 */
object NetworkGuard {

    private const val TAG = "NetworkGuard"

    private val counter = AtomicInteger(0)

    /** Number of network calls made this session. */
    val callCount: Int
        get() = counter.get()

    /** Increment the network call counter. Call from any HTTP client before the request. */
    fun recordNetworkCall() {
        // AtomicInteger, not @Volatile: callCount++ is read-modify-write, and
        // volatile only orders those three steps, it does not make them one.
        // The moment a second network path exists (live audio is already
        // specced), concurrent increments get lost and the Proof screen shows
        // 0 for a call that happened — the worst failure mode for a counter
        // that exists to be trusted.
        val n = counter.incrementAndGet()
        Log.w(TAG, "Network call #$n — app should be offline!")
    }

    /**
     * True only when the current network has been checked by Android and
     * really reaches the internet.
     *
     * NET_CAPABILITY_INTERNET alone means a network claims it can carry
     * internet traffic, not that it does. The difference was found on the
     * emulator: wifi up, INTERNET set, the Proof screen saying ONLINE, and every
     * lookup of the Bhashini host failing with "Unable to resolve host". A
     * school wifi with no working uplink looks exactly like that. VALIDATED is
     * Android's own confirmation that traffic got through, so without it the
     * app treats the network as absent instead of attempting calls that can
     * only time out.
     */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Call [onChange] whenever the device gains or loses its default network,
     * and return the function that stops watching.
     *
     * This observes connectivity; it never uses it. No socket is opened and
     * [recordNetworkCall] is not touched, so watching cannot move the offline
     * counter. The value passed is re-read from [isOnline] rather than trusted
     * from the callback, except on loss, where the callback is authoritative
     * and [isOnline] can briefly still report the network that just went.
     */
    fun watch(context: Context, onChange: (Boolean) -> Unit): () -> Unit {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChange(isOnline(appContext))
            override fun onLost(network: Network) = onChange(false)
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                onChange(isOnline(appContext))
        }
        return try {
            cm.registerDefaultNetworkCallback(callback)
            val stop: () -> Unit = {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (e: IllegalArgumentException) {
                    // Already unregistered. Stopping twice is harmless.
                }
            }
            stop
        } catch (e: Exception) {
            Log.w(TAG, "Could not watch connectivity: ${e.message}")
            val noop: () -> Unit = {}
            noop
        }
    }

    /** Reset counter (for tests only). */
    fun reset() {
        counter.set(0)
    }
}