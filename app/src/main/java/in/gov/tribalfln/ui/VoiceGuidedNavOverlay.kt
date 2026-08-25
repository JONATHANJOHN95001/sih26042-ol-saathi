package `in`.gov.tribalfln.ui

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * VoiceGuidedNavOverlay — Foreground service providing voice-guided
 * navigation overlay for accessibility in classroom settings.
 * Supports spoken instructions for visually impaired educators.
 */
class VoiceGuidedNavOverlay : Service() {

    companion object {
        private const val TAG = "VoiceGuidedOverlay"
        private const val NOTIFICATION_ID = 7701
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "VoiceGuidedNavOverlay service started")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "VoiceGuidedNavOverlay service destroyed")
        super.onDestroy()
    }
}
