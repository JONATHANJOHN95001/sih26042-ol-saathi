package app.olsaathi.content

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The dropdown has to name a language in its own script and in English at
 * once. A Hindi-medium teacher cannot tell Odia from Gujarati by their own
 * scripts, and that teacher is who this app is for.
 */
class LanguageMenuLabelTest {

    private val manifest = """
        {"languages":[
          {"code":"asm","english":"Assamese","endonym":"অসমীয়া","display":"অসমীয়া","available":true},
          {"code":"sat","english":"Santali","endonym":"","display":"Santali","available":true},
          {"code":"xx","english":"Blankish","available":true}
        ]}
    """.trimIndent()

    private fun option(code: String) =
        LanguageRegistry.parseForTest(manifest).first { it.code == code }

    @Test
    fun endonymCarriesTheEnglishNameBesideIt() {
        assertEquals("অসমীয়া (Assamese)", option("asm").menuLabel)
    }

    @Test
    fun aNameIsNeverPrintedTwice() {
        // Santali's pack has no endonym, so display already is the English
        // name. "Santali (Santali)" would look like a bug.
        assertEquals("Santali", option("sat").menuLabel)
    }

    @Test
    fun aMissingDisplayFallsBackToEnglishAlone() {
        assertEquals("Blankish", option("xx").menuLabel)
    }
}
