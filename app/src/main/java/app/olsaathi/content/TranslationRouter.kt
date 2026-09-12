package app.olsaathi.content

import android.content.Context
import app.olsaathi.mt.OfflineTranslator
import app.olsaathi.net.BhashiniClient
import app.olsaathi.util.NetworkGuard
import java.util.concurrent.Executors

/**
 * Decides where a translation comes from: the shipped pack, or Bhashini.
 *
 * ## The policy, and why it is this way round
 *
 * The pack is always tried first, and a pack hit never touches the network.
 * That is not a performance choice. Pack strings have been through the
 * per-script contamination guard at build time, which caught Arabic letters
 * inside Santali and a whole language returned in the wrong script. A live
 * response has had none of that checking. The verified answer is the better
 * answer even when a faster one is available.
 *
 * Bhashini is therefore only asked about the sentences the pack cannot answer,
 * which today return "Not in the offline pack". Online turns those misses into
 * real translations; offline they stay misses, exactly as before.
 *
 * ## What this means for the offline claim
 *
 * In airplane mode, with no key, or on any lesson made of shipped phrases,
 * this class makes zero network calls and [NetworkGuard.callCount] stays at
 * zero. The offline deliverable is demonstrated the same way it always was.
 * When a call does happen it is counted and named on the Check and Proof
 * screen, so the two modes are told apart on screen rather than assumed.
 */
class TranslationRouter(
    private val context: Context,
    private val pack: VerifiedContentPack,
    private val bhashini: BhashiniClient = BhashiniClient(),
) {

    /** One thread: translations are user-driven and must not race each other. */
    private val io = Executors.newSingleThreadExecutor()

    enum class Mode {
        /** No key compiled in. Behaves exactly as the app did before. */
        OFFLINE_ONLY,

        /** Key present, but no usable network right now. */
        OFFLINE_NO_NETWORK,

        /** Key present and a network is available. Misses will be looked up. */
        ONLINE_AVAILABLE,
    }

    fun mode(): Mode = mode(NetworkGuard.isOnline(context))

    /**
     * The mode for a connectivity value the caller already has.
     *
     * A network callback learns the network has gone before a fresh
     * connectivity read necessarily does, so a screen redrawing on that
     * callback should route on what the callback said rather than re-asking.
     */
    fun mode(online: Boolean): Mode = when {
        !bhashini.isConfigured -> Mode.OFFLINE_ONLY
        !online -> Mode.OFFLINE_NO_NETWORK
        else -> Mode.ONLINE_AVAILABLE
    }

    /**
     * Translate, calling [onResult] on a background thread.
     *
     * A pack hit calls back immediately and synchronously, so the common case
     * keeps the near-zero latency the lookup already had. Only a miss goes
     * near the executor, and only then if there is somewhere to ask.
     */
    fun translate(hindi: String, onResult: (Translation) -> Unit) {
        val local = pack.lookup(hindi)
        if (local.provenance != Provenance.UNAVAILABLE) {
            onResult(local)
            return
        }
        val code = pack.languageCode.ifEmpty { "sat" }
        if (mode() != Mode.ONLINE_AVAILABLE) {
            // No network (or no key): the on-device model, when this tablet
            // has it, answers the sentence the pack cannot. The miss is shown
            // first so the screen is never blank while it works.
            onResult(local)
            translateOnDevice(hindi, code, local, onResult)
            return
        }
        // Hand back the offline miss first so the screen is never blank while
        // the network is being waited on, then correct it if an answer comes.
        onResult(local)
        io.execute {
            val target = bhashini.translate(hindi, code)
            if (!target.isNullOrBlank()) {
                onResult(
                    local.copy(
                        target = target,
                        provenance = Provenance.ONLINE_MACHINE,
                        serviceName = "Bhashini",
                    )
                )
            } else {
                // Bhashini down or the network gone mid-call: fall back to
                // the tablet rather than leave the miss on screen.
                translateOnDevice(hindi, code, local, onResult)
            }
        }
    }

    private fun translateOnDevice(
        hindi: String, code: String, local: Translation, onResult: (Translation) -> Unit,
    ) {
        if (!OfflineTranslator.available(context, code)) return
        OfflineTranslator.translate(context, hindi, code) { target ->
            if (!target.isNullOrBlank()) {
                onResult(
                    local.copy(
                        target = target,
                        provenance = Provenance.ON_DEVICE_MACHINE,
                        serviceName = "IndicTrans2",
                    )
                )
            }
        }
    }

    /**
     * Synthesise [text] and hand the WAV bytes back on a background thread.
     *
     * Called for a line that came back as [Provenance.ONLINE_MACHINE], and for
     * a pack line nothing else can speak (every Santali line today: the pack
     * has no Santali audio and Android has no Santali voice). It checks the
     * same conditions itself: a key and a network, or null. It runs on the
     * same single executor as [translate], which means
     * a synthesis request queues behind the translation that produced it
     * rather than racing it.
     *
     * [onAudio] is called with null on any failure, including "not configured"
     * and "offline". The caller shows the text with the play button disabled,
     * which is the pre-existing behaviour and an honest one: the translation is
     * real even when the voice for it is not available.
     */
    fun synthesise(text: String, onAudio: (ByteArray?) -> Unit) {
        if (text.isBlank() || mode() != Mode.ONLINE_AVAILABLE) {
            onAudio(null)
            return
        }
        io.execute {
            onAudio(bhashini.synthesise(text, pack.languageCode.ifEmpty { "sat" }))
        }
    }

    fun shutdown() = io.shutdownNow()
}
