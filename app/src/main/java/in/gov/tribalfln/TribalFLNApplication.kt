package `in`.gov.tribalfln

import android.app.Application
import android.util.Log
import `in`.gov.tribalfln.data.StudentProgressDatabase
import `in`.gov.tribalfln.data.LocalVectorDatabase
import `in`.gov.tribalfln.engine.materials.ThermalStateManager
import `in`.gov.tribalfln.engine.materials.TribalLanguageRegistry
import `in`.gov.tribalfln.util.BitmapPoolManager
import `in`.gov.tribalfln.util.NetworkGuard
import ai.onnxruntime.OrtEnvironment
import kotlinx.coroutines.*
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


class TribalFLNApplication : Application() {
    companion object {
        private const val TAG = "TribalFLNApp"
        const val HEAP_CEILING_BYTES = 180L * 1024 * 1024
        const val HEAP_CEILING_MB = 180L
        private const val GC_TRIGGER_THRESHOLD = 0.80
        private val initScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        @Volatile var ortEnvironment: OrtEnvironment? = null; private set
        @Volatile var studentProgressDatabase: StudentProgressDatabase? = null; private set
        @Volatile var localVectorDatabase: LocalVectorDatabase? = null; private set
        @Volatile var instance: TribalFLNApplication? = null; private set
        @Volatile var thermalStateManager: ThermalStateManager? = null; private set
        fun getCurrentHeapMB(): Long = (Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }) / (1024 * 1024)
        fun isHeapWithinCeiling(): Boolean = getCurrentHeapMB() <= HEAP_CEILING_MB
        fun enforceHeapCeiling(): Boolean {
            val usedBytes = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
            if (usedBytes > HEAP_CEILING_BYTES * GC_TRIGGER_THRESHOLD) { System.gc(); return true }; return false
        }
        fun oomEmergencyRecovery(): Long { val b = getCurrentHeapMB(); System.gc(); System.gc(); return b - getCurrentHeapMB() }
    }
    private val isInitialized = AtomicBoolean(false)
    override fun onCreate() {
        super.onCreate(); instance = this
        NetworkGuard.initialize(this)
        initScope.launch {
            ortEnvironment = OrtEnvironment.getEnvironment()
            studentProgressDatabase = StudentProgressDatabase.getInstance(applicationContext)
            localVectorDatabase = LocalVectorDatabase(applicationContext)
            isInitialized.set(true)
            Log.d(TAG, "Core subsystems initialized")
        }
    }
    override fun onLowMemory() { super.onLowMemory(); System.gc() }
    fun isReady(): Boolean = isInitialized.get()
    fun getThermalStateManager(): ThermalStateManager { if (thermalStateManager == null) thermalStateManager = ThermalStateManager(this); return thermalStateManager!! }
}

