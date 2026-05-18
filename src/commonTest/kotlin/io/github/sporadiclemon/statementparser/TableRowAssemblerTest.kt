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
    fun `skips rows with no recognised column content`() {
        val fragments = listOf(
            TextFragment("PAGE", x = 270f, y = 200f, page = 0),
        )
        val rows = assembler.assemble(fragments, natwestLayout)
        assertEquals(1, rows.size)
        assertEquals("PAGE", rows[0].description)
    }
}
