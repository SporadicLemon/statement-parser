package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColumnDetectorTest {

    private val detector = ColumnDetector()

    // NatWest header row: Date | Description | Paid In(£) | Withdrawn(£) | Balance(£)
    private val natwestHeaderFragments = listOf(
        TextFragment("Date",          x = 30f,  y = 300f, page = 0),
        TextFragment("Description",   x = 120f, y = 300f, page = 0),
        TextFragment("Paid",          x = 380f, y = 300f, page = 0),
        TextFragment("In(£)",         x = 400f, y = 300f, page = 0),
        TextFragment("Withdrawn(£)",  x = 460f, y = 300f, page = 0),
        TextFragment("Balance(£)",    x = 540f, y = 300f, page = 0),
        // Non-header fragments above the header
        TextFragment("NatWest",       x = 300f, y = 100f, page = 0),
        TextFragment("Statement",     x = 360f, y = 100f, page = 0),
    )

    @Test
    fun `detects NatWest header row y`() {
        val layout = detector.detect(natwestHeaderFragments, PdfBankProfiles.NATWEST)
        assertNotNull(layout)
        assertEquals(300f, layout.headerY)
    }

    @Test
    fun `assigns DATE column x range for NatWest`() {
        val layout = detector.detect(natwestHeaderFragments, PdfBankProfiles.NATWEST)!!
        val dateRange = layout.columns[ColumnRole.DATE]!!
        assertTrue(30f in dateRange, "DATE fragment x=30 should be in DATE range")
        assertTrue(119f !in dateRange || 120f in layout.columns[ColumnRole.DESCRIPTION]!!)
    }

    @Test
    fun `assigns DESCRIPTION column for NatWest`() {
        val layout = detector.detect(natwestHeaderFragments, PdfBankProfiles.NATWEST)!!
        val descRange = layout.columns[ColumnRole.DESCRIPTION]!!
        assertTrue(120f in descRange)
        assertTrue(200f in descRange)
    }

    @Test
    fun `assigns AMOUNT_IN column for NatWest`() {
        val layout = detector.detect(natwestHeaderFragments, PdfBankProfiles.NATWEST)!!
        assertTrue(390f in layout.columns[ColumnRole.AMOUNT_IN]!!)
    }

    @Test
    fun `assigns AMOUNT_OUT column for NatWest`() {
        val layout = detector.detect(natwestHeaderFragments, PdfBankProfiles.NATWEST)!!
        assertTrue(460f in layout.columns[ColumnRole.AMOUNT_OUT]!!)
    }

    @Test
    fun `assigns BALANCE column for NatWest`() {
        val layout = detector.detect(natwestHeaderFragments, PdfBankProfiles.NATWEST)!!
        assertTrue(540f in layout.columns[ColumnRole.BALANCE]!!)
        assertTrue(600f in layout.columns[ColumnRole.BALANCE]!!)
    }

    @Test
    fun `returns null when header row not found`() {
        val fragments = listOf(TextFragment("Some random text", x = 100f, y = 100f, page = 0))
        assertNull(detector.detect(fragments, PdfBankProfiles.NATWEST))
    }
}
