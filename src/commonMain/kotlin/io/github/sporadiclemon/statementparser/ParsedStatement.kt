package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

data class ParsedStatement(
    val transactions: List<ParsedTransaction>,
    val accountInfoResult: AccountInfoResult,
    val detectedBank: BankProfile?,
    val suggestedMapping: ColumnMapping?,
    val rawHeaders: List<String>?,
)

data class ParsedTransaction(
    val date: LocalDate,
    val amount: Double,
    val description: String,
    val raw: String,
)

sealed class AccountInfoResult {
    data class Found(val info: ParsedAccountInfo) : AccountInfoResult()
    data class NotAvailable(val reason: AccountInfoUnavailableReason) : AccountInfoResult()
}

enum class AccountInfoUnavailableReason { CsvFormat, MissingFromFile }

data class ParsedAccountInfo(val institutionName: String?, val accountNumber: String?)

data class ExistingTransaction(val date: LocalDate, val amount: Double, val description: String)

data class DuplicateCheckResult(val newTransactions: List<ParsedTransaction>, val duplicates: List<ParsedTransaction>)
