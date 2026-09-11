package app.olsaathi.content

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for human review provenance.
 *
 * Uses small inline JSON fixtures rather than the shipped pack, since
 * the shipped pack has no review data yet and we need both branches.
 */
class HumanReviewTest {

    /** A pack with one entry that has been reviewed and confirmed. */
    private val reviewedPack = VerifiedContentPack.loadFromString("""
        {
          "generated": "2026-08-27T00:00:00Z",
          "provenance": {
            "translationService": "ai4bharat/indictrans-v2-all-gpu--t4",
            "platform": "Bhashini (MeitY, Government of India)"
          },
          "entries": {
            "r01": {
              "source": "नमस्ते बच्चों।",
              "target": "ᱡᱟᱞᱟᱨᱤ ᱵᱟᱨᱤᱤᱣᱟᱜᱼ",
              "en": "Hello children.",
              "nipun": "ROUTINE",
              "kind": "phrase",
              "service": "ai4bharat/indictrans-v2-all-gpu--t4",
              "reviewedBy": "Somai Murmu",
              "reviewedOn": "2026-08-27",
              "reviewVerdict": "confirmed"
            }
          }
        }
    """.trimIndent())

    /** A pack with no review fields at all — current pack behaviour. */
    private val unreviewedPack = VerifiedContentPack.loadFromString("""
        {
          "generated": "2026-08-27T00:00:00Z",
          "provenance": {
            "translationService": "ai4bharat/indictrans-v2-all-gpu--t4",
            "platform": "Bhashini (MeitY, Government of India)"
          },
          "entries": {
            "u01": {
              "source": "सब बैठ जाओ।",
              "target": "ᱡᱚᱢᱟᱹᱠᱩ ᱥᱟᱹᱜᱤᱫᱚᱜᱼ",
              "en": "Everyone sit down.",
              "nipun": "ROUTINE",
              "kind": "phrase",
              "service": "ai4bharat/indictrans-v2-all-gpu--t4"
            }
          }
        }
    """.trimIndent())

    @Test
    fun reviewed_entry_resolves_to_human_verified() {
        val result = reviewedPack.lookup("नमस्ते बच्चों।")
        assertEquals(Provenance.HUMAN_VERIFIED, result.provenance)
        assertEquals("Somai Murmu", result.reviewerName)
        assertEquals("2026-08-27", result.reviewedOn)
        assertTrue("HUMAN_VERIFIED is trustworthy", result.isTrustworthy)
        assertTrue("HUMAN_VERIFIED is available", result.isAvailable)
    }

    @Test
    fun unreviewed_entry_resolves_as_before() {
        val result = unreviewedPack.lookup("सब बैठ जाओ।")
        assertEquals(Provenance.VERIFIED, result.provenance)
        assertEquals("", result.reviewerName)
        assertEquals("", result.reviewedOn)
        assertTrue("VERIFIED is trustworthy", result.isTrustworthy)
        assertTrue("VERIFIED is available", result.isAvailable)
    }
}
