package app.olsaathi.mt

import java.io.File
import java.text.Normalizer

/**
 * The SentencePiece BPE encoder IndicTrans2 was trained with, in plain Kotlin.
 *
 * The on-device model needs its input cut into exactly the pieces the Python
 * tokenizer would produce, or it translates garbage. The native SentencePiece
 * library would add another native dependency for one function, so this reads
 * the `.model` file (a protobuf) directly and runs the same merge loop:
 * normalise, mark word starts with U+2581, then repeatedly merge the adjacent
 * pair whose joined string is a known piece with the highest score, leftmost
 * first on ties. OnDeviceTokenizerTest checks it against the Python output.
 *
 * Normalisation is Java's NFKC plus the whitespace rules the model declares
 * (collapse runs, trim, dummy prefix). The model's own `nmt_nfkc` table adds a
 * few control-character rules on top; for the Hindi a teacher speaks or types
 * the two agree, which the test fixture checks sentence by sentence.
 */
class SentencePieceBpe private constructor(private val scores: HashMap<String, Float>) {

    fun encodeAsPieces(text: String): List<String> {
        var norm = Normalizer.normalize(text, Normalizer.Form.NFKC)
        norm = norm.replace(WHITESPACE, " ").trim()
        if (norm.isEmpty()) return emptyList()
        val marked = SPACE + norm.replace(' ', SPACE)

        // One symbol per code point to start with.
        val symbols = ArrayList<String>(marked.length)
        var i = 0
        while (i < marked.length) {
            val cp = marked.codePointAt(i)
            val n = Character.charCount(cp)
            symbols.add(marked.substring(i, i + n))
            i += n
        }

        while (symbols.size > 1) {
            var best = Float.NEGATIVE_INFINITY
            var at = -1
            for (k in 0 until symbols.size - 1) {
                val s = scores[symbols[k] + symbols[k + 1]] ?: continue
                if (s > best) {
                    best = s
                    at = k
                }
            }
            if (at < 0) break
            symbols[at] = symbols[at] + symbols[at + 1]
            symbols.removeAt(at + 1)
        }
        return symbols
    }

    companion object {
        private const val SPACE = '▁'
        private val WHITESPACE = Regex("\\s+")

        // SentencePiece piece types: 1 normal, 2 unknown, 3 control,
        // 4 user-defined, 5 unused, 6 byte. Only normal and user-defined
        // pieces take part in merges.
        private const val TYPE_NORMAL = 1
        private const val TYPE_USER_DEFINED = 4

        fun load(modelFile: File): SentencePieceBpe = parse(modelFile.readBytes())

        /** Read the pieces out of a serialised sentencepiece ModelProto. */
        fun parse(bytes: ByteArray): SentencePieceBpe {
            val scores = HashMap<String, Float>(180_000)
            val top = ProtoReader(bytes, 0, bytes.size)
            while (top.hasMore()) {
                val (field, wire) = top.tag()
                if (field == 1 && wire == 2) {
                    val (start, end) = top.lengthDelimited()
                    val msg = ProtoReader(bytes, start, end)
                    var piece: String? = null
                    var score = 0f
                    var type = TYPE_NORMAL
                    while (msg.hasMore()) {
                        val (f, w) = msg.tag()
                        when {
                            f == 1 && w == 2 -> {
                                val (s, e) = msg.lengthDelimited()
                                piece = String(bytes, s, e - s, Charsets.UTF_8)
                            }
                            f == 2 && w == 5 -> score = msg.fixed32Float()
                            f == 3 && w == 0 -> type = msg.varint().toInt()
                            else -> msg.skip(w)
                        }
                    }
                    if (piece != null && (type == TYPE_NORMAL || type == TYPE_USER_DEFINED)) {
                        scores[piece] = score
                    }
                } else {
                    top.skip(wire)
                }
            }
            return SentencePieceBpe(scores)
        }
    }

    /** Just enough of the protobuf wire format to read a ModelProto. */
    private class ProtoReader(private val b: ByteArray, private var pos: Int, private val end: Int) {
        fun hasMore() = pos < end

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val x = b[pos++].toInt() and 0xFF
                result = result or ((x and 0x7F).toLong() shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
            }
        }

        fun tag(): Pair<Int, Int> {
            val t = varint().toInt()
            return (t ushr 3) to (t and 7)
        }

        fun lengthDelimited(): Pair<Int, Int> {
            val len = varint().toInt()
            val start = pos
            pos += len
            return start to pos
        }

        fun fixed32Float(): Float {
            val bits = (b[pos].toInt() and 0xFF) or
                ((b[pos + 1].toInt() and 0xFF) shl 8) or
                ((b[pos + 2].toInt() and 0xFF) shl 16) or
                ((b[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return java.lang.Float.intBitsToFloat(bits)
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> lengthDelimited()
                5 -> pos += 4
                else -> throw IllegalStateException("Unsupported wire type $wire")
            }
        }
    }
}
