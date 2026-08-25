package `in`.gov.tribalfln.startup

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import `in`.gov.tribalfln.data.NipunCurriculumDatabase

/**
 * AiEngineInitializer — App Startup initializer that performs deferred
 * initialization of the AI engine subsystems for sub-500ms cold start.
 * Loads curriculum database asynchronously during app startup.
 */
class AiEngineInitializer : Initializer<Unit> {

    companion object {
        private const val TAG = "AiEngineInitializer"
    }

    override fun create(context: Context) {
        Log.d(TAG, "Starting deferred AI engine initialization")
        try {
            // Pre-load the curriculum database for fast queries
            NipunCurriculumDatabase.getInstance(context)
            Log.d(TAG, "Curriculum database initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize curriculum database", e)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
