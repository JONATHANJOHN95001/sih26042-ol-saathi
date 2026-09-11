package app.olsaathi.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavPcmTest {

    /** A float WAV shaped like Bhashini's: format 3, mono, 22050 Hz, 32 bit. */
    private fun floatWav(samples: FloatArray, withFactChunk: Boolean = false): ByteArray {
        val fact = if (withFactChunk) 12 else 0
        val dataBytes = samples.size * 4
        val b = ByteBuffer.allocate(44 + fact + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + fact + dataBytes); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(3); b.putShort(1)
        b.putInt(22050); b.putInt(22050 * 4); b.putShort(4); b.putShort(32)
        if (withFactChunk) { b.put("fact".toByteArray()); b.putInt(4); b.putInt(samples.size) }
        b.put("data".toByteArray()); b.putInt(dataBytes)
        samples.forEach { b.putFloat(it) }
        return b.array()
    }

    private fun pcm16Samples(wav: ByteArray): ShortArray {
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        val n = b.getInt(40) / 2
        return ShortArray(n) { b.getShort(44 + it * 2) }
    }

    @Test
    fun bhashiniFloatBecomesSixteenBitPcm() {
        val out = WavPcm.toPcm16(floatWav(floatArrayOf(0f, 0.5f, 1f, -1f)))
        assertNotNull(out)
        val b = ByteBuffer.wrap(out!!).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(out, 0, 4))
        assertEquals("WAVE", String(out, 8, 4))
        assertEquals(1, b.getShort(20).toInt())
        assertEquals(1, b.getShort(22).toInt())
        assertEquals(22050, b.getInt(24))
        assertEquals(16, b.getShort(34).toInt())
        assertEquals(8, b.getInt(40))
        assertArrayEquals(shortArrayOf(0, 16383, 32767, -32767), pcm16Samples(out))
    }

    @Test
    fun outOfRangeAndNanAreClampedRatherThanWrapped() {
        val out = WavPcm.toPcm16(floatWav(floatArrayOf(2f, -3f, Float.NaN)))!!
        assertArrayEquals(shortArrayOf(32767, -32767, 0), pcm16Samples(out))
    }

    @Test
    fun aFactChunkBeforeTheDataIsSkipped() {
        val out = WavPcm.toPcm16(floatWav(floatArrayOf(0.25f), withFactChunk = true))!!
        assertArrayEquals(shortArrayOf(8191), pcm16Samples(out))
    }

    @Test
    fun sixteenBitInputIsPassedThroughUntouched() {
        val pcm = WavPcm.toPcm16(floatWav(floatArrayOf(0.5f)))!!
        assertSame(pcm, WavPcm.toPcm16(pcm))
    }

    @Test
    fun somethingThatIsNotAWavIsRefusedRatherThanPlayed() {
        assertNull(WavPcm.toPcm16("not audio at all".toByteArray()))
        assertNull(WavPcm.toPcm16(ByteArray(0)))
    }
}
