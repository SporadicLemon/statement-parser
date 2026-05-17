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
}
