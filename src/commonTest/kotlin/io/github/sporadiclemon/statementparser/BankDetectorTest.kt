package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BankDetectorTest {

    private val detector = BankDetector()

    @Test
    fun `detects NatWest from keyword fragment`() {
        val fragments = listOf(
            TextFragment("Welcome", x = 100f, y = 200f, page = 0),
            TextFragment("NatWest", x = 200f, y = 200f, page = 0),
            TextFragment("Statement", x = 260f, y = 200f, page = 0),
        )
        val profile = detector.detect(fragments)
        assertEquals(Bank.NATWEST, profile?.bank)
    }

    @Test
    fun `detects NatWest from National Westminster keyword`() {
        val fragments = listOf(
            TextFragment("National", x = 100f, y = 50f, page = 0),
            TextFragment("Westminster", x = 165f, y = 50f, page = 0),
            TextFragment("Bank", x = 230f, y = 50f, page = 0),
        )
        val profile = detector.detect(fragments)
        assertEquals(Bank.NATWEST, profile?.bank)
    }

    @Test
    fun `detects Monzo from keyword fragment`() {
        val fragments = listOf(
            TextFragment("Monzo", x = 300f, y = 100f, page = 0),
            TextFragment("Statement", x = 360f, y = 100f, page = 0),
        )
        val profile = detector.detect(fragments)
        assertEquals(Bank.MONZO, profile?.bank)
    }

    @Test
    fun `returns null for unrecognised bank`() {
        val fragments = listOf(
            TextFragment("ACME", x = 100f, y = 100f, page = 0),
            TextFragment("Bank", x = 150f, y = 100f, page = 0),
        )
        assertNull(detector.detect(fragments))
    }

    @Test
    fun `detection is case-insensitive`() {
        val fragments = listOf(TextFragment("natwest", x = 0f, y = 0f, page = 0))
        assertEquals(Bank.NATWEST, detector.detect(fragments)?.bank)
    }

    /**
     * Every registered profile's own detection keyword, and nothing else, must resolve to that
     * exact profile - not merely the same [Bank] (HSBC has two profiles, current account and
     * credit card, sharing one Bank) and not a different, earlier-in-the-list profile whose
     * keyword happens to be a substring match against this one's page.
     *
     * This is the general form of a guard that was previously hand-written once, for one pair
     * (HSBC_CREDIT_CARD before HSBC - see [HsbcCreditCardTest]). Written generally, it catches
     * the same category of mistake for any future profile without needing a bespoke test for
     * every new pair.
     */
    @Test
    fun `no profile's keyword resolves to a different profile`() {
        for (profile in PdfBankProfiles.all) {
            for (keyword in profile.detectionKeywords) {
                val page = keyword.split(' ').mapIndexed { i, word ->
                    TextFragment(word, x = i * 60f, y = 40f, page = 0)
                }
                val detected = detector.detect(page)
                assertEquals(
                    profile,
                    detected,
                    "keyword \"$keyword\" (belongs to ${profile.bank.displayName}) resolved to " +
                        "${detected?.bank?.displayName ?: "no profile"} instead",
                )
            }
        }
    }
}
