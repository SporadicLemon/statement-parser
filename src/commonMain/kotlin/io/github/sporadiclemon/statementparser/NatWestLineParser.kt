package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

/**
 * Text-line based parser for NatWest PDF statements where PDFBox merges all
 * column data into a single text fragment per visual row.
 *
 * NatWest amount encoding in merged text (columns: Date | Description | Paid In | Withdrawn | Balance):
 *  - Credit (Paid In filled):  "...TEXT AMOUNT  BALANCE"  — ONE  space before AMOUNT, TWO  spaces before BALANCE
 *  - Debit (Withdrawn filled): "...TEXT  AMOUNT BALANCE"  — TWO  spaces before AMOUNT, ONE  space before BALANCE
 *  - Balance-only row:         "...TEXT   BALANCE"         — THREE or more spaces before BALANCE (BROUGHT FORWARD)
 */
internal object NatWestLineParser {

    // Matches a date at the start: "DD MMM" or "DD MMM YYYY"
    private val DATE_PREFIX_RE = Regex("""^(\d{2}\s+[A-Z]{3}(?:\s+\d{4})?)\s""")

    // Debit: two spaces before amount, one space before balance — at end of string
    private val DEBIT_RE = Regex("""  (\d[\d,]*\.\d{2}) (\d[\d,]*\.\d{2})$""")

    // Credit: one space before amount, two spaces before balance — at end of string
    private val CREDIT_RE = Regex(""" (\d[\d,]*\.\d{2})  (\d[\d,]*\.\d{2})$""")

    // Balance-only: three or more spaces before balance (no in/out amount)
    private val BALANCE_ONLY_RE = Regex("""   +(\d[\d,]*\.\d{2})$""")

    // Header row sentinel — the row containing all column header keywords
    private val HEADER_SENTINEL_RE = Regex("""Date.*Description.*Paid In.*Withdrawn.*Balance""", RegexOption.IGNORE_CASE)

    // Transaction-starting type prefixes (same as PdfBankProfiles.NATWEST.transactionTypePrefixes)
    private val TYPE_PREFIXES = listOf(
        "Automated Credit",
        "OnLine Transaction",
        "Direct Debit",
        "Standing Order",
        "ATM",
        "XFER",
    )

    fun parse(fragments: List<TextFragment>, statementYear: Int?): List<ParsedTransaction> {
        // Collect text rows from all fragments, in page+y order
        val rows = fragments
            .sortedWith(compareBy({ it.page }, { it.y }))
            .map { it.text.trimEnd() }

        // Find the header row and only process rows after it
        val headerIdx = rows.indexOfFirst { HEADER_SENTINEL_RE.containsMatchIn(it) }
        val dataRows = if (headerIdx >= 0) rows.drop(headerIdx + 1) else rows

        return parseRows(dataRows, statementYear)
    }

    private fun parseRows(rows: List<String>, statementYear: Int?): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Accumulator state
        var currentDate: LocalDate? = null
        var pendingDate: LocalDate? = null
        var pendingDescParts = mutableListOf<String>()

        fun flush(amount: Double, balance: Double?) {
            val d = pendingDate ?: return
            transactions.add(
                ParsedTransaction(
                    date = d,
                    description = pendingDescParts.joinToString(" ").trim(),
                    amount = amount,
                    runningBalance = balance,
                )
            )
            pendingDate = null
            pendingDescParts = mutableListOf()
        }

        for (raw in rows) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue

            // Skip footer / non-transaction rows (page headers, legal text, etc.)
            if (looksLikeNonTransactionRow(line)) continue

            // Check if this row ends with amounts
            val debit = DEBIT_RE.find(line)
            val credit = CREDIT_RE.find(line)
            val balanceOnly = BALANCE_ONLY_RE.find(line)

            // Extract date prefix if present
            val dateMatch = DATE_PREFIX_RE.find(line)
            if (dateMatch != null) {
                val dateText = dateMatch.groupValues[1]
                    .replace(Regex("""\s+\d{4}$"""), "").trim()  // strip year if present
                val parsed = DateParser.parse(dateText, "dd MMM", statementYear)
                if (parsed != null) currentDate = parsed
            }

            when {
                // ---- Row has a DEBIT amount at end ----
                debit != null -> {
                    val amount = debit.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
                    val balance = debit.groupValues[2].replace(",", "").toDoubleOrNull()
                    val descPart = line.substring(0, debit.range.first).trim()
                    val descWithoutDate = stripDatePrefix(descPart)

                    if (dateMatch != null || startsWithTypePrefix(descWithoutDate)) {
                        // New transaction entirely on this row
                        pendingDate = currentDate
                        pendingDescParts = mutableListOf(descWithoutDate)
                        flush(-amount, balance)
                    } else {
                        // Continuation row with amounts — close pending transaction
                        if (descWithoutDate.isNotBlank()) pendingDescParts.add(descWithoutDate)
                        flush(-amount, balance)
                    }
                }

                // ---- Row has a CREDIT amount at end ----
                credit != null -> {
                    val amount = credit.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
                    val balance = credit.groupValues[2].replace(",", "").toDoubleOrNull()
                    val descPart = line.substring(0, credit.range.first).trim()
                    val descWithoutDate = stripDatePrefix(descPart)

                    if (dateMatch != null || startsWithTypePrefix(descWithoutDate)) {
                        pendingDate = currentDate
                        pendingDescParts = mutableListOf(descWithoutDate)
                        flush(amount, balance)
                    } else {
                        if (descWithoutDate.isNotBlank()) pendingDescParts.add(descWithoutDate)
                        flush(amount, balance)
                    }
                }

                // ---- Balance-only row (e.g. BROUGHT FORWARD) — skip ----
                balanceOnly != null -> continue

                // ---- Row starts with date + type prefix: start of a new transaction ----
                dateMatch != null || startsWithTypePrefix(stripDatePrefix(line)) -> {
                    // Start new pending transaction (amounts will come on a later row)
                    val descWithoutDate = stripDatePrefix(line)
                    pendingDate = currentDate
                    pendingDescParts = mutableListOf(descWithoutDate)
                }

                // ---- Continuation row: description overflow ----
                else -> {
                    if (pendingDate != null) {
                        pendingDescParts.add(line.trim())
                    }
                }
            }
        }

        return transactions
    }

    private fun stripDatePrefix(text: String): String {
        val m = DATE_PREFIX_RE.find(text) ?: return text
        return text.substring(m.range.last + 1).trim()
    }

    private fun startsWithTypePrefix(text: String): Boolean =
        TYPE_PREFIXES.any { text.startsWith(it, ignoreCase = true) }

    private fun looksLikeNonTransactionRow(line: String): Boolean {
        // Skip legal footer lines, page headers, statement summary lines
        return line.startsWith("National Westminster Bank") ||
            line.startsWith("Authority and regulated") ||
            line.startsWith("RETSTMT") ||
            line.startsWith("Account Name") ||
            line.startsWith("STUDENT ACCOUNT") ||
            line.startsWith("MR ") ||
            line.contains("Registered in England and Wales") ||
            line.startsWith("Interest (variable)") ||
            line.startsWith("When you stay") ||
            line.startsWith("Amount Account") ||
            line.startsWith("Over £") ||
            line.startsWith("Select Account") ||
            line.startsWith("Summary") ||
            line.startsWith("Statement Date") ||
            line.startsWith("Period Covered") ||
            line.startsWith("Previous Balance") ||
            line.startsWith("Paid In £") ||
            line.startsWith("Withdrawn £") ||
            line.startsWith("New Balance") ||
            line.startsWith("BIC ") ||
            line.startsWith("IBAN ") ||
            line.startsWith("Welcome to") ||
            line.startsWith("Why file") ||
            line.startsWith("If you have changed") ||
            line.startsWith("Interest paid for") ||
            line.startsWith("95 ") ||     // address lines
            line.startsWith("LONDON") ||
            line.startsWith("SE")
    }
}
