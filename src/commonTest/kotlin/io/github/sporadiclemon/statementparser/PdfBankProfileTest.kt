package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfBankProfileTest {

    @Test
    fun `NatWest profile has correct detection keywords`() {
        assertEquals(listOf("NatWest", "National Westminster"), PdfBankProfiles.NATWEST.detectionKeywords)
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
    fun `Starling profile keys the balance column off the first half of its two-line heading`() {
        val headers = PdfBankProfiles.STARLING.columnHeaders
        assertEquals("Account", headers[ColumnRole.BALANCE])
        // "Type", not "Transaction" - see StarlingPdfProfileTest for why.
        assertEquals("Type", headers[ColumnRole.DESCRIPTION])
    }

    @Test
    fun `Starling detection keywords are specific enough not to match a payee name`() {
        assertEquals(
            listOf("www.starlingbank.com", "Starling Bank Limited"),
            PdfBankProfiles.STARLING.detectionKeywords,
        )
    }

    @Test
    fun `all profiles are in the all list`() {
        assertEquals(5, PdfBankProfiles.all.size)
        assertEquals(
            listOf(Bank.NATWEST, Bank.MONZO, Bank.HSBC, Bank.HSBC, Bank.STARLING),
            PdfBankProfiles.all.map { it.bank },
        )
    }
}
