package io.github.sporadiclemon.statementparser

import java.math.BigDecimal

interface BankStatementParser {
    fun parseDocument(
        lines: List<String>,
        bankType: BankType,
        statementYearHint: Int?
    ): StatementParseResult
}

class PdfBankStatementParser : BankStatementParser {

    override fun parseDocument(
        lines: List<String>,
        bankType: BankType,
        statementYearHint: Int?
    ): StatementParseResult {
        val processor = when (bankType) {
            BankType.HSBC -> HsbcProcessor()
            BankType.NATWEST -> NatWestProcessor(statementYearHint)
            BankType.MONZO -> MonzoProcessor()
            BankType.HALIFAX -> HalifaxProcessor()
            BankType.SANTANDER -> SantanderProcessor()
            BankType.STARLING -> StarlingProcessor()
        }

        val transactions = processor.process(lines)

        return if (transactions.isEmpty()) {
            StatementParseResult.NoTransactionsFound
        } else {
            StatementParseResult.Success(transactions)
        }
    }
}

private abstract class BankProcessor {
    abstract fun process(lines: List<String>): List<BankStatementTransaction>

    protected fun cleanDescription(description: String): String {
        return description.replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * HSBC Column Layout: [Date] [Payment type and details] [£ Paid out] [£ Paid in] [£ Balance]
 * Date Format: "DD Mmm YY"
 */
private class HsbcProcessor : BankProcessor() {
    private val regex = Regex("""^(\d{2} \w{3} \d{2})\s+(.+?)\s+([\d,.]*)\s+([\d,.]*)\s+([\d,.]*)$""")

    override fun process(lines: List<String>): List<BankStatementTransaction> {
        return lines.mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val (dateText, desc, paidOut, paidIn, balance) = match.destructured

            val date = ParserUtils.parseDate(dateText, "dd MMM yy") ?: return@mapNotNull null
            val outAmt = ParserUtils.parseBigDecimal(paidOut)
            val inAmt = ParserUtils.parseBigDecimal(paidIn)
            val balAmt = ParserUtils.parseBigDecimal(balance) ?: BigDecimal.ZERO

            val finalAmount = when {
                outAmt != null -> outAmt.negate()
                inAmt != null -> inAmt
                else -> return@mapNotNull null
            }

            BankStatementTransaction(
                date = date,
                rawDateText = dateText,
                description = cleanDescription(desc),
                amount = finalAmount,
                runningBalance = balAmt,
                bankType = BankType.HSBC
            )
        }
    }
}

/**
 * NATWEST Column Layout: [Date] [Type] [Description] [Paid out] [Paid in] [Balance]
 * Date Format: "DD Mmm"
 */
private class NatWestProcessor(private val yearHint: Int?) : BankProcessor() {
    private val regex = Regex("""^(\d{2} \w{3})\s+(\w{2,3})\s+(.+?)\s+([\d,.]*)\s+([\d,.]*)\s+([\d,.]*)$""")

    override fun process(lines: List<String>): List<BankStatementTransaction> {
        val year = yearHint ?: java.time.LocalDate.now().year
        return lines.mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val (dateText, type, desc, paidOut, paidIn, balance) = match.destructured

            val date = ParserUtils.parseDateWithYear(dateText, "dd MMM", year) ?: return@mapNotNull null
            val outAmt = ParserUtils.parseBigDecimal(paidOut)
            val inAmt = ParserUtils.parseBigDecimal(paidIn)
            val balAmt = ParserUtils.parseBigDecimal(balance) ?: BigDecimal.ZERO

            val finalAmount = when {
                outAmt != null -> outAmt.negate()
                inAmt != null -> inAmt
                else -> return@mapNotNull null
            }

            BankStatementTransaction(
                date = date,
                rawDateText = dateText,
                description = cleanDescription(desc),
                amount = finalAmount,
                runningBalance = balAmt,
                bankType = BankType.NATWEST,
                metadata = mapOf("type" to type)
            )
        }
    }
}

/**
 * MONZO Column Layout: [Date] [Description] [Amount (GBP)] [Balance]
 * Date Format: "DD Mmm YYYY, HH:MM" or "DD Mmm YYYY"
 */
private class MonzoProcessor : BankProcessor() {
    private val regex = Regex("""^(\d{2} \w{3} \d{4}(?:, \d{2}:\d{2})?)\s+(.+?)\s+(-?[\d,.]+)\s+([\d,.]+)$""")

    override fun process(lines: List<String>): List<BankStatementTransaction> {
        return lines.mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val (dateText, desc, amountText, balanceText) = match.destructured

            val pattern = if (dateText.contains(",")) "dd MMM yyyy, HH:mm" else "dd MMM yyyy"
            val date = ParserUtils.parseDate(dateText, pattern) ?: return@mapNotNull null
            val amount = ParserUtils.parseBigDecimal(amountText) ?: return@mapNotNull null
            val balance = ParserUtils.parseBigDecimal(balanceText) ?: BigDecimal.ZERO

            val metadata = mutableMapOf<String, String>()
            if (desc.contains("To Pot", ignoreCase = true) || desc.contains("From Pot", ignoreCase = true)) {
                metadata["transfer_type"] = "internal_ledger"
            }

            BankStatementTransaction(
                date = date,
                rawDateText = dateText,
                description = cleanDescription(desc),
                amount = amount,
                runningBalance = balance,
                bankType = BankType.MONZO,
                metadata = metadata
            )
        }
    }
}

/**
 * HALIFAX Column Structure: [Date] [Description] [Money Out] [Money In] [Balance]
 * Date Format: "DD Mmm YY"
 */
private class HalifaxProcessor : BankProcessor() {
    private val regex = Regex("""^(\d{2} \w{3} \d{2})\s+(.+?)\s+([\d,.]*)\s+([\d,.]*)\s+([\d,.]*)$""")

    override fun process(lines: List<String>): List<BankStatementTransaction> {
        return lines.mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val (dateText, desc, paidOut, paidIn, balance) = match.destructured

            val date = ParserUtils.parseDate(dateText, "dd MMM yy") ?: return@mapNotNull null
            val outAmt = ParserUtils.parseBigDecimal(paidOut)
            val inAmt = ParserUtils.parseBigDecimal(paidIn)
            val balAmt = ParserUtils.parseBigDecimal(balance) ?: BigDecimal.ZERO

            val finalAmount = when {
                outAmt != null -> outAmt.negate()
                inAmt != null -> inAmt
                else -> return@mapNotNull null
            }

            BankStatementTransaction(
                date = date,
                rawDateText = dateText,
                description = cleanDescription(desc),
                amount = finalAmount,
                runningBalance = balAmt,
                bankType = BankType.HALIFAX
            )
        }
    }
}

/**
 * SANTANDER Column Structure: [Date] [Description] [Paid Out] [Paid In] [Balance]
 * Date Format: "DD/MM/YYYY"
 * Multi-line Quirk: Line 1 is payment method, Line 2 is merchant.
 */
private class SantanderProcessor : BankProcessor() {
    private val dateRegex = Regex("""^(\d{2}/\d{2}/\d{4})\s+(.*)$""")
    private val amountRegex = Regex("""\s+([\d,.]*)\s+([\d,.]*)\s+([\d,.]*)$""")

    override fun process(lines: List<String>): List<BankStatementTransaction> {
        val transactions = mutableListOf<BankStatementTransaction>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val dateMatch = dateRegex.find(line)
            if (dateMatch != null) {
                val dateText = dateMatch.groupValues[1]
                val line1Desc = dateMatch.groupValues[2]
                
                // Try to find amounts on this line or next line
                var currentLineContent = line
                var amountMatch = amountRegex.find(currentLineContent)
                
                var description = line1Desc
                var nextLineIndex = i + 1
                
                // If no amounts on current line, or it looks like a multi-line description
                if (nextLineIndex < lines.size) {
                    val nextLine = lines[nextLineIndex]
                    // If next line doesn't start with a date, it's likely the second part of description
                    if (!dateRegex.containsMatchIn(nextLine)) {
                        val nextLineAmountMatch = amountRegex.find(nextLine)
                        if (nextLineAmountMatch != null) {
                            // Extract description from next line (everything before amounts)
                            val descPart2 = nextLine.substring(0, nextLineAmountMatch.range.first).trim()
                            description = "$description $descPart2"
                            amountMatch = nextLineAmountMatch
                            i++ // Skip next line as we consumed it
                        } else {
                            description = "$description $nextLine"
                            i++
                            // Look for amounts on the line after that? Let's keep it simple for now.
                        }
                    }
                }

                if (amountMatch != null) {
                    val (paidOut, paidIn, balance) = amountMatch.destructured
                    val date = ParserUtils.parseDate(dateText, "dd/MM/yyyy")
                    val outAmt = ParserUtils.parseBigDecimal(paidOut)
                    val inAmt = ParserUtils.parseBigDecimal(paidIn)
                    val balAmt = ParserUtils.parseBigDecimal(balance) ?: BigDecimal.ZERO

                    if (date != null) {
                        val finalAmount = when {
                            outAmt != null -> outAmt.negate()
                            inAmt != null -> inAmt
                            else -> null
                        }
                        
                        if (finalAmount != null) {
                            transactions.add(
                                BankStatementTransaction(
                                    date = date,
                                    rawDateText = dateText,
                                    description = cleanDescription(description),
                                    amount = finalAmount,
                                    runningBalance = balAmt,
                                    bankType = BankType.SANTANDER
                                )
                            )
                        }
                    }
                }
            }
            i++
        }
        return transactions
    }
}

/**
 * STARLING Column Structure: [Date] [Description] [Amount] [Balance]
 * Date Format: "DD/MM/YYYY"
 */
private class StarlingProcessor : BankProcessor() {
    private val regex = Regex("""^(\d{2}/\d{2}/\d{4})\s+(.+?)\s+(-?[\d,.]+)\s+([\d,.]+)$""")

    override fun process(lines: List<String>): List<BankStatementTransaction> {
        return lines.mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val (dateText, desc, amountText, balanceText) = match.destructured

            val date = ParserUtils.parseDate(dateText, "dd/MM/yyyy") ?: return@mapNotNull null
            val amount = ParserUtils.parseBigDecimal(amountText) ?: return@mapNotNull null
            val balance = ParserUtils.parseBigDecimal(balanceText) ?: BigDecimal.ZERO

            BankStatementTransaction(
                date = date,
                rawDateText = dateText,
                description = cleanDescription(desc),
                amount = amount,
                runningBalance = balance,
                bankType = BankType.STARLING
            )
        }
    }
}
