package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfTransactionParserTest {

    private val parser = PdfTransactionParser()
    private val profile = PdfBankProfiles.NATWEST

    @Test
    fun `parses NatWest multi-line credit transaction`() {
        val rows = listOf(
            RawTableRow("07 APR", "Automated Credit S MITCHELL SHARON", null, null, null, null, 150f),
            RawTableRow(null, "200000001740692772", "25.00", null, null, "123.78", 163f),
        )
        val txns = parser.parse(rows, profile, statementYear = 2026)
        assertEquals(1, txns.size)
        assertEquals(LocalDate(2026, 4, 7), txns[0].date)
        assertEquals("Automated Credit S MITCHELL SHARON 200000001740692772", txns[0].description)
        assertEquals(25.0, txns[0].amount, 0.001)
        assertEquals(123.78, txns[0].runningBalance!!, 0.001)
    }

    @Test
    fun `parses NatWest multi-line debit transaction`() {
        val rows = listOf(
            RawTableRow(null, "OnLine Transaction AMERICAN EXP 3773", null, null, null, null, 180f),
            RawTableRow(null, "24EA33C7571470F823 TPP AMERICAN EXPRE", null, "25.00", null, "98.78", 193f),
        )
        val txns = parser.parse(rows, profile, statementYear = 2026, initialDate = LocalDate(2026, 4, 7))
        assertEquals(1, txns.size)
        assertEquals(LocalDate(2026, 4, 7), txns[0].date)
        assertEquals(-25.0, txns[0].amount, 0.001)
    }

    @Test
    fun `parses NatWest single-line direct debit`() {
        val rows = listOf(
            RawTableRow(null, "Direct Debit EE LIMITED", null, "50.81", null, "24.97", 200f),
        )
        val txns = parser.parse(rows, profile, statementYear = 2026, initialDate = LocalDate(2026, 4, 7))
        assertEquals(1, txns.size)
        assertEquals("Direct Debit EE LIMITED", txns[0].description)
        assertEquals(-50.81, txns[0].amount, 0.001)
    }

    @Test
    fun `date propagates across transactions in same group`() {
        val rows = listOf(
            RawTableRow("27 APR", "Automated Credit PAUL MITCHELL", "39.91", null, null, "40.88", 200f),
            RawTableRow(null, "Automated Credit PAUL MITCHELL", "9.89", null, null, "50.77", 210f),
        )
        val txns = parser.parse(rows, profile, statementYear = 2026)
        assertEquals(2, txns.size)
        assertEquals(LocalDate(2026, 4, 27), txns[0].date)
        assertEquals(LocalDate(2026, 4, 27), txns[1].date)
    }

    @Test
    fun `skips BROUGHT FORWARD row`() {
        val rows = listOf(
            RawTableRow("03 APR 2026", "BROUGHT FORWARD", null, null, null, "98.78", 120f),
            RawTableRow("07 APR", "Direct Debit EE LIMITED", null, "50.81", null, "47.97", 150f),
        )
        val txns = parser.parse(rows, profile, statementYear = 2026)
        assertEquals(1, txns.size)
        assertEquals("Direct Debit EE LIMITED", txns[0].description)
    }
}
