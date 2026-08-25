package `in`.gov.tribalfln

import android.content.Context
import android.util.Log
import android.speech.RecognitionListener


class VoiceEngineManager(
    private val context: Context,
    strictOfflineRecognition: Boolean = true,
    onStatus: (String) -> Unit = {},
    onMatch: (VoiceMatch) -> Unit = {}
) {
    data class VoiceMatch(val hindiPrompt: String, val santhaliOlChiki: String)
    fun initializeSpeechRecognizer() { Log.d("VoiceEngine", "Initializing recognizer") }
    fun startListening() { Log.d("VoiceEngine", "Listening") }
    fun stopListening() { Log.d("VoiceEngine", "Stopped listening") }
    fun release() { Log.d("VoiceEngine", "Released") }
}

