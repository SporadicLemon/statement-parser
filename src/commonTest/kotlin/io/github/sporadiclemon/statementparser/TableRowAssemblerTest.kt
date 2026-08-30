package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TableRowAssemblerTest {

    private val assembler = TableRowAssembler()

    // A layout matching NatWest column positions
    private val natwestLayout = ColumnLayout(
        headerY = 100f,
        headerPage = 0,
        columns = mapOf(
            ColumnRole.DATE        to (0f..90f),
            ColumnRole.DESCRIPTION to (90f..370f),
            ColumnRole.AMOUNT_IN   to (370f..430f),
            ColumnRole.AMOUNT_OUT  to (430f..510f),
            ColumnRole.BALANCE     to (510f..Float.MAX_VALUE),
        ),
    )

    @Test
    fun `assembles single-line transaction row`() {
        val fragments = listOf(
            TextFragment("Direct",   x = 91f,  y = 200f, page = 0),
            TextFragment("Debit",    x = 135f, y = 200f, page = 0),
            TextFragment("EE",       x = 177f, y = 200f, page = 0),
            TextFragment("LIMITED",  x = 195f, y = 200f, page = 0),
            TextFragment("50.81",    x = 440f, y = 200f, page = 0),
            TextFragment("24.97",    x = 540f, y = 200f, page = 0),
        )
        val rows = assembler.assemble(fragments, natwestLayout)
        assertEquals(1, rows.size)
        assertEquals("Direct Debit EE LIMITED", rows[0].description)
        assertNull(rows[0].date)
        assertNull(rows[0].amountIn)
        assertEquals("50.81", rows[0].amountOut)
        assertEquals("24.97", rows[0].balance)
    }

    @Test
    fun `assembles two-line transaction with date`() {
        val fragments = listOf(
            // Row 1 (y=150): date + description
            TextFragment("07",           x = 30f,  y = 150f, page = 0),
            TextFragment("APR",          x = 45f,  y = 150f, page = 0),
            TextFragment("Automated",    x = 95f,  y = 150f, page = 0),
            TextFragment("Credit",       x = 155f, y = 150f, page = 0),
            TextFragment("S",            x = 202f, y = 150f, page = 0),
            TextFragment("MITCHELL",     x = 215f, y = 150f, page = 0),
            // Row 2 (y=163): description continuation + amount + balance
            TextFragment("200000001740692772", x = 95f,  y = 163f, page = 0),
            TextFragment("25.00",              x = 390f, y = 163f, page = 0),
            TextFragment("123.78",             x = 540f, y = 163f, page = 0),
        )
        val rows = assembler.assemble(fragments, natwestLayout)
        assertEquals(2, rows.size)

        assertEquals("07 APR", rows[0].date)
        assertEquals("Automated Credit S MITCHELL", rows[0].description)
        assertNull(rows[0].amountIn)
        assertNull(rows[0].balance)

        assertNull(rows[1].date)
        assertEquals("200000001740692772", rows[1].description)
        assertEquals("25.00", rows[1].amountIn)
        assertEquals("123.78", rows[1].balance)
    }

    @Test
    fun `skips fragments above headerY`() {
        val fragments = listOf(
            TextFragment("HEADER", x = 100f, y = 50f, page = 0),  // above headerY=100
            TextFragment("Direct", x = 95f,  y = 200f, page = 0),
            TextFragment("Debit",  x = 140f, y = 200f, page = 0),
            TextFragment("10.00",  x = 440f, y = 200f, page = 0),
            TextFragment("90.00",  x = 540f, y = 200f, page = 0),
        )
        val rows = assembler.assemble(fragments, natwestLayout)
        assertEquals(1, rows.size)
        assertEquals("Direct Debit", rows[0].description)
    }

    @Test
    fun `emits row when fragment falls in description column`() {
        val fragments = listOf(
            TextFragment("PAGE", x = 270f, y = 200f, page = 0),
        )
        val rows = assembler.assemble(fragments, natwestLayout)
        assertEquals(1, rows.size)
        assertEquals("PAGE", rows[0].description)
    }

    @Test
    fun `keeps a row that carries a figure but no description of its own`() {
        val fragments = listOf(
            // x=440 falls in AMOUNT_OUT only — no description content.
            // Monzo splits a refund across three lines and the middle one, holding the date,
            // amount and balance, has an empty description column. Dropping it loses the
            // transaction outright, so the row has to survive with an empty description.
            TextFragment("50.00", x = 440f, y = 200f, page = 0),
        )
        val rows = assembler.assemble(fragments, natwestLayout)
        assertEquals(1, rows.size)
        assertEquals("", rows[0].description)
        assertEquals("50.00", rows[0].amountOut)
    }

    @Test
    fun `drops a row with neither a description nor a figure`() {
        val fragments = listOf(
            // x=20 falls in DATE only — nothing else on the line
            TextFragment("03", x = 20f, y = 200f, page = 0),
        )
        assertEquals(0, assembler.assemble(fragments, natwestLayout).size)
    }

    // Bands from a right-aligned NatWest-style heading: the amount columns run to the midpoints
    // between their headings' right edges (396, 453, 523), so Paid In owns 253..424.5.
    private val rightAlignedLayout = ColumnLayout(
        headerY = 100f,
        headerPage = 0,
        columns = mapOf(
            ColumnRole.DATE        to (0f..84.5f),
            ColumnRole.DESCRIPTION to (84.5f..253f),
            ColumnRole.AMOUNT_IN   to (253f..424.5f),
            ColumnRole.AMOUNT_OUT  to (424.5f..488f),
            ColumnRole.BALANCE     to (488f..Float.MAX_VALUE),
        ),
    )

    @Test
    fun `classifies amounts by their right edge not their left`() {
        // Every figure in an amount column ends at the same x and begins wherever its length
        // puts it, so a long figure reaches back into the previous column's band. Matching on
        // the left edge reads "1,234.56" withdrawn (ends at 453) as money paid in, and a
        // "12,345.67" balance (ends at 523) as money withdrawn.
        val fragments = listOf(
            TextFragment("Rent",      x = 110f, y = 200f, page = 0, width = 24f),
            TextFragment("1,234.56",  x = 407f, y = 200f, page = 0, width = 46f),
            TextFragment("12,345.67", x = 468f, y = 200f, page = 0, width = 55f),
        )
        val rows = assembler.assemble(fragments, rightAlignedLayout)
        assertEquals(1, rows.size)
        assertEquals("Rent", rows[0].description)
        assertNull(rows[0].amountIn)
        assertEquals("1,234.56", rows[0].amountOut)
        assertEquals("12,345.67", rows[0].balance)
    }

    @Test
    fun `classifies short and long figures in the same column alike`() {
        val fragments = listOf(
            // "7.00" and "32.00" end together at 393-396; "80.00" is a withdrawal ending at 449.
            TextFragment("Credit", x = 110f, y = 200f, page = 0, width = 30f),
            TextFragment("7.00",   x = 379f, y = 200f, page = 0, width = 14f),
            TextFragment("Credit", x = 110f, y = 220f, page = 0, width = 30f),
            TextFragment("32.00",  x = 374f, y = 220f, page = 0, width = 22f),
            TextFragment("Debit",  x = 110f, y = 240f, page = 0, width = 26f),
            TextFragment("80.00",  x = 431f, y = 240f, page = 0, width = 18f),
        )
        val rows = assembler.assemble(fragments, rightAlignedLayout)
        assertEquals(3, rows.size)
        assertEquals("7.00", rows[0].amountIn)
        assertEquals("32.00", rows[1].amountIn)
        assertNull(rows[2].amountIn)
        assertEquals("80.00", rows[2].amountOut)
    }

    @Test
    fun `a short credit past the naive column midpoint is not a withdrawal`() {
        // End to end over a right-aligned heading, the failure this geometry caused: "Paid In(£)"
        // begins at 358 and "Withdrawn(£)" at 398, so midpoints between the headings' left edges
        // put the boundary at 378 - while the credits beneath right-align at 393-396. A £7.00
        // credit begins at 379, past that boundary, and was recorded as a £7.00 withdrawal.
        val header = listOf(
            TextFragment("Date",         x = 59f,  y = 100f, page = 0, width = 22f),
            TextFragment("Description",  x = 110f, y = 100f, page = 0, width = 55f),
            TextFragment("Paid",         x = 358f, y = 100f, page = 0, width = 17f),
            TextFragment("In(£)",        x = 377f, y = 100f, page = 0, width = 19f),
            TextFragment("Withdrawn(£)", x = 398f, y = 100f, page = 0, width = 55f),
            TextFragment("Balance(£)",   x = 478f, y = 100f, page = 0, width = 45f),
        )
        val body = listOf(
            TextFragment("05",        x = 59f,  y = 200f, page = 0, width = 11f),
            TextFragment("JUN",       x = 72f,  y = 200f, page = 0, width = 20f),
            TextFragment("Automated", x = 110f, y = 200f, page = 0, width = 48f),
            TextFragment("Credit",    x = 160f, y = 200f, page = 0, width = 30f),
            TextFragment("7.00",      x = 379f, y = 200f, page = 0, width = 14f),
            TextFragment("131.29",    x = 496f, y = 200f, page = 0, width = 21f),
        )
        val layout = ColumnDetector().detect(header + body, PdfBankProfiles.NATWEST)!!
        val rows = assembler.assemble(header + body, layout)
        assertEquals(1, rows.size)
        assertEquals("05 JUN", rows[0].date)
        assertEquals("Automated Credit", rows[0].description)
        assertEquals("7.00", rows[0].amountIn)
        assertNull(rows[0].amountOut)
        assertEquals("131.29", rows[0].balance)
    }

    @Test
    fun `overflowing description text in an amount column is still description`() {
        val fragments = listOf(
            TextFragment("Standing", x = 110f, y = 200f, page = 0, width = 42f),
            TextFragment("Order",    x = 155f, y = 200f, page = 0, width = 30f),
            // Reference text running past the description column: not a figure, so it rejoins
            // the description rather than becoming an amount.
            TextFragment("REF-90210-Q", x = 300f, y = 200f, page = 0, width = 60f),
            TextFragment("45.00",       x = 431f, y = 200f, page = 0, width = 18f),
        )
        val rows = assembler.assemble(fragments, rightAlignedLayout)
        assertEquals(1, rows.size)
        assertEquals("Standing Order REF-90210-Q", rows[0].description)
        assertNull(rows[0].amountIn)
        assertEquals("45.00", rows[0].amountOut)
    }
}
