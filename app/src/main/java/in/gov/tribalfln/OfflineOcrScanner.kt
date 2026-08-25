package `in`.gov.tribalfln

import android.content.Context
import android.graphics.Bitmap
import android.util.Log


class OfflineOcrScanner(private val context: Context) {
    fun initialize() { Log.d("OcrScanner", "Initialized") }
    fun scanText(bitmap: Bitmap): String { return "" }
    fun close() { Log.d("OcrScanner", "Closed") }
}

