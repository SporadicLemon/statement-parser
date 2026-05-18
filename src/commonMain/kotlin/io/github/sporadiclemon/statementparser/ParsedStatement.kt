package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

data class ParsedTransaction(
    val date: LocalDate,
    val amount: Double,
    val description: String,
    val raw: String = "",
    val runningBalance: Double? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class ParsedStatement(
    val transactions: List<ParsedTransaction>,
    val accountInfo: ParsedAccountInfo?,
    val detectedBank: Bank?,
)

data class ParsedAccountInfo(
    val institutionName: String?,
    val accountNumber: String?,
)

data class ExistingTransaction(
    val date: LocalDate,
    val amount: Double,
    val description: String,
)

data class DuplicateCheckResult(
    val newTransactions: List<ParsedTransaction>,
    val duplicates: List<ParsedTransaction>,
)
