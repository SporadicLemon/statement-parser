package io.github.sporadiclemon.statementparser

data class ColumnMapping(
    val dateIndex: Int,
    val dateFormat: String,
    val amountIndex: Int?,
    val amountInIndex: Int?,
    val amountOutIndex: Int?,
    val descriptionIndex: Int,
)

data class BankProfile(
    val name: String,
    val headerSignature: Set<String>,
    val mapping: ColumnMapping,
)
