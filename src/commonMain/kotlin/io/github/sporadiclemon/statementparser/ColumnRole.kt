package io.github.sporadiclemon.statementparser

enum class ColumnRole { DATE, DESCRIPTION, AMOUNT_IN, AMOUNT_OUT, AMOUNT, BALANCE }

/**
 * The roles whose cells hold a money figure.
 *
 * Statements right-align these columns and left-align the text ones, so the two kinds are
 * anchored and matched on opposite edges - see [ColumnDetector] and [TableRowAssembler].
 */
internal val AMOUNT_ROLES = setOf(
    ColumnRole.AMOUNT_IN, ColumnRole.AMOUNT_OUT, ColumnRole.AMOUNT, ColumnRole.BALANCE,
)
