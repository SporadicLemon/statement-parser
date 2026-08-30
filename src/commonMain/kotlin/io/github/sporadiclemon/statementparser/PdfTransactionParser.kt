package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

class PdfTransactionParser(private val logger: ((String) -> Unit)? = null) {

    fun parse(
        rows: List<RawTableRow>,
        profile: PdfBankProfile,
        statementYear: Int? = null,
        initialDate: LocalDate? = null,
    ): List<ParsedTransaction> = if (profile.descriptionSurroundsAmountRow) {
        logger?.invoke("[PdfTransactionParser] mode=amountAnchor (${rows.size} rows)")
        parseAmountAnchor(rows, profile, statementYear)
    } else {
        logger?.invoke("[PdfTransactionParser] mode=dateFirst (${rows.size} rows)")
        parseDateFirst(rows, profile, statementYear, initialDate)
    }

    // NatWest-style: date/prefix marks the start of a transaction, amount comes last.
    private fun parseDateFirst(
        rows: List<RawTableRow>,
        profile: PdfBankProfile,
        statementYear: Int?,
        initialDate: LocalDate?,
    ): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        var currentDate: LocalDate? = initialDate
        var pendingDescriptions = mutableListOf<String>()
        var pendingDate: LocalDate? = null

        for ((rowIndex, row) in rows.withIndex()) {
            val hasAmount = row.amountIn != null || row.amountOut != null || row.amount != null
            logger?.invoke("[PdfTransactionParser] row[$rowIndex] date=${row.date} desc=\"${row.description}\" in=${row.amountIn} out=${row.amountOut} amt=${row.amount} bal=${row.balance}")

            if (row.description.contains("BROUGHT FORWARD", ignoreCase = true) && !hasAmount) {
                logger?.invoke("[PdfTransactionParser] row[$rowIndex] skipped (BROUGHT FORWARD)")
                continue
            }

            if (row.date != null) {
                val dateText = row.date.replace(Regex("""\s+\d{4}$"""), "").trim()
                val parsed = DateParser.parse(
                    text = dateText,
                    format = profile.dateFormat,
                    yearHint = statementYear,
                )
                logger?.invoke("[PdfTransactionParser] row[$rowIndex] date parse: \"$dateText\" format=${profile.dateFormat} yearHint=$statementYear → $parsed")
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
                        val tx = ParsedTransaction(
                            date = pendingDate!!,
                            description = pendingDescriptions.joinToString(" ").trim(),
                            amount = amount,
                            runningBalance = balance,
                        )
                        logger?.invoke("[PdfTransactionParser] transaction: date=${tx.date} amount=${tx.amount} balance=${tx.runningBalance} desc=\"${tx.description}\"")
                        transactions.add(tx)
                    } else {
                        logger?.invoke("[PdfTransactionParser] row[$rowIndex] amount unresolvable (in=${row.amountIn} out=${row.amountOut} amt=${row.amount})")
                    }
                    pendingDescriptions = mutableListOf()
                    pendingDate = null
                }
            }
        }

        return transactions
    }

    // Monzo-style: the date+amount row is the anchor; description rows appear on either side.
    // Uses y-proximity (threshold 15pt) to associate description rows with their anchor.
    private fun parseAmountAnchor(
        rows: List<RawTableRow>,
        profile: PdfBankProfile,
        statementYear: Int?,
    ): List<ParsedTransaction> {
        val threshold = 15f
        val transactions = mutableListOf<ParsedTransaction>()

        rows.forEachIndexed { i, row ->
            val hasAmount = row.amountIn != null || row.amountOut != null || row.amount != null
            logger?.invoke("[PdfTransactionParser] row[$i] date=${row.date} hasAmount=$hasAmount desc=\"${row.description}\" in=${row.amountIn} out=${row.amountOut} amt=${row.amount} bal=${row.balance}")

            if (!hasAmount || row.date == null) return@forEachIndexed

            val dateText = row.date.replace(Regex("""\s+\d{4}$"""), "").trim()
            val date = DateParser.parse(
                text = dateText,
                format = profile.dateFormat,
                yearHint = statementYear,
            )
            logger?.invoke("[PdfTransactionParser] row[$i] date parse: \"$dateText\" format=${profile.dateFormat} → $date")
            if (date == null) return@forEachIndexed

            val descParts = mutableListOf<String>()
            if (row.description.isNotBlank()) descParts.add(row.description)

            // Walk backward: collect description-only rows within threshold of this anchor
            var j = i - 1
            while (j >= 0) {
                val prev = rows[j]
                if (row.pageY - prev.pageY > threshold) break
                val prevHasAmount = prev.amountIn != null || prev.amountOut != null || prev.amount != null
                if (prevHasAmount) break
                if (prev.description.isNotBlank()) descParts.add(0, prev.description)
                j--
            }

            // Walk forward: collect description-only rows within threshold of this anchor
            var k = i + 1
            while (k < rows.size) {
                val next = rows[k]
                if (next.pageY - row.pageY > threshold) break
                val nextHasAmount = next.amountIn != null || next.amountOut != null || next.amount != null
                if (nextHasAmount) break
                if (next.description.isNotBlank()) descParts.add(next.description)
                k++
            }

            val amount = resolveAmount(row) ?: return@forEachIndexed
            val balance = row.balance?.clean()?.toDoubleOrNull()

            val tx = ParsedTransaction(
                date = date,
                description = descParts.joinToString(" ").trim(),
                amount = amount,
                runningBalance = balance,
            )
            logger?.invoke("[PdfTransactionParser] transaction: date=${tx.date} amount=${tx.amount} balance=${tx.runningBalance} desc=\"${tx.description}\"")
            transactions.add(tx)
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
