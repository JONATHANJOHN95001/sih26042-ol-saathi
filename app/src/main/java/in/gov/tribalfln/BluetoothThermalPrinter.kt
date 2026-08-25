package `in`.gov.tribalfln

import android.graphics.Bitmap
import android.util.Log
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean


class BluetoothThermalPrinter {
    private val connected = AtomicBoolean(false)
    fun isConnected(): Boolean = connected.get()
    fun connect(address: String): Boolean { Log.d("ThermalPrinter", "Connecting to "); connected.set(true); return true }
    fun disconnect() { connected.set(false) }
    fun printBitmap(bitmap: Bitmap) { Log.d("ThermalPrinter", "Printing bitmap") }
    fun feedPaper(lines: Int) { Log.d("ThermalPrinter", "Feeding  lines") }
}

