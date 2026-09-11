package app.olsaathi.text

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Kotlin bridge must match tools/olchiki_bridge.py exactly, because the
 * Python one wrote the `bridge` field every shipped Santali line carries, and
 * the live voice speaks whatever this one produces. Checked against every
 * line in the real pack rather than a few hand-picked words.
 */
class OlChikiBridgeTest {

    @Test
    fun matchesThePythonBridgeOnEveryShippedSantaliLine() {
        val pack = JSONObject(File("app/src/main/assets/pack/pack.sat.json").readText(Charsets.UTF_8))
        val pairs = mutableListOf<Pair<String, String>>()
        collect(pack.get("entries"), pairs)
        assertTrue("expected the 53 Santali lines, found ${pairs.size}", pairs.size >= 50)
        pairs.forEach { (target, bridge) ->
            assertEquals("bridge differs for $target", bridge, OlChikiBridge.toDevanagari(target))
        }
    }

    @Test
    fun johar() {
        // Word-final consonant stays bare: जोहार, never जोहार्.
        assertEquals("जोहार", OlChikiBridge.toDevanagari("ᱡᱚᱦᱟᱨ"))
    }

    @Test
    fun theLiveTranslationFromTodaysTest() {
        assertEquals("आम चेद लेका काना", OlChikiBridge.toDevanagari("ᱟᱢ ᱪᱮᱫ ᱞᱮᱠᱟ ᱠᱟᱱᱟ"))
    }

    @Test
    fun textOutsideOlChikiPassesThrough() {
        assertEquals("abc 12 नमस्ते", OlChikiBridge.toDevanagari("abc 12 नमस्ते"))
        assertFalse(OlChikiBridge.containsOlChiki("नमस्ते"))
        assertTrue(OlChikiBridge.containsOlChiki("ᱡᱚᱦᱟᱨ"))
    }

    private fun collect(node: Any?, out: MutableList<Pair<String, String>>) {
        when (node) {
            is JSONObject -> {
                val target = node.optString("target")
                val bridge = node.optString("bridge")
                if (bridge.isNotEmpty() && OlChikiBridge.containsOlChiki(target)) out += target to bridge
                node.keys().forEach { collect(node.get(it), out) }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.get(i), out)
        }
    }
}
