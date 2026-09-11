package app.olsaathi.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Rewrites the WAV Bhashini returns into one every Android MediaPlayer plays.
 *
 * Bhashini's speech models answer with IEEE float, 32 bit, mono, 22050 Hz,
 * normalised so the loudest sample sits at exactly 1.0 (measured on Tamil,
 * Malayalam, Telugu and Bengali, 10 Sep 2026). Float WAV support in the
 * platform's WAV extractor is not something to rely on across the cheap
 * tablets this app targets, while 16-bit integer PCM is the one format every
 * MediaPlayer has always played. So the samples are rewritten as 16-bit PCM
 * before playback, which also halves them in memory.
 *
 * 16-bit PCM input is passed through untouched. Anything else this does not
 * understand returns null, and the caller shows the text with no audio rather
 * than playing noise to a class.
 */
object WavPcm {

    private const val FORMAT_PCM = 1
    private const val FORMAT_FLOAT = 3

    fun toPcm16(wav: ByteArray): ByteArray? {
        val fmt = find(wav, "fmt ") ?: return null
        val data = find(wav, "data") ?: return null
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        if (fmt + 24 > wav.size) return null
        val format = b.getShort(fmt + 8).toInt() and 0xFFFF
        val channels = b.getShort(fmt + 10).toInt() and 0xFFFF
        val rate = b.getInt(fmt + 12)
        val bits = b.getShort(fmt + 22).toInt() and 0xFFFF

        if (format == FORMAT_PCM && bits == 16) return wav
        if (format != FORMAT_FLOAT || bits != 32 || channels < 1) return null

        val start = data + 8
        val available = (wav.size - start).coerceAtLeast(0)
        val declared = b.getInt(data + 4)
        val length = if (declared in 1..available) declared else available
        val samples = length / 4

        val out = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(out, channels, rate, samples * 2)
        for (i in 0 until samples) {
            val f = b.getFloat(start + i * 4)
            // Clamp before the cast. A float above 1.0 cast straight to a
            // short wraps round to a large negative value and plays as a click.
            val clamped = if (f.isNaN()) 0f else f.coerceIn(-1f, 1f)
            out.putShort((clamped * Short.MAX_VALUE).toInt().toShort())
        }
        return out.array()
    }

    private fun writeHeader(out: ByteBuffer, channels: Int, rate: Int, dataBytes: Int) {
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt(36 + dataBytes)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(16)
        out.putShort(FORMAT_PCM.toShort())
        out.putShort(channels.toShort())
        out.putInt(rate)
        out.putInt(rate * channels * 2)
        out.putShort((channels * 2).toShort())
        out.putShort(16)
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(dataBytes)
    }

    /** Offset of a chunk by id, walking the chunk list after the RIFF header. */
    private fun find(wav: ByteArray, id: String): Int? {
        if (wav.size < 12 || String(wav, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        val needle = id.toByteArray(Charsets.US_ASCII)
        var i = 12
        while (i + 8 <= wav.size) {
            if (wav[i] == needle[0] && wav[i + 1] == needle[1] &&
                wav[i + 2] == needle[2] && wav[i + 3] == needle[3]) return i
            val size = ByteBuffer.wrap(wav, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (size < 0) return null
            i += 8 + size + (size and 1)
        }
        return null
    }
}
