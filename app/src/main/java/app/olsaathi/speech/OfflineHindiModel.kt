package app.olsaathi.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Makes sure the tablet can recognise Hindi with no network.
 *
 * Android's recogniser only works offline if a Hindi model is on the device,
 * and none ships by default: on a Galaxy Tab A8 (Android 14) hold-to-speak in
 * airplane mode failed with "Failed to get language pack of required locale:
 * error 13". From Android 13 an app can ask the on-device recogniser whether
 * it has Hindi and, if it can get it, tell it to download it. This does that
 * while the tablet is online, which is the "initial content sync" the problem
 * statement allows, so the classroom later works in airplane mode.
 *
 * Below Android 13 there is no such API; the status says so, and the teacher
 * is pointed at the system setting instead.
 */
object OfflineHindiModel {

    enum class Status {
        /** Hindi is on the device; hold-to-speak works offline. */
        INSTALLED,
        /** A download was already in progress. */
        PENDING,
        /** This call asked Android to download it. */
        DOWNLOAD_REQUESTED,
        /** Android can get it but it is not here, and no download was asked for. */
        MISSING,
        /** The on-device recogniser does not offer Hindi at all. */
        UNSUPPORTED,
        /** No on-device recogniser, or Android older than 13. */
        UNAVAILABLE,
        ERROR,
    }

    private const val TAG = "OfflineHindiModel"
    private const val LANG = "hi-IN"

    /**
     * Check, and download if needed. [onStatus] runs on the main thread.
     * Must be called on the main thread, as SpeechRecognizer requires.
     */
    fun ensure(
        context: Context,
        download: Boolean = true,
        onProgress: ((Int) -> Unit)? = null,
        onDownloaded: ((Boolean) -> Unit)? = null,
        onStatus: (Status) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < 33 ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onStatus(Status.UNAVAILABLE)
            return
        }
        check33(context.applicationContext, download, onProgress, onDownloaded, onStatus)
    }

    @RequiresApi(33)
    private fun check33(
        context: Context,
        download: Boolean,
        onProgress: ((Int) -> Unit)?,
        onDownloaded: ((Boolean) -> Unit)?,
        onStatus: (Status) -> Unit,
    ) {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANG)
        }
        recognizer.checkRecognitionSupport(intent, context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    fun has(list: List<String>) = list.any { it.startsWith("hi", ignoreCase = true) }
                    Log.i(TAG, "installed=" + support.installedOnDeviceLanguages +
                        " pending=" + support.pendingOnDeviceLanguages +
                        " supported=" + support.supportedOnDeviceLanguages)
                    val status = when {
                        has(support.installedOnDeviceLanguages) -> Status.INSTALLED
                        has(support.pendingOnDeviceLanguages) -> Status.PENDING
                        has(support.supportedOnDeviceLanguages) ->
                            if (download) Status.DOWNLOAD_REQUESTED else Status.MISSING
                        else -> Status.UNSUPPORTED
                    }
                    Log.i(TAG, "Offline Hindi: $status")
                    if (status == Status.DOWNLOAD_REQUESTED) {
                        // The recogniser must stay alive until the service has
                        // taken the request: destroying it straight after
                        // triggerModelDownload cancelled the download on the
                        // Tab A8 (the pending list stayed empty).
                        download(context, recognizer, intent, onProgress, onDownloaded)
                    } else {
                        recognizer.destroy()
                    }
                    onStatus(status)
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "checkRecognitionSupport failed: $error")
                    recognizer.destroy()
                    onStatus(Status.ERROR)
                }
            })
    }

    /**
     * Ask for the Hindi model. Android 14 reports progress through a
     * listener, so the recogniser is released when the download ends; on 13
     * there is no callback, so it is held for a minute and then released.
     */
    @RequiresApi(33)
    private fun download(
        context: Context,
        recognizer: SpeechRecognizer,
        intent: Intent,
        onProgress: ((Int) -> Unit)?,
        onDownloaded: ((Boolean) -> Unit)?,
    ) {
        if (Build.VERSION.SDK_INT >= 34) {
            recognizer.triggerModelDownload(intent, context.mainExecutor,
                object : android.speech.ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) {
                        Log.i(TAG, "Hindi model download: $completedPercent%")
                        onProgress?.invoke(completedPercent)
                    }
                    override fun onSuccess() {
                        Log.i(TAG, "Hindi model downloaded")
                        recognizer.destroy()
                        onDownloaded?.invoke(true)
                    }
                    override fun onScheduled() {
                        // Android queued it for later (for example, until the
                        // tablet is on Wi-Fi); it will finish on its own.
                        Log.i(TAG, "Hindi model download scheduled")
                        recognizer.destroy()
                        onDownloaded?.invoke(false)
                    }
                    override fun onError(error: Int) {
                        Log.w(TAG, "Hindi model download failed: $error")
                        recognizer.destroy()
                        onDownloaded?.invoke(false)
                    }
                })
        } else {
            recognizer.triggerModelDownload(intent)
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ recognizer.destroy() }, 60_000)
        }
    }
}
