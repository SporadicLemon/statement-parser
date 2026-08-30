package io.github.sporadiclemon.statementparser

/**
 * The outcome of parsing a statement, as more than the two states [Result] offers.
 *
 * [Result.success]/[Result.failure] collapse two genuinely different situations into one
 * "success": a statement with real transactions in it, and one where the format or bank was
 * matched but not a single transaction came out - which is exactly the shape of a wrong
 * [ColumnMapping], a mismatched CSV bank profile, or a PDF profile applied to a table it
 * cannot actually read. Both looked identical to a caller of the plain [Result]-returning API,
 * which is precisely how a real, unmodified re-export of this library's own CSV output parsed
 * to zero transactions and reported success.
 *
 * Use [StatementParser.parseDetailed]/[StatementParser.parsePdfDetailed] to get this type;
 * [StatementParser.parse]/[StatementParser.parsePdf] still return `Result<ParsedStatement>` for
 * existing callers, collapsing [NoTransactionsFound] back into [Result.success] and [Failure]
 * into [Result.failure] - see [toResult].
 */
sealed class ParsedStatementResult {

    /** Parsing found at least one transaction. */
    data class Success(val statement: ParsedStatement) : ParsedStatementResult()

    /**
     * A format or bank was recognised and parsing completed without error, but not one
     * transaction was extracted. This is not necessarily wrong - a statement covering a period
     * with no activity is a real, if unusual, document - but it is exactly the shape a wrong
     * mapping, a bank misdetection, or a malformed file also produces, so it is surfaced
     * distinctly rather than silently folded into [Success] with an empty list.
     *
     * [statement] still carries whatever diagnostic information parsing gathered -
     * [ParsedStatement.detectedBank], [ParsedStatement.suggestedMapping] and
     * [ParsedStatement.rawHeaders] in particular - for a caller who wants to investigate why.
     */
    data class NoTransactionsFound(val statement: ParsedStatement) : ParsedStatementResult()

    /** Parsing could not proceed. [error] is a structured reason - see [StatementParseError]. */
    data class Failure(val error: StatementParseError) : ParsedStatementResult()

    /** True for [Success] and [NoTransactionsFound]; false for [Failure]. */
    val isSuccess: Boolean get() = this !is Failure

    /** True only for [Failure]. */
    val isFailure: Boolean get() = this is Failure

    /**
     * The parsed statement, whether or not it had any transactions in it. Null for [Failure].
     * For most callers this is the one field worth reading directly; the [NoTransactionsFound]
     * split exists for callers who specifically want to distinguish "empty" from "had data".
     */
    fun statementOrNull(): ParsedStatement? = when (this) {
        is Success -> statement
        is NoTransactionsFound -> statement
        is Failure -> null
    }

    /** The structured error, or null if this is not a [Failure]. */
    fun errorOrNull(): StatementParseError? = (this as? Failure)?.error

    /** Runs [action] with the statement when this is [Success], and returns this unchanged. */
    inline fun onSuccess(action: (ParsedStatement) -> Unit): ParsedStatementResult {
        if (this is Success) action(statement)
        return this
    }

    /**
     * Runs [action] with the (empty-transactions) statement when this is [NoTransactionsFound],
     * and returns this unchanged.
     */
    inline fun onEmpty(action: (ParsedStatement) -> Unit): ParsedStatementResult {
        if (this is NoTransactionsFound) action(statement)
        return this
    }

    /** Runs [action] with the error when this is [Failure], and returns this unchanged. */
    inline fun onFailure(action: (StatementParseError) -> Unit): ParsedStatementResult {
        if (this is Failure) action(error)
        return this
    }

    /** Exhaustively maps every variant to a single value of type [R]. */
    inline fun <R> fold(
        onSuccess: (ParsedStatement) -> R,
        onEmpty: (ParsedStatement) -> R,
        onFailure: (StatementParseError) -> R,
    ): R = when (this) {
        is Success -> onSuccess(statement)
        is NoTransactionsFound -> onEmpty(statement)
        is Failure -> onFailure(error)
    }

    /**
     * Collapses back to the two-state [Result] the rest of this library's public API still
     * returns: [NoTransactionsFound] becomes [Result.success] (an empty-transactions statement is
     * exactly what the older, two-state API already returned for this case, so this preserves its
     * behaviour rather than turning a previously-succeeding call into a failure), and [Failure]
     * becomes [Result.failure] with [StatementParseError] as the thrown value.
     */
    fun toResult(): Result<ParsedStatement> = when (this) {
        is Success -> Result.success(statement)
        is NoTransactionsFound -> Result.success(statement)
        is Failure -> Result.failure(error)
    }

    companion object {
        /**
         * Builds a [ParsedStatementResult] from a [Result], classifying an empty transaction list
         * as [NoTransactionsFound] rather than [Success]. A failure whose exception is already a
         * [StatementParseError] is used as-is; anything else is wrapped in
         * [StatementParseError.Unexpected] so [Failure.error] is always the structured type.
         */
        fun from(result: Result<ParsedStatement>): ParsedStatementResult =
            result.fold(
                onSuccess = { statement ->
                    if (statement.transactions.isEmpty()) NoTransactionsFound(statement) else Success(statement)
                },
                onFailure = { throwable ->
                    Failure(throwable as? StatementParseError ?: StatementParseError.Unexpected(throwable))
                },
            )
    }
}
