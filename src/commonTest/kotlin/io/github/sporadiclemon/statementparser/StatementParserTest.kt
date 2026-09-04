package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatementParserTest {

    private val parser = StatementParser()

    @Test
    fun `getProfiledBanks returns all CSV-profiled banks`() {
        val banks = parser.getProfiledBanks()
        assertTrue(banks.contains(Bank.MONZO))
        assertTrue(banks.contains(Bank.STARLING))
        assertTrue(banks.contains(Bank.BARCLAYS))
        assertTrue(banks.contains(Bank.HSBC))
        assertTrue(banks.contains(Bank.LLOYDS))
        assertTrue(banks.contains(Bank.NATWEST))
        assertTrue(banks.contains(Bank.SANTANDER))
    }

    @Test
    fun `detectFormat delegates to FormatDetector`() {
        assertEquals(StatementFormat.OFX, parser.detectFormat("statement.ofx", ""))
        assertEquals(StatementFormat.CSV, parser.detectFormat("statement.csv", ""))
    }

    @Test
    fun `detectBank returns Monzo profile for Monzo headers`() {
        val headers = listOf("Transaction ID", "Local amount", "Category split", "Money Out", "Money In")
        val profile = parser.detectBank(headers)
        assertEquals(Bank.MONZO, profile?.bank)
    }

    @Test
    fun `detectBank returns null for unknown headers`() {
        assertNull(parser.detectBank(listOf("Col1", "Col2", "Col3")))
    }

    @Test
    fun `detectBank resolves Monzo's real export despite incidentally satisfying Santander's generic signature`() {
        // Monzo's actual CSV export has many more columns than its 5-column signature needs -
        // three of the extras ("Date", "Description", "Amount") happen to be exactly Santander's
        // whole (much more generic) signature. Monzo's signature is still the more specific match
        // (5 columns vs Santander's 3), so it should win outright rather than being reported as
        // ambiguous - unlike the genuine HSBC/Santander tie below, where both signatures are the
        // same size and neither is more specific than the other.
        val headers = listOf(
            "Transaction ID", "Date", "Time", "Type", "Name", "Emoji", "Category", "Amount",
            "Currency", "Local amount", "Local currency", "Notes and #tags", "Address", "Receipt",
            "Description", "Category split", "Money Out", "Money In",
        )
        assertEquals(Bank.MONZO, parser.detectBank(headers)?.bank)
    }

    @Test
    fun `parse CSV auto-detects Starling`() {
        val csv = "Date,Counter Party,Reference,Type,Amount (GBP),Balance (GBP),Spending Category\n" +
                  "15/01/2024,Tesco,,FASTER_PAYMENT,-4.50,295.50,GROCERIES"
        val result = parser.parse(csv, StatementFormat.CSV).getOrThrow()
        assertEquals(1, result.transactions.size)
        assertEquals(Bank.STARLING, result.detectedBank)
        assertTrue(result.accountInfoResult is AccountInfoResult.NotAvailable)
    }

    @Test
    fun `parse CSV with unknown bank has null accountInfo`() {
        val csv = "Date,Merchant,Total\n15/01/2024,Coffee,-3.50"
        val result = parser.parse(csv, StatementFormat.CSV).getOrThrow()
        assertNull(result.detectedBank)
        assertTrue(result.accountInfoResult is AccountInfoResult.NotAvailable)
    }

    @Test
    fun `parse OFX returns accountInfo`() {
        val ofx = """<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS>
            <BANKACCTFROM><BANKID>112233</BANKID><ACCTID>99887766</ACCTID></BANKACCTFROM>
            <BANKTRANLIST>
            <STMTTRN><DTPOSTED>20240115</DTPOSTED><TRNAMT>-10.00</TRNAMT><NAME>Coffee</NAME></STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"""
        val result = parser.parse(ofx, StatementFormat.OFX).getOrThrow()
        assertTrue(result.accountInfoResult is AccountInfoResult.Found)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun `parsePdf returns failure for empty bytes`() {
        assertTrue(parser.parsePdf(ByteArray(0)).isFailure)
    }

    // --- parseDetailed / parsePdfDetailed: the richer result type ---

    @Test
    fun `parseDetailed reports Success when transactions were found`() {
        val csv = "Date,Counter Party,Reference,Type,Amount (GBP),Balance (GBP),Spending Category\n15/01/2024,Tesco,,FASTER_PAYMENT,-4.50,295.50,GROCERIES"
        val result = parser.parseDetailed(csv, StatementFormat.CSV)
        val success = assertIs<ParsedStatementResult.Success>(result)
        assertEquals(1, success.statement.transactions.size)
    }

    @Test
    fun `parseDetailed reports NoTransactionsFound for a CSV that matches a bank but parses nothing`() {
        // Every row's date is unparseable, so the bank is detected correctly but zero
        // transactions come out - exactly the shape that used to be indistinguishable from a
        // genuinely empty statement under the plain Result-returning API.
        val csv = "Date,Counter Party,Reference,Type,Amount (GBP),Balance (GBP),Spending Category\nnot-a-date,Tesco,,FASTER_PAYMENT,-4.50,295.50,GROCERIES"
        val result = parser.parseDetailed(csv, StatementFormat.CSV)
        val empty = assertIs<ParsedStatementResult.NoTransactionsFound>(result)
        assertEquals(Bank.STARLING, empty.statement.detectedBank)
        assertTrue(empty.statement.transactions.isEmpty())
    }

    @Test
    fun `parseDetailed reports NoTransactionsFound for an OFX file with no transaction blocks`() {
        val ofx = "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"
        val result = parser.parseDetailed(ofx, StatementFormat.OFX)
        assertIs<ParsedStatementResult.NoTransactionsFound>(result)
    }

    @Test
    fun `parseDetailed refuses to guess between two CSV bank profiles that both match`() {
        // HSBC's signature is {Description, Amount, Balance} and Santander's is {Date,
        // Description, Amount} - both are common enough column names that a header row
        // containing all four satisfies both signatures at once. Silently picking either would
        // mean parsing with a fixed column mapping that might not be this file's actual layout.
        val csv = "Date,Description,Amount,Balance\n15/01/2024,Coffee,-3.50,100.00"
        assertEquals(2, parser.detectBankCandidates(listOf("Date", "Description", "Amount", "Balance")).size)

        val result = parser.parseDetailed(csv, StatementFormat.CSV)
        val failure = assertIs<ParsedStatementResult.Failure>(result)
        val error = assertIs<StatementParseError.AmbiguousCsvBank>(failure.error)
        assertEquals(setOf(Bank.HSBC, Bank.SANTANDER), error.candidates.toSet())
    }

    @Test
    fun `legacy parse also fails on an ambiguous CSV bank match not just parseDetailed`() {
        // This is a deliberate behaviour change: previously detectBank silently picked the first
        // matching profile and parsed with its (possibly wrong) column positions. Failing loudly
        // is worth a previously-succeeding call now failing, since the alternative is parsing
        // with a mapping that may not match the file at all.
        val csv = "Date,Description,Amount,Balance\n15/01/2024,Coffee,-3.50,100.00"
        val result = parser.parse(csv, StatementFormat.CSV)
        assertTrue(result.isFailure)
        assertIs<StatementParseError.AmbiguousCsvBank>(result.exceptionOrNull())
    }

    @Test
    fun `detectBank returns null rather than guessing when multiple profiles match`() {
        assertNull(parser.detectBank(listOf("Date", "Description", "Amount", "Balance")))
    }

    @Test
    fun `parseDetailed on PDF format returns a specific named error`() {
        val result = parser.parseDetailed("irrelevant", StatementFormat.PDF)
        val failure = assertIs<ParsedStatementResult.Failure>(result)
        assertIs<StatementParseError.WrongParseFunctionForPdf>(failure.error)
    }

    @Test
    fun `parsePdfDetailed reports a structured error for empty bytes`() {
        val result = parser.parsePdfDetailed(ByteArray(0))
        val failure = assertIs<ParsedStatementResult.Failure>(result)
        assertIs<StatementParseError.EmptyInput>(failure.error)
    }

    @Test
    fun `parsePdfDetailed reports UnreadablePdf for bytes that are not a PDF at all`() {
        val result = parser.parsePdfDetailed("not a pdf".encodeToByteArray())
        val failure = assertIs<ParsedStatementResult.Failure>(result)
        assertIs<StatementParseError.UnreadablePdf>(failure.error)
    }

    @Test
    fun `toResult on parseDetailed matches what the legacy parse method returns`() {
        // The two entry points must agree: parse() is defined in terms of parseDetailed().
        val csv = "Date,Merchant,Total\n15/01/2024,Coffee,-3.50"
        val detailed = parser.parseDetailed(csv, StatementFormat.CSV)
        val legacy = parser.parse(csv, StatementFormat.CSV)
        assertEquals(legacy.isSuccess, detailed.toResult().isSuccess)
        assertEquals(legacy.getOrNull(), detailed.toResult().getOrNull())
    }
}
