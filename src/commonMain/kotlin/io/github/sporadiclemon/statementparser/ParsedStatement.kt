package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

/**
 * Represents the final result of a parsed statement.
 *
 * @property transactions A list of [ParsedTransaction]s found in the statement.
 * @property accountInfoResult The result of trying to find account-level information (e.g., account number).
 * @property detectedBank The [Bank] detected during parsing, if any.
 * @property suggestedMapping A [ColumnMapping] suggested by the parser if no bank was detected (CSV only).
 * @property rawHeaders The raw column headers found in the statement (CSV only).
 */
data class ParsedStatement(
    val transactions: List<ParsedTransaction>,
    val accountInfoResult: AccountInfoResult,
    val detectedBank: Bank?,
    val suggestedMapping: ColumnMapping?,
    val rawHeaders: List<String>?,
)

/**
 * Represents a single transaction parsed from a statement.
 *
 * @property date The date of the transaction.
 * @property amount The amount of the transaction. Positive for income, negative for expenses.
 * @property description The transaction description or merchant name.
 * @property raw The raw line or row text from which this transaction was parsed.
 * @property runningBalance The running balance after this transaction, if available.
 * @property metadata Additional bank-specific information.
 */
data class ParsedTransaction(
    val date: LocalDate,
    val amount: Double,
    val description: String,
    val raw: String = "",
    val runningBalance: Double? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Represents the result of an attempt to extract account info.
 */
sealed class AccountInfoResult {
    /**
     * Account information was successfully found.
     *
     * @property info The [ParsedAccountInfo] that was extracted.
     */
    data class Found(
        val info: ParsedAccountInfo,
    ) : AccountInfoResult()

    /**
     * Account information was not available.
     *
     * @property reason The [AccountInfoUnavailableReason] explaining why it's missing.
     */
    data class NotAvailable(
        val reason: AccountInfoUnavailableReason,
    ) : AccountInfoResult()
}

/**
 * Reasons why account information might be unavailable.
 */
enum class AccountInfoUnavailableReason {
    /** The format (like CSV) typically doesn't contain account info in the headers. */
    CsvFormat,
    /** The information was expected but could not be found in the file. */
    MissingFromFile
}

/**
 * Extracted account-level information.
 *
 * @property institutionName The name of the financial institution.
 * @property accountNumber The account number, if extracted.
 */
data class ParsedAccountInfo(
    val institutionName: String?,
    val accountNumber: String?,
)

/**
 * Represents an existing transaction for duplicate checking.
 */
data class ExistingTransaction(
    val date: LocalDate,
    val amount: Double,
    val description: String,
)

/**
 * The result of a duplicate check.
 */
data class DuplicateCheckResult(
    val newTransactions: List<ParsedTransaction>,
    val duplicates: List<ParsedTransaction>,
)
