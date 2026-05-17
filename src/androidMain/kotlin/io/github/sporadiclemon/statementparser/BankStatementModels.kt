package io.github.sporadiclemon.statementparser

import java.math.BigDecimal
import java.time.LocalDate

enum class BankType { HSBC, NATWEST, HALIFAX, SANTANDER, MONZO, STARLING }

data class BankStatementTransaction(
    val date: LocalDate,
    val rawDateText: String,
    val description: String,
    val amount: BigDecimal, // Positive for Money In, Negative for Money Out
    val runningBalance: BigDecimal,
    val bankType: BankType,
    val metadata: Map<String, String> = emptyMap() // For internal codes like VIS, Pot flags, etc.
)

sealed interface LineParseResult {
    data class Success(val transaction: BankStatementTransaction) : LineParseResult
    object IgnoredRow : LineParseResult // For headers, footers, or systemic blank lines
    data class Failure(val error: Throwable) : LineParseResult
}

sealed interface StatementParseResult {
    data class Success(val transactions: List<BankStatementTransaction>) : StatementParseResult
    object NoTransactionsFound : StatementParseResult
    data class InvalidStatement(val reason: String) : StatementParseResult
}
