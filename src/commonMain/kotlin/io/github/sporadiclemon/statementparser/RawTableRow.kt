package io.github.sporadiclemon.statementparser

data class RawTableRow(
    val date: String?,
    val description: String,
    val amountIn: String?,
    val amountOut: String?,
    val amount: String?,
    val balance: String?,
    val pageY: Float,
)
