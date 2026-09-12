package app.olsaathi.mt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.File
import java.nio.LongBuffer

/**
 * Hindi to an Indic language with no network: AI4Bharat IndicTrans2
 * indic-indic-dist-320M, int8 ONNX, running on the tablet's CPU.
 *
 * This is what turns a sentence the pack has never seen into a translation
 * in airplane mode. It is still machine translation, and is labelled as such
 * on screen ("Machine translation, on this tablet"), kept apart from the pack
 * (which went through the build-time script guard) and from live Bhashini.
 *
 * The pipeline is the model's reference one, pared down to what the app
 * needs and checked against it in tools/ondevice_mt_probe.py:
 *   "hin_Deva <tgt> " + SentencePiece pieces -> ids (dict.SRC.json) + </s>
 *   -> encoder -> greedy decoder with a KV cache -> ids -> dict.TGT.json.
 *
 * About 330 MB of weights; loading takes a few seconds and some 500 MB of
 * RAM, so [OfflineTranslator] only offers it on tablets with enough memory
 * and loads it once, off the main thread.
 */
class OnDeviceTranslator private constructor(
    private val env: OrtEnvironment,
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    private val decoderWithPast: OrtSession,
    private val bpe: SentencePieceBpe,
    private val srcVocab: HashMap<String, Int>,
    private val tgtPieces: Array<String?>,
) : AutoCloseable {

    /**
     * Translate one Hindi sentence into [targetTag], an IndicTrans2 language
     * tag such as "sat_Olck". Returns null on any failure so the caller can
     * fall back to "Not in the offline pack" rather than show a broken line.
     */
    fun translate(hindi: String, targetTag: String, maxNewTokens: Int = 96): String? = try {
        translateOrThrow(hindi, targetTag, maxNewTokens)
    } catch (e: Exception) {
        Log.e(TAG, "On-device translation failed", e)
        null
    }

    private fun translateOrThrow(hindi: String, targetTag: String, maxNewTokens: Int): String? {
        val unk = srcVocab["<unk>"] ?: 3
        val tgtId = srcVocab[targetTag] ?: return null
        val pieces = bpe.encodeAsPieces(hindi)
        if (pieces.isEmpty()) return null
        val ids = LongArray(pieces.size + 3)
        ids[0] = (srcVocab[SRC_TAG] ?: return null).toLong()
        ids[1] = tgtId.toLong()
        pieces.forEachIndexed { i, p -> ids[i + 2] = (srcVocab[p] ?: unk).toLong() }
        ids[ids.size - 1] = EOS.toLong()
        val shape = longArrayOf(1, ids.size.toLong())
        val mask = LongArray(ids.size) { 1L }

        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { inputIds ->
        OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape).use { attn ->
            encoder.run(mapOf("input_ids" to inputIds, "attention_mask" to attn)).use { enc ->
                val hidden = enc[0] as OnnxTensor
                val out = ArrayList<Int>()
                var next = EOS // decoder_start_token_id is </s> for IndicTrans2
                var previous: OrtSession.Result? = null
                try {
                    for (step in 0 until maxNewTokens) {
                        val token = OnnxTensor.createTensor(
                            env, LongBuffer.wrap(longArrayOf(next.toLong())), longArrayOf(1, 1))
                        val result = token.use {
                            if (previous == null) {
                                decoder.run(mapOf(
                                    "input_ids" to token,
                                    "encoder_hidden_states" to hidden,
                                    "encoder_attention_mask" to attn,
                                ))
                            } else {
                                val feed = HashMap<String, OnnxTensor>(80)
                                feed["input_ids"] = token
                                feed["encoder_attention_mask"] = attn
                                for ((name, value) in previous!!) {
                                    if (name.startsWith("present.")) {
                                        feed["past_key_values." + name.removePrefix("present.")] =
                                            value as OnnxTensor
                                    }
                                }
                                decoderWithPast.run(feed)
                            }
                        }
                        // The cache in [previous] has been consumed; the new
                        // result carries the next one.
                        previous?.close()
                        previous = result

                        next = argmaxLast(result[0] as OnnxTensor)
                        if (next == EOS) break
                        out.add(next)
                    }
                } finally {
                    previous?.close()
                }
                return detokenize(out).ifBlank { null }
            }
        }
        }
    }

    private fun argmaxLast(logits: OnnxTensor): Int {
        val buf = logits.floatBuffer
        val vocab = logits.info.shape.last().toInt()
        val base = buf.limit() - vocab
        var best = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 0 until vocab) {
            val v = buf.get(base + i)
            if (v > bestScore) {
                bestScore = v
                best = i
            }
        }
        return best
    }

    private fun detokenize(ids: List<Int>): String {
        val sb = StringBuilder()
        for (id in ids) {
            val p = tgtPieces.getOrNull(id) ?: continue
            if (p.startsWith("<") && p.endsWith(">")) continue
            sb.append(p)
        }
        return sb.toString().replace('▁', ' ').trim()
            // Latin punctuation hugs the word before it; the danda-style
            // full stop keeps its space, as the pack writes it.
            .replace(SPACE_BEFORE_PUNCT, "$1")
    }

    override fun close() {
        encoder.close()
        decoder.close()
        decoderWithPast.close()
    }

    companion object {
        private const val TAG = "OnDeviceTranslator"
        private const val SRC_TAG = "hin_Deva"
        private const val EOS = 2
        private val SPACE_BEFORE_PUNCT = Regex(" ([,?!.;:])")

        /** The files the model folder must hold, and nothing else is read. */
        val REQUIRED_FILES = listOf(
            "encoder_model.onnx", "encoder_model.onnx.data",
            "decoder_model.onnx", "decoder_with_past_model.onnx", "decoder_shared.onnx.data",
            "model.SRC", "dict.SRC.json", "dict.TGT.json",
        )

        fun isInstalled(dir: File): Boolean =
            REQUIRED_FILES.all { File(dir, it).let { f -> f.isFile && f.length() > 0 } }

        /** Load from [dir]. Slow (seconds): never call on the main thread. */
        fun load(dir: File): OnDeviceTranslator {
            val started = System.currentTimeMillis()
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            // Loaded by path, not bytes: the .onnx files point at their
            // weights in the sibling .onnx.data files by relative name.
            val enc = env.createSession(File(dir, "encoder_model.onnx").path, opts)
            val dec = env.createSession(File(dir, "decoder_model.onnx").path, opts)
            val decPast = env.createSession(File(dir, "decoder_with_past_model.onnx").path, opts)
            val bpe = SentencePieceBpe.load(File(dir, "model.SRC"))
            val src = FlatJsonVocab.load(File(dir, "dict.SRC.json"))
            val tgt = FlatJsonVocab.load(File(dir, "dict.TGT.json"))
            val pieces = arrayOfNulls<String>((tgt.values.maxOrNull() ?: 0) + 1)
            for ((piece, id) in tgt) pieces[id] = piece
            Log.i(TAG, "Loaded in ${System.currentTimeMillis() - started} ms from $dir")
            return OnDeviceTranslator(env, enc, dec, decPast, bpe, src, pieces)
        }
    }
}
