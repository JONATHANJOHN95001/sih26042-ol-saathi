package app.olsaathi.mt

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The Kotlin tokenizer must cut Hindi into exactly the pieces Python's
 * SentencePiece does, or the on-device model translates the wrong input.
 *
 * The fixture holds Python's output for every Hindi line in the packs plus a
 * few awkward ones (nukta letters, digits, extra spaces, a dash). The model
 * file itself is 3 MB and lives outside the repo with the rest of the
 * on-device model, so this test runs where that folder exists (the
 * developer's laptop, set OLSAATHI_MT_DIR to point elsewhere) and is skipped
 * on CI rather than failing there.
 */
class OnDeviceTokenizerTest {

    private val modelDir = File(
        System.getenv("OLSAATHI_MT_DIR") ?: System.getProperty("user.home") + "/models/it2-indic-indic-320M-int8"
    )

    @Test
    fun kotlinPiecesMatchPythonSentencePiece() {
        val model = File(modelDir, "model.SRC")
        assumeTrue("on-device model not present at $modelDir", model.exists())

        val bpe = SentencePieceBpe.load(model)
        val fixture = javaClass.getResource("/mt/tokenizer_fixture.json")!!.readText()
        val cases = JSONArray(fixture)
        var checked = 0
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val text = case.getString("text")
            val expected = case.getJSONArray("pieces").let { a -> List(a.length()) { a.getString(it) } }
            assertEquals("pieces for \"$text\"", expected, bpe.encodeAsPieces(text))
            checked++
        }
        assertEquals(cases.length(), checked)
    }

    @Test
    fun vocabularyReaderFindsSpecialsAndLanguageTags() {
        val dict = File(modelDir, "dict.SRC.json")
        assumeTrue("on-device model not present at $modelDir", dict.exists())

        val vocab = FlatJsonVocab.load(dict)
        assertEquals(0, vocab["<s>"])
        assertEquals(2, vocab["</s>"])
        assertEquals(3, vocab["<unk>"])
        assertEquals(8, vocab["hin_Deva"])
        assertEquals(29925, vocab["sat_Olck"])
        assertEquals(122706, vocab.size)
    }
}
