package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

class PdfTransactionParser {

    fun parse(
        rows: List<RawTableRow>,
        profile: PdfBankProfile,
        statementYear: Int? = null,
        initialDate: LocalDate? = null,
    ): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        var currentDate: LocalDate? = initialDate
        var pendingDescriptions = mutableListOf<String>()
        var pendingDate: LocalDate? = null

        for (row in rows) {
            val hasAmount = row.amountIn != null || row.amountOut != null || row.amount != null

            if (row.description.contains("BROUGHT FORWARD", ignoreCase = true) && !hasAmount) {
                continue
            }

            if (row.date != null) {
                val dateText = row.date.replace(Regex("""\s+\d{4}$"""), "").trim()
                val parsed = DateParser.parse(
                    text = dateText,
                    format = if (profile.dateIncludesYear) "dd MMM yyyy" else profile.dateFormat,
                    yearHint = statementYear,
                )
                if (parsed != null) currentDate = parsed
            }

            val isNewTransaction = row.date != null ||
                profile.transactionTypePrefixes.any { row.description.startsWith(it, ignoreCase = true) }

            if (isNewTransaction) {
                pendingDescriptions = mutableListOf()
                pendingDate = currentDate
            }

            if (pendingDate != null) {
                if (row.description.isNotBlank()) pendingDescriptions.add(row.description)

                if (hasAmount) {
                    val amount = resolveAmount(row)
                    val balance = row.balance?.clean()?.toDoubleOrNull()
                    if (amount != null) {
                        transactions.add(
                            ParsedTransaction(
                                date = pendingDate!!,
                                description = pendingDescriptions.joinToString(" ").trim(),
                                amount = amount,
                                runningBalance = balance,
                            )
                        )
                    }
                    pendingDescriptions = mutableListOf()
                    pendingDate = null
                }
            }
        }

        return transactions
    }

    private fun resolveAmount(row: RawTableRow): Double? = when {
        row.amountIn  != null -> row.amountIn.clean().toDoubleOrNull()
        row.amountOut != null -> row.amountOut.clean().toDoubleOrNull()?.let { -it }
        row.amount    != null -> row.amount.clean().toDoubleOrNull()
        else -> null
    }

    private fun String.clean() = trim().replace(",", "").replace("£", "")
}
