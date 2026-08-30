package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

/** Trailing year on a date cell ("12 Mar 2024"), stripped before format-specific parsing. */
private val TRAILING_YEAR = Regex("""\s+\d{4}$""")

/** How far, in points, a description-only row may sit from its amount row and still belong to it. */
private const val DESCRIPTION_Y_PROXIMITY = 15f

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

            var startsWithDate = false
            if (row.date != null) {
                val dateText = row.date.replace(TRAILING_YEAR, "").trim()
                val parsed = DateParser.parse(
                    text = dateText,
                    format = profile.dateFormat,
                    yearHint = statementYear,
                )
                logger?.invoke("[PdfTransactionParser] row[$rowIndex] date parse: \"$dateText\" format=${profile.dateFormat} yearHint=$statementYear → $parsed")
                if (parsed != null) {
                    currentDate = parsed
                    startsWithDate = true
                }
            }

            // A cell in the date column only opens a transaction if it is genuinely a date.
            // Prose below the table lands in that column too - a NatWest statement's small print
            // about transaction fees put "For charging periods..." there - and treating that as
            // the start of a transaction let a stray figure from the same prose become one.
            val isNewTransaction = startsWithDate ||
                profile.transactionTypePrefixes.any { row.description.startsWith(it, ignoreCase = true) }

            if (isNewTransaction) {
                pendingDescriptions = mutableListOf()
                pendingDate = currentDate
            }

            if (pendingDate != null) {
                if (row.description.isNotBlank()) pendingDescriptions.add(row.description)

                if (hasAmount) {
                    val amount = resolveAmount(row, profile.creditMarkerSuffix)
                    val balance = row.balance?.clean()?.toDoubleOrNull()
                    if (amount != null) {
                        val tx = ParsedTransaction(
                            date = pendingDate,
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
    // Uses y-proximity to associate description rows with their anchor.
    private fun parseAmountAnchor(
        rows: List<RawTableRow>,
        profile: PdfBankProfile,
        statementYear: Int?,
    ): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        rows.forEachIndexed { i, row ->
            val hasAmount = row.amountIn != null || row.amountOut != null || row.amount != null
            logger?.invoke("[PdfTransactionParser] row[$i] date=${row.date} hasAmount=$hasAmount desc=\"${row.description}\" in=${row.amountIn} out=${row.amountOut} amt=${row.amount} bal=${row.balance}")

            if (!hasAmount || row.date == null) return@forEachIndexed

            val dateText = row.date.replace(TRAILING_YEAR, "").trim()
            val date = DateParser.parse(
                text = dateText,
                format = profile.dateFormat,
                yearHint = statementYear,
            )
            logger?.invoke("[PdfTransactionParser] row[$i] date parse: \"$dateText\" format=${profile.dateFormat} → $date")
            if (date == null) return@forEachIndexed

            // Walk backward: collect description-only rows within threshold of this anchor.
            // Collected in reverse, then appended in reading order.
            val preceding = mutableListOf<String>()
            var j = i - 1
            while (j >= 0) {
                val prev = rows[j]
                if (row.pageY - prev.pageY > DESCRIPTION_Y_PROXIMITY) break
                val prevHasAmount = prev.amountIn != null || prev.amountOut != null || prev.amount != null
                if (prevHasAmount) break
                if (prev.description.isNotBlank()) preceding.add(prev.description)
                j--
            }

            val descParts = mutableListOf<String>()
            for (idx in preceding.indices.reversed()) descParts.add(preceding[idx])
            if (row.description.isNotBlank()) descParts.add(row.description)

            // Walk forward: collect description-only rows within threshold of this anchor
            var k = i + 1
            while (k < rows.size) {
                val next = rows[k]
                if (next.pageY - row.pageY > DESCRIPTION_Y_PROXIMITY) break
                val nextHasAmount = next.amountIn != null || next.amountOut != null || next.amount != null
                if (nextHasAmount) break
                if (next.description.isNotBlank()) descParts.add(next.description)
                k++
            }

            val amount = resolveAmount(row, profile.creditMarkerSuffix) ?: return@forEachIndexed
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

    private fun resolveAmount(row: RawTableRow, creditMarker: String?): Double? = when {
        row.amountIn  != null -> row.amountIn.clean().toDoubleOrNull()
        row.amountOut != null -> row.amountOut.clean().toDoubleOrNull()?.let { -it }
        row.amount    != null -> signedAmount(row.amount, creditMarker)
        else -> null
    }

    /**
     * Applies [PdfBankProfile.creditMarkerSuffix] to a single-amount-column cell: with a marker
     * set, the suffix means money in and its absence means money out, so an HSBC credit card's
     * "10.00" is a purchase (-10.00) and "10.00CR" a payment or refund (+10.00). Without a
     * marker the figure keeps whatever sign it was printed with.
     */
    private fun signedAmount(cell: String, creditMarker: String?): Double? {
        val text = cell.clean()
        if (creditMarker == null) return text.toDoubleOrNull()
        return if (text.endsWith(creditMarker, ignoreCase = true)) {
            text.dropLast(creditMarker.length).trim().toDoubleOrNull()
        } else {
            text.toDoubleOrNull()?.let { -it }
        }
    }

    // Strips thousands separators and the currency symbol in one pass, avoiding the
    // intermediate strings a trim + two replaces would allocate per cell.
    private fun String.clean(): String {
        var needsStrip = false
        for (c in this) if (c == ',' || c == '£') { needsStrip = true; break }
        if (!needsStrip) return trim()
        val sb = StringBuilder(length)
        for (c in this) if (c != ',' && c != '£') sb.append(c)
        return sb.toString().trim()
    }
}
