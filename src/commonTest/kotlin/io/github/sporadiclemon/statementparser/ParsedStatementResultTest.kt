package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParsedStatementResultTest {

    private fun statement(transactions: List<ParsedTransaction>) = ParsedStatement(
        transactions = transactions,
        accountInfoResult = AccountInfoResult.NotAvailable(AccountInfoUnavailableReason.CsvFormat),
        detectedBank = Bank.MONZO,
        suggestedMapping = null,
        rawHeaders = null,
    )

    private val oneTransaction = ParsedTransaction(
        date = LocalDate(2024, 1, 15),
        amount = -4.50,
        description = "Coffee",
    )

    // --- from(Result) classification ---

    @Test
    fun `from a Result with transactions is Success`() {
        val result = ParsedStatementResult.from(Result.success(statement(listOf(oneTransaction))))
        assertIs<ParsedStatementResult.Success>(result)
        assertEquals(1, result.statement.transactions.size)
    }

    @Test
    fun `from a Result with no transactions is NoTransactionsFound, not Success`() {
        val result = ParsedStatementResult.from(Result.success(statement(emptyList())))
        assertIs<ParsedStatementResult.NoTransactionsFound>(result)
        assertTrue(result.statement.transactions.isEmpty())
    }

    @Test
    fun `from a failed Result carrying a StatementParseError uses it directly`() {
        val error = StatementParseError.UnrecognisedPdfBank
        val result = ParsedStatementResult.from(Result.failure(error))
        assertIs<ParsedStatementResult.Failure>(result)
        assertSame(error, result.error)
    }

    @Test
    fun `from a failed Result carrying an ordinary exception wraps it as Unexpected`() {
        val original = IllegalStateException("boom")
        val result = ParsedStatementResult.from(Result.failure(original))
        assertIs<ParsedStatementResult.Failure>(result)
        val error = assertIs<StatementParseError.Unexpected>(result.error)
        assertSame(original, error.cause)
    }

    // --- isSuccess / isFailure ---

    @Test
    fun `isSuccess is true for Success and NoTransactionsFound, false for Failure`() {
        assertTrue(ParsedStatementResult.Success(statement(listOf(oneTransaction))).isSuccess)
        assertTrue(ParsedStatementResult.NoTransactionsFound(statement(emptyList())).isSuccess)
        assertFalse(ParsedStatementResult.Failure(StatementParseError.UnrecognisedPdfBank).isSuccess)

        assertFalse(ParsedStatementResult.Success(statement(listOf(oneTransaction))).isFailure)
        assertTrue(ParsedStatementResult.Failure(StatementParseError.UnrecognisedPdfBank).isFailure)
    }

    // --- statementOrNull / errorOrNull ---

    @Test
    fun `statementOrNull is available for both success shapes and null for Failure`() {
        assertEquals(1, ParsedStatementResult.Success(statement(listOf(oneTransaction))).statementOrNull()?.transactions?.size)
        assertEquals(0, ParsedStatementResult.NoTransactionsFound(statement(emptyList())).statementOrNull()?.transactions?.size)
        assertNull(ParsedStatementResult.Failure(StatementParseError.UnrecognisedPdfBank).statementOrNull())
    }

    @Test
    fun `errorOrNull is only present for Failure`() {
        assertNull(ParsedStatementResult.Success(statement(listOf(oneTransaction))).errorOrNull())
        assertNull(ParsedStatementResult.NoTransactionsFound(statement(emptyList())).errorOrNull())
        assertSame(
            StatementParseError.UnrecognisedPdfBank,
            ParsedStatementResult.Failure(StatementParseError.UnrecognisedPdfBank).errorOrNull(),
        )
    }

    // --- fold / onSuccess / onEmpty / onFailure ---

    @Test
    fun `fold routes to the matching branch for each variant`() {
        fun describe(result: ParsedStatementResult) = result.fold(
            onSuccess = { "success:${it.transactions.size}" },
            onEmpty = { "empty" },
            onFailure = { "failure:${it::class.simpleName}" },
        )
        assertEquals("success:1", describe(ParsedStatementResult.Success(statement(listOf(oneTransaction)))))
        assertEquals("empty", describe(ParsedStatementResult.NoTransactionsFound(statement(emptyList()))))
        assertEquals(
            "failure:UnrecognisedPdfBank",
            describe(ParsedStatementResult.Failure(StatementParseError.UnrecognisedPdfBank)),
        )
    }

    @Test
    fun `onSuccess onEmpty and onFailure each fire only for their own variant`() {
        var successFired = false
        var emptyFired = false
        var failureFired = false

        ParsedStatementResult.Success(statement(listOf(oneTransaction)))
            .onSuccess { successFired = true }
            .onEmpty { emptyFired = true }
            .onFailure { failureFired = true }
        assertTrue(successFired)
        assertFalse(emptyFired)
        assertFalse(failureFired)
    }

    // --- toResult(): the backward-compatibility bridge ---

    @Test
    fun `toResult keeps Success as Result success`() {
        val result = ParsedStatementResult.Success(statement(listOf(oneTransaction))).toResult()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().transactions.size)
    }

    @Test
    fun `toResult collapses NoTransactionsFound into Result success, matching pre-existing behaviour`() {
        // This is the compatibility guarantee for parse()/parsePdf(): a statement that parses to
        // zero transactions must keep succeeding for existing callers, exactly as it always has -
        // only parseDetailed()/parsePdfDetailed() surface the distinction.
        val result = ParsedStatementResult.NoTransactionsFound(statement(emptyList())).toResult()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().transactions.isEmpty())
    }

    @Test
    fun `toResult turns Failure into Result failure carrying the StatementParseError`() {
        val error = StatementParseError.PdfTableNotFound(Bank.HSBC)
        val result = ParsedStatementResult.Failure(error).toResult()
        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
    }
}
