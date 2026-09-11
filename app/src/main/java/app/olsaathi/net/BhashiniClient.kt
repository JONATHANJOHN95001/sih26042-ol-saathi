package app.olsaathi.net

import android.util.Log
import app.olsaathi.BuildConfig
import app.olsaathi.audio.WavPcm
import app.olsaathi.text.OlChikiBridge
import app.olsaathi.util.NetworkGuard
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Live Bhashini translation and speech, used only when the offline pack has
 * no answer.
 *
 * This is the one place in the app that touches the network, and it is
 * deliberately small. It never replaces a pack hit: the pack has been through
 * the per-script contamination guard and a live response has not, so a pack
 * answer is always the better answer.
 *
 * ## The wire format, verified 10 Sep 2026 against a real credential
 *
 * The Bhashini dashboard issues a user id, an Udyat key and an inference key.
 * The first two exist only to call the ULCA handshake, which answers with a
 * compute endpoint, the header to authenticate with, and a service id per
 * task. Probing it established that the endpoint is fixed, the header is
 * Authorization, and the key it hands back is byte for byte the dashboard's
 * inference key. So this client skips the handshake and calls the compute
 * endpoint directly: one round trip instead of two, and neither the user id
 * nor the Udyat key ever needs to be inside the APK.
 *
 * The earlier version of this file guessed at that shape and got two things
 * wrong that would have failed every call: it sent no serviceId, which the
 * compute endpoint requires, and it passed the pack's three-letter language
 * codes, where Bhashini wants "ta", not "tam".
 *
 * Measured from a laptop on home broadband: translation alone about 100 ms,
 * speech 230 to 730 ms, and both chained in one request a median of 308 ms
 * over five runs.
 *
 * ## Credentials
 *
 * The inference key comes from local.properties, which is gitignored, through
 * BuildConfig, and a build with -PofflineOnly leaves it out entirely. **When
 * it is blank the client reports itself unconfigured and no socket is ever
 * opened**, which is what the zero-network claim on Check and Proof rests on.
 */
class BhashiniClient(
    private val apiKey: String = BuildConfig.BHASHINI_API_KEY,
    private val endpoint: String = BuildConfig.BHASHINI_ENDPOINT,
) {

    /** True when there is enough configuration to be worth trying. */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && endpoint.isNotBlank()

    /**
     * Translate one Hindi sentence into the pack language [packLanguageCode].
     *
     * Blocking. Call it off the main thread. Returns null on any failure at
     * all, including a language Bhashini was not confirmed to serve, because a
     * failure here must degrade to the offline behaviour rather than surface
     * an error to a teacher mid-lesson.
     */
    fun translate(hindi: String, packLanguageCode: String): String? {
        if (!isConfigured || hindi.isBlank()) return null
        val target = bhashiniCode(packLanguageCode) ?: return null
        val body = pipeline(
            task("translation", language("hi", target), TRANSLATION_SERVICE),
            input = hindi,
        )
        val response = post(body, readTimeoutSeconds = 5) ?: return null
        return parseTranslation(response)
    }

    /**
     * Speak [text] in the pack language [packLanguageCode] and return 16-bit
     * PCM WAV bytes, or null.
     *
     * Null for any failure, and also for a language with no confirmed speech
     * model, so the caller shows the text instead of inventing a voice.
     *
     * Santali is the special case. Its only voice, IIT Madras, returns 0.02 s
     * of silence with a success response for Ol Chiki input and real speech
     * for the same words in Devanagari (verified 10 Sep 2026). So Ol Chiki is
     * sent across [OlChikiBridge] first. The text on screen stays Ol Chiki.
     */
    fun synthesise(text: String, packLanguageCode: String): ByteArray? {
        if (!isConfigured || text.isBlank()) return null
        val code = bhashiniCode(packLanguageCode) ?: return null
        val service = ttsServiceFor(code) ?: return null
        val spoken = if (code == "sat" && OlChikiBridge.containsOlChiki(text))
            OlChikiBridge.toDevanagari(text) else text
        val speak = task("tts", language(code), service)
        speak.getJSONObject("config").apply {
            put("gender", "female")
            // The two fields the official SDK sends to this model, copied
            // rather than guessed, since a rejected field fails silently.
            if (service == IITM_TTS) {
                put("audioFormat", "wav")
                put("samplingRate", 16000)
            }
        }
        // Longer read timeout than translation: the payload is audio, not a
        // sentence. Still bounded, because an unbounded wait is not a feature
        // for a teacher standing in front of a class.
        val response = post(pipeline(speak, input = spoken), readTimeoutSeconds = 8) ?: return null
        val wav = parseAudio(response) ?: return null
        return WavPcm.toPcm16(wav)
    }

    /**
     * Hindi speech to Hindi text through Bhashini's ASR.
     *
     * [wav] is 16-bit mono PCM WAV at 16 kHz, which is what the model was
     * probed with (10 Sep 2026: 0.26 s for a short sentence). This exists
     * because Android's recogniser cannot be relied on for Hindi: on a
     * OnePlus Nord 5 the only general recogniser was Google's on-device
     * service, which answers "language not supported" without the offline
     * Hindi pack, and there was no online recogniser to fall back to.
     *
     * Blocking; call off the main thread. Null on any failure.
     */
    fun transcribe(wav: ByteArray): String? {
        if (!isConfigured || wav.isEmpty()) return null
        val asr = task("asr", language("hi"), ASR_SERVICE)
        asr.getJSONObject("config").apply {
            put("audioFormat", "wav")
            put("samplingRate", 16000)
        }
        val body = JSONObject().apply {
            put("pipelineTasks", JSONArray().put(asr))
            put("inputData", JSONObject().put("audio", JSONArray().put(
                JSONObject().put("audioContent", Base64.getEncoder().encodeToString(wav))
            )))
        }
        val response = post(body, readTimeoutSeconds = 10) ?: return null
        return try {
            JSONObject(response)
                .optJSONArray("pipelineResponse")
                ?.optJSONObject(0)
                ?.optJSONArray("output")
                ?.optJSONObject(0)
                ?.optString("source")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Bhashini ASR response not understood: ${e.message}")
            null
        }
    }

    private fun post(body: JSONObject, readTimeoutSeconds: Long): String? {
        NetworkGuard.recordNetworkCall()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TimeUnit.SECONDS.toMillis(3).toInt()
                readTimeout = TimeUnit.SECONDS.toMillis(readTimeoutSeconds).toInt()
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", apiKey)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "Bhashini HTTP ${conn.responseCode}")
                return null
            }
            conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: Exception) {
            // Deliberately broad. Any network fault whatsoever means fall back
            // to the pack, and there is nothing a teacher could do with the
            // difference between a DNS failure and a socket timeout.
            Log.w(TAG, "Bhashini call failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun language(source: String, target: String? = null) = JSONObject().apply {
        put("sourceLanguage", source)
        if (target != null) put("targetLanguage", target)
    }

    private fun task(type: String, language: JSONObject, serviceId: String) = JSONObject().apply {
        put("taskType", type)
        put("config", JSONObject().apply {
            put("language", language)
            put("serviceId", serviceId)
        })
    }

    private fun pipeline(vararg tasks: JSONObject, input: String) = JSONObject().apply {
        put("pipelineTasks", JSONArray().apply { tasks.forEach { put(it) } })
        put("inputData", JSONObject().put("input", JSONArray().put(JSONObject().put("source", input))))
    }

    private fun parseTranslation(body: String): String? = try {
        JSONObject(body)
            .optJSONArray("pipelineResponse")
            ?.optJSONObject(0)
            ?.optJSONArray("output")
            ?.optJSONObject(0)
            ?.optString("target")
            ?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.w(TAG, "Bhashini response not understood: ${e.message}")
        null
    }

    /**
     * Pull the base64 audio out of the response. Tolerant of shape by design:
     * an unexpected body returns null, and a wrong guess here must never
     * become noise played to a class.
     */
    private fun parseAudio(body: String): ByteArray? = try {
        val b64 = JSONObject(body)
            .optJSONArray("pipelineResponse")
            ?.optJSONObject(0)
            ?.optJSONArray("audio")
            ?.optJSONObject(0)
            ?.optString("audioContent")
        if (b64.isNullOrBlank()) null else Base64.getDecoder().decode(b64)
    } catch (e: Exception) {
        Log.w(TAG, "Bhashini speech response not understood: ${e.message}")
        null
    }

    companion object {
        private const val TAG = "BhashiniClient"

        /** One model serves Hindi to every language the app ships (probed 10 Sep 2026). */
        const val TRANSLATION_SERVICE = "ai4bharat/indictrans-v2-all-gpu--t4"

        /** Hindi speech recognition, confirmed with a real recording on 10 Sep 2026. */
        const val ASR_SERVICE = "ai4bharat/conformer-hi-gpu--t4"

        /**
         * Pack code to Bhashini code. Every entry was confirmed by asking the
         * handshake for a Hindi-to-X translation model and getting one back,
         * rather than copied from a language table.
         */
        private val CODES = mapOf(
            "asm" to "as", "ben" to "bn", "brx" to "brx", "doi" to "doi",
            "gom" to "gom", "guj" to "gu", "kan" to "kn", "mai" to "mai",
            "mal" to "ml", "mar" to "mr", "mni" to "mni", "npi" to "ne",
            "ory" to "or", "pan" to "pa", "sat" to "sat", "tam" to "ta",
            "tel" to "te",
        )

        /**
         * IIT Madras's multilingual voice. The only Santali speech on Bhashini:
         * it is on pipeline 660fa5bec7fb5b0328229016, not the MeitY pipeline,
         * which is why the MeitY handshake said Santali had none.
         */
        const val IITM_TTS = "Bhashini/IITM/TTS"

        /**
         * Speech models, only where a real synthesis request returned real
         * audio. The others are absent until someone confirms them the same
         * way, since an unconfirmed service id fails every call silently.
         */
        private val TTS = mapOf(
            // Confirmed 10 Sep 2026: hi->sat translation, bridged to
            // Devanagari, spoke 1.24 s of real speech. About 3 s per call.
            "sat" to IITM_TTS,
            "ta" to "ai4bharat/indic-tts-coqui-dravidian-gpu--t4",
            "ml" to "ai4bharat/indic-tts-coqui-dravidian-gpu--t4",
            "te" to "ai4bharat/indic-tts-coqui-dravidian-gpu--t4",
            "bn" to "ai4bharat/indic-tts-coqui-indo_aryan-gpu--t4",
            // Bodo is the one tribal language in the packs with a Bhashini
            // voice, confirmed with a real synthesis on 10 Sep 2026.
            "brx" to "ai4bharat/indic-tts-coqui-misc-gpu--t4",
        )

        /** The code Bhashini expects for a pack language, or null if unconfirmed. */
        fun bhashiniCode(packCode: String): String? = CODES[packCode.lowercase()]

        /** The confirmed speech model for a Bhashini code, or null. */
        fun ttsServiceFor(bhashiniCode: String): String? = TTS[bhashiniCode]
    }
}
