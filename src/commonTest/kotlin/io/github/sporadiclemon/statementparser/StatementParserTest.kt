package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatementParserTest {

    private val parser = StatementParser()

    // --- getProfiledBanks ---

    @Test
    fun `getProfiledBanks returns all banks with at least one profile`() {
        val banks = parser.getProfiledBanks()
        assertTrue(banks.contains(Bank.MONZO))
        assertTrue(banks.contains(Bank.STARLING))
        assertTrue(banks.contains(Bank.BARCLAYS))
        assertTrue(banks.contains(Bank.HSBC))
        assertTrue(banks.contains(Bank.LLOYDS))
        assertTrue(banks.contains(Bank.NATWEST))
        assertTrue(banks.contains(Bank.SANTANDER))
    }

    // --- detectFormat ---

    @Test
    fun `detectFormat delegates to FormatDetector`() {
        assertEquals(StatementFormat.OFX, parser.detectFormat("statement.ofx", ""))
        assertEquals(StatementFormat.CSV, parser.detectFormat("statement.csv", ""))
    }

    // --- detectBank ---

    @Test
    fun `detectBank returns Monzo profile for Monzo headers`() {
        val headers = listOf("Transaction ID", "Date", "Time", "Type", "Name", "Emoji", "Category",
            "Amount", "Currency", "Local amount", "Local currency", "Notes and #tags",
            "Address", "Receipt", "Description", "Category split", "Money Out", "Money In")
        val profile = parser.detectBank(headers)
        assertEquals(Bank.MONZO, profile?.bank)
    }

    @Test
    fun `detectBank returns null for unknown headers`() {
        assertNull(parser.detectBank(listOf("Col1", "Col2", "Col3")))
    }

    @Test
    fun `detectBank is case insensitive for header matching`() {
        val headers = listOf("transaction id", "money out", "money in")
        assertNotNull(parser.detectBank(headers))
    }

    // --- parse CSV with known bank ---

    @Test
    fun `parse CSV auto-detects Starling and parses transactions`() {
        // Starling: Date,Counter Party,Reference,Type,Amount (GBP),Balance (GBP),Spending Category
        val csv = "Date,Counter Party,Reference,Type,Amount (GBP),Balance (GBP),Spending Category\n" +
                  "15/01/2024,Tesco,,FASTER_PAYMENT,-4.50,295.50,GROCERIES"
        val result = parser.parse(csv, StatementFormat.CSV).getOrThrow()
        assertEquals(1, result.transactions.size)
        assertEquals(Bank.STARLING, result.detectedBank)
        assertIs<AccountInfoResult.NotAvailable>(result.accountInfoResult)
        assertEquals(AccountInfoUnavailableReason.CsvFormat,
            (result.accountInfoResult as AccountInfoResult.NotAvailable).reason)
    }

    @Test
    fun `parse CSV with unknown bank returns suggestedMapping and rawHeaders`() {
        val csv = "Date,Merchant,Total\n15/01/2024,Coffee,-3.50"
        val result = parser.parse(csv, StatementFormat.CSV).getOrThrow()
        assertNull(result.detectedBank)
        assertNotNull(result.suggestedMapping)
        assertEquals(listOf("Date", "Merchant", "Total"), result.rawHeaders)
    }

    // --- parse OFX ---

    @Test
    fun `parse OFX returns Found account info`() {
        val ofx = """<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS>
            <BANKACCTFROM><BANKID>112233</BANKID><ACCTID>99887766</ACCTID></BANKACCTFROM>
            <BANKTRANLIST>
            <STMTTRN><DTPOSTED>20240115</DTPOSTED><TRNAMT>-10.00</TRNAMT><NAME>Coffee</NAME></STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"""
        val result = parser.parse(ofx, StatementFormat.OFX).getOrThrow()
        assertIs<AccountInfoResult.Found>(result.accountInfoResult)
        assertEquals(1, result.transactions.size)
    }

    // --- parsePdf ---

    @Test
    fun `parsePdf returns failure for empty bytes`() {
        val result = parser.parsePdf(ByteArray(0), bankHint = "Monzo")
        assertTrue(result.isFailure)
    }
}
