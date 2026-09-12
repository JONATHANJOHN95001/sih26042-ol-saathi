package app.olsaathi.mt

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import app.olsaathi.util.NetworkGuard
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * App-wide owner of the on-device translation model: where it lives, whether
 * this tablet can run it, loading it once, and fetching it once.
 *
 * The model is not in the APK (it is about 330 MB). It is downloaded once,
 * during setup while the tablet has a network, from a pinned revision of the
 * public int8 ONNX export of AI4Bharat's IndicTrans2 indic-indic-dist-320M,
 * into the app's own storage. After that it works in airplane mode.
 */
object OfflineTranslator {

    private const val TAG = "OfflineTranslator"

    /** Pinned so the files cannot change under a tablet that already has them. */
    private const val REVISION = "b04956dee2f2a3e06e44bf09f8d654a6b81af99a"
    private const val REPO = "hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8"

    /** Rough total, for the "download 330 MB?" question. */
    const val DOWNLOAD_MB = 340

    /** The model needs several hundred MB while it runs, so a 2 GB tablet (the
     *  problem statement's floor) keeps pack + online; 2.4 GB and up get it. */
    private const val MIN_TOTAL_RAM = 2_400L * 1024 * 1024

    /** Pack code -> IndicTrans2 tag. Manipuri is left out: the model writes
     *  Meetei Mayek and the pack is in Bengali script, so they would disagree. */
    private val TAGS = mapOf(
        "sat" to "sat_Olck", "asm" to "asm_Beng", "ben" to "ben_Beng",
        "brx" to "brx_Deva", "doi" to "doi_Deva", "gom" to "gom_Deva",
        "guj" to "guj_Gujr", "kan" to "kan_Knda", "mai" to "mai_Deva",
        "mal" to "mal_Mlym", "mar" to "mar_Deva", "npi" to "npi_Deva",
        "ory" to "ory_Orya", "pan" to "pan_Guru", "tam" to "tam_Taml",
        "tel" to "tel_Telu",
    )

    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var loaded: OnDeviceTranslator? = null
    @Volatile private var loadFailed = false

    fun modelDir(context: Context): File =
        File(context.filesDir, "mt/it2-indic-indic-320M-int8")

    fun isInstalled(context: Context) = OnDeviceTranslator.isInstalled(modelDir(context))

    fun supports(languageCode: String) = languageCode in TAGS

    /** Enough memory to run it at all. A 2 GB tablet keeps pack + online. */
    fun deviceCanRun(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return info.totalMem >= MIN_TOTAL_RAM
    }

    /** Ready to translate right now, without waiting for a load. */
    val isLoaded: Boolean get() = loaded != null

    /** Worth trying for this language on this tablet. */
    fun available(context: Context, languageCode: String) =
        supports(languageCode) && !loadFailed && deviceCanRun(context) && isInstalled(context)

    /** Start loading in the background, so the first sentence does not wait. */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        Log.i(TAG, "warmUp: dir=${modelDir(app)} installed=${isInstalled(app)} " +
            "canRun=${deviceCanRun(app)} loaded=${loaded != null} failed=$loadFailed")
        if (loaded != null || loadFailed || !deviceCanRun(app) || !isInstalled(app)) return
        worker.execute { ensureLoaded(app) }
    }

    /**
     * Translate on the worker thread and call back there. [onResult] gets null
     * when the model is missing, the language is not covered, or it fails.
     */
    fun translate(context: Context, hindi: String, languageCode: String, onResult: (String?) -> Unit) {
        val app = context.applicationContext
        val tag = TAGS[languageCode]
        if (tag == null || !available(app, languageCode)) {
            onResult(null)
            return
        }
        worker.execute {
            val mt = ensureLoaded(app)
            val started = System.currentTimeMillis()
            val raw = mt?.translate(hindi, tag)
            Log.i(TAG, "Translated in ${System.currentTimeMillis() - started} ms")
            onResult(raw?.let { BrahmicScript.fromDevanagari(it, tag.substringAfter('_')) })
        }
    }

    private fun ensureLoaded(context: Context): OnDeviceTranslator? {
        loaded?.let { return it }
        return try {
            OnDeviceTranslator.load(modelDir(context)).also { loaded = it }
        } catch (t: Throwable) {
            // Out of memory or a corrupt file: stop offering it this session
            // rather than failing on every sentence.
            Log.e(TAG, "Could not load the on-device model", t)
            loadFailed = true
            null
        }
    }

    /**
     * Fetch the model once. Runs on the worker thread; [onProgress] gets
     * 0..100 and [onDone] true on success. Each file goes to a .part file
     * and is renamed only when complete, so an interrupted download is never
     * mistaken for an installed model. Counted by NetworkGuard like every
     * other network call, so the Check and Proof screen shows it.
     */
    fun download(context: Context, onProgress: (Int) -> Unit, onDone: (Boolean) -> Unit) {
        val dir = modelDir(context.applicationContext)
        worker.execute {
            try {
                dir.mkdirs()
                val files = OnDeviceTranslator.REQUIRED_FILES
                val sizes = files.map { name -> name to headSize(name) }
                val total = sizes.sumOf { it.second }.coerceAtLeast(1)
                var doneBytes = 0L
                for ((name, size) in sizes) {
                    val target = File(dir, name)
                    if (target.isFile && target.length() == size) {
                        doneBytes += size
                        continue
                    }
                    val part = File(dir, "$name.part")
                    NetworkGuard.recordNetworkCall()
                    val conn = open(name)
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(1 shl 16)
                            var lastPct = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                doneBytes += n
                                val pct = (doneBytes * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                            }
                        }
                    }
                    if (size > 0 && part.length() != size) {
                        throw IllegalStateException("$name: got ${part.length()} of $size bytes")
                    }
                    target.delete()
                    if (!part.renameTo(target)) throw IllegalStateException("Could not save $name")
                }
                loadFailed = false
                onDone(isInstalled(context))
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                onDone(false)
            }
        }
    }

    private fun url(name: String) = URL("https://huggingface.co/$REPO/resolve/$REVISION/$name")

    private fun open(name: String): HttpURLConnection =
        (url(name).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }

    private fun headSize(name: String): Long {
        NetworkGuard.recordNetworkCall()
        val conn = open(name).apply { requestMethod = "HEAD" }
        return try {
            conn.contentLengthLong.coerceAtLeast(0)
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * IndicTrans2 writes every Brahmic target in Devanagari internally; the
 * reference post-processor maps it back by Unicode block offset, which is
 * what this does (checked: Tamil, Bengali and Gujarati come out right).
 * Ol Chiki and Devanagari targets pass through unchanged.
 */
object BrahmicScript {
    private val BLOCKS = mapOf(
        "Beng" to 0x0980, "Guru" to 0x0A00, "Gujr" to 0x0A80, "Orya" to 0x0B00,
        "Taml" to 0x0B80, "Telu" to 0x0C00, "Knda" to 0x0C80, "Mlym" to 0x0D00,
    )

    fun fromDevanagari(text: String, script: String): String {
        val base = BLOCKS[script] ?: return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val c = ch.code
            if (c in 0x0900..0x097F && c != 0x0964 && c != 0x0965) {
                val mapped = c - 0x0900 + base
                if (Character.isDefined(mapped)) {
                    sb.append(mapped.toChar())
                    continue
                }
            }
            sb.append(ch)
        }
        return sb.toString()
    }
}
