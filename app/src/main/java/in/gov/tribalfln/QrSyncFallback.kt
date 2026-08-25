package `in`.gov.tribalfln

import android.content.Context
import android.util.Log
import java.io.File


class QrSyncFallback(private val context: Context) {
    fun generateSyncQr(data: File): Any? { Log.d("QrSync", "Generating QR for ${data.name}"); return null }
    fun scanSyncQr(): File? { Log.d("QrSync", "Scanning QR"); return null }
}

