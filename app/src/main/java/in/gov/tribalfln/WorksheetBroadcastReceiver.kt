package `in`.gov.tribalfln

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log


class WorksheetBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("WorksheetBroadcast", "Received: ${intent?.action}")
    }
}

