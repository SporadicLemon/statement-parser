package io.github.sporadiclemon.statementparser

import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NatWestPdfIntegrationTest {

    @Test
    fun parsesNatWestStatementCorrectly() {
        val context = InstrumentationRegistry.getInstrumentation().context
        PDFBoxResourceLoader.init(context)

        val bytes = context.assets.open("natwest_sample.pdf").readBytes()
        val result = StatementParser().parsePdf(bytes)

        assertTrue(result.isSuccess, "parsePdf should succeed: ${result.exceptionOrNull()?.message}")
        val statement = result.getOrThrow()

        // Bank detection
        assertEquals(Bank.NATWEST, statement.detectedBank)

        // Account info
        assertNotNull(statement.accountInfo)
        assertEquals("84318767", statement.accountInfo!!.accountNumber)

        // Transaction count: 14 transactions across all pages of the statement
        assertEquals(14, statement.transactions.size)

        // Spot-check: first credit on 07 APR
        val firstCredit = statement.transactions.first { it.amount > 0 }
        assertEquals(LocalDate(2026, 4, 7), firstCredit.date)
        assertEquals(25.0, firstCredit.amount, 0.01)
        assertTrue(firstCredit.runningBalance != null)

        // Spot-check: last transaction (Direct Debit HASTINGS INSURANCE on 01 MAY)
        val lastTxn = statement.transactions.last()
        assertEquals(LocalDate(2026, 5, 1), lastTxn.date)
        assertEquals(-41.30, lastTxn.amount, 0.01)
        assertEquals(62.39, lastTxn.runningBalance!!, 0.01)
    }
}
