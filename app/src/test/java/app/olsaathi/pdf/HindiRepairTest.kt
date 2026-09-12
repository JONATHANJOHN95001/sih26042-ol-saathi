package app.olsaathi.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every broken form here was extracted from a real NCERT PDF: Sarangi
 * Class 1 and Class 2, chapter 1 (ahsr101.pdf, bhsr101.pdf), 2026-27 reprint.
 */
class HindiRepairTest {

    // Frequency order, as in the IndicTrans2 vocabulary: क्या far above कया.
    private val words = HindiRepair.Words.of(
        "है", "क्या", "उन्हें", "क्यों", "मदद", "अगर", "अच्छी", "बातचीत", "अग्र",
        "जल्दी", "सप्ताह", "कया", "जलदी", "चप्पल", "सब्जी", "सबज़ी",
    )

    private fun fix(w: String) = HindiRepair.bestSpelling(HindiRepair.nfc(w), words)

    @Test
    fun `a halant the font map dropped is restored`() {
        assertEquals("उन्हें", fix("उनहें"))
        assertEquals("क्यों", fix("कयों"))
        assertEquals("सप्ताह", fix("सपताह"))
    }

    @Test
    fun `the more frequent spelling wins even when both are words`() {
        // कया is in the vocabulary, noise from other extracted PDFs, but far rarer.
        assertEquals("क्या", fix("कया"))
        assertEquals("जल्दी", fix("जलदी"))
    }

    @Test
    fun `a right word is left alone`() {
        // अग्र is a word too, but rarer than अगर.
        assertEquals("अगर", fix("अगर"))
        assertEquals("मदद", fix("मदद"))
        assertEquals("क्या", fix("क्या"))
    }

    @Test
    fun `a doubled letter goes only when the word is unknown`() {
        assertEquals("बातचीत", fix("बातचचीत"))
    }

    @Test
    fun `a stem match repairs an inflected word`() {
        assertEquals(HindiRepair.nfc("चप्पलें"), fix("चपपलें"))
    }

    @Test
    fun `an optional nukta is kept while the halant is restored`() {
        assertEquals(HindiRepair.nfc("सब्ज़ी"), fix("सबज़ी"))
    }

    @Test
    fun `a lost consonant after a halant is found`() {
        assertEquals("क्या", fix("क्ा"))
        assertEquals("अच्छी", fix("अच्ी"))
    }

    @Test
    fun `split and doubled vowel signs and joiners are fixed by rule`() {
        assertEquals(HindiRepair.nfc("ज़ोर से"), HindiRepair.normalize("ज़ाेर से"))
        assertEquals("ऐसा", HindiRepair.normalize("एेसा"))
        assertEquals("और", HindiRepair.normalize("आैर"))
        assertEquals("क्यों आए होंगे", HindiRepair.normalize("क्योंों आए होंोंगे"))
        assertEquals("खूब", HindiRepair.normalize("खूूब"))
        assertEquals("बच्चों", HindiRepair.normalize("बच्\u200Dचों"))
        assertEquals("स्कूल", HindiRepair.normalize("स्\u200D कूल"))
        assertEquals("विस्तृत चर्चा", HindiRepair.normalize("िवस्तृत चर्चा"))
        assertEquals("नीमा", HindiRepair.normalize("Reprint 2026-27  नीमा").trim())
    }

    @Test
    fun `a line reports how many words it repaired`() {
        val (line, n) = HindiRepair.repairLine("उनहें नीमा का बहुत इंतज़ार होता है।", words)
        assertEquals("उन्हें नीमा का बहुत इंतज़ार होता है।", line)
        assertEquals(1, n)
    }
}
