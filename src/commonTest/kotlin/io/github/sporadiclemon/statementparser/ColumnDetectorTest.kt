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

    // A measured header row, the shape a real statement produces: every word carries its width,
    // so the right edge of a heading phrase is known. Positions are invented but reproduce the
    // geometry that breaks left-edge anchoring - "Paid In(£)" begins 40pt left of "Withdrawn(£)"
    // while the figures under it end only 57pt left of the figures under "Withdrawn(£)".
    //
    //   heading        left   right
    //   Date             59      81
    //   Description     110     165
    //   Paid In(£)      358     396
    //   Withdrawn(£)    398     453
    //   Balance(£)      478     523
    private val measuredHeaderFragments = listOf(
        TextFragment("Date",         x = 59f,  y = 300f, page = 0, width = 22f),
        TextFragment("Description",  x = 110f, y = 300f, page = 0, width = 55f),
        TextFragment("Paid",         x = 358f, y = 300f, page = 0, width = 17f),
        TextFragment("In(£)",        x = 377f, y = 300f, page = 0, width = 19f),
        TextFragment("Withdrawn(£)", x = 398f, y = 300f, page = 0, width = 55f),
        TextFragment("Balance(£)",   x = 478f, y = 300f, page = 0, width = 45f),
    )

    @Test
    fun `anchors amount columns on the right edge of their heading`() {
        val layout = detector.detect(measuredHeaderFragments, PdfBankProfiles.NATWEST)!!
        // Midpoints between the anchors: 59, 110, 396, 453, 523.
        assertEquals(0f..84.5f, layout.columns[ColumnRole.DATE])
        assertEquals(84.5f..253f, layout.columns[ColumnRole.DESCRIPTION])
        assertEquals(253f..424.5f, layout.columns[ColumnRole.AMOUNT_IN])
        assertEquals(424.5f..488f, layout.columns[ColumnRole.AMOUNT_OUT])
        assertEquals(488f..Float.MAX_VALUE, layout.columns[ColumnRole.BALANCE])
    }

    @Test
    fun `short right-aligned figure stays inside its amount column`() {
        val layout = detector.detect(measuredHeaderFragments, PdfBankProfiles.NATWEST)!!
        // "7.00" is printed at x=379..393 - it starts right of where left-edge anchoring put the
        // Paid In / Withdrawn boundary (378), so only its right edge identifies its column.
        assertTrue(393f in layout.columns[ColumnRole.AMOUNT_IN]!!, "right edge of a £7.00 credit")
        // A longer figure in the same column ends in the same place.
        assertTrue(396f in layout.columns[ColumnRole.AMOUNT_IN]!!, "right edge of a £32.00 credit")
        // And a debit's right edge still lands in Withdrawn.
        assertTrue(449f in layout.columns[ColumnRole.AMOUNT_OUT]!!, "right edge of an £80.00 debit")
        assertTrue(517f in layout.columns[ColumnRole.BALANCE]!!, "right edge of a £131.29 balance")
    }

    @Test
    fun `degrades to bare x positions when widths are unavailable`() {
        // An extractor that cannot measure words leaves width at 0, so right == x and a column
        // is anchored on a plain coordinate rather than a measured edge.
        val layout = detector.detect(natwestHeaderFragments, PdfBankProfiles.NATWEST)!!
        assertEquals(0f..75f, layout.columns[ColumnRole.DATE])
        assertEquals(75f..260f, layout.columns[ColumnRole.DESCRIPTION])
        assertEquals(260f..430f, layout.columns[ColumnRole.AMOUNT_IN])
    }

    @Test
    fun `returns null when header row not found`() {
        val fragments = listOf(TextFragment("Some random text", x = 100f, y = 100f, page = 0))
        assertNull(detector.detect(fragments, PdfBankProfiles.NATWEST))
    }

    @Test
    fun `groupByRow keeps fragments with drifting y in same group`() {
        val fragments = listOf(
            TextFragment("A", x = 10f, y = 100.0f, page = 0),
            TextFragment("B", x = 50f, y = 101.5f, page = 0),
            TextFragment("C", x = 90f, y = 101.8f, page = 0),
            // New row: 6pt gap from anchor 100.0
            TextFragment("D", x = 10f, y = 110.0f, page = 0),
        )
        val rows = detector.groupByRow(fragments)
        assertEquals(2, rows.size)
        assertEquals(3, rows[0].size)
        assertEquals(1, rows[1].size)
    }
}
