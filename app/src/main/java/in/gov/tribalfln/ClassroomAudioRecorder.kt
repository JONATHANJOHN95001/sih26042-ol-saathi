package `in`.gov.tribalfln

import android.media.AudioRecord
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean


class ClassroomAudioRecorder {
    fun start() { Log.d("AudioRecorder", "Started") }
    fun stop() { Log.d("AudioRecorder", "Stopped") }
    fun release() { Log.d("AudioRecorder", "Released") }
}

