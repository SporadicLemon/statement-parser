package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfBankProfileTest {

    @Test
    fun `NatWest profile has correct detection keywords`() {
        assertTrue(PdfBankProfiles.NATWEST.detectionKeywords.any { it.contains("NatWest") })
    }

    @Test
    fun `NatWest profile has split amount columns`() {
        val headers = PdfBankProfiles.NATWEST.columnHeaders
        assertTrue(headers.containsKey(ColumnRole.AMOUNT_IN))
        assertTrue(headers.containsKey(ColumnRole.AMOUNT_OUT))
    }

    @Test
    fun `Monzo profile has single amount column`() {
        val headers = PdfBankProfiles.MONZO.columnHeaders
        assertTrue(headers.containsKey(ColumnRole.AMOUNT))
    }

    @Test
    fun `all profiles are in the all list`() {
        assertEquals(2, PdfBankProfiles.all.size)
    }
}
