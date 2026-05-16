package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

internal class PdfParser(
    private val profiles: List<PdfBankProfile> = PdfBankProfiles.all,
) {
    private val extractor = PdfTextExtractor()

    fun parse(
        bytes: ByteArray,
        bankHint: String? = null,
    ): Result<ParsedStatement> =
        runCatching {
            val text = extractor.extractText(bytes)
            parseText(text, bankHint).getOrThrow()
        }

    internal fun parseText(
        text: String,
        bankHint: String?,
    ): Result<ParsedStatement> =
        runCatching {
            val profile =
                resolveProfile(text, bankHint)
                    ?: throw IllegalArgumentException(
                        "No PDF bank profile matched. Supported banks: ${profiles.joinToString { it.bank.displayName }}",
                    )
            val transactions = extractTransactions(text, profile)
            ParsedStatement(
                transactions = transactions,
                accountInfoResult = AccountInfoResult.NotAvailable(AccountInfoUnavailableReason.MissingFromFile),
                detectedBank = profile.bank,
                suggestedMapping = null,
                rawHeaders = null,
            )
        }

    private fun resolveProfile(
        text: String,
        bankHint: String?,
    ): PdfBankProfile? =
        if (bankHint != null) {
            profiles.firstOrNull { it.bank.displayName.equals(bankHint, ignoreCase = true) }
        } else {
            profiles.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        }

    private fun extractTransactions(
        text: String,
        profile: PdfBankProfile,
    ): List<ParsedTransaction> =
        profile.transactionLinePattern
            .findAll(text)
            .mapNotNull { match ->
                matchToTransaction(match, profile)
            }.toList()

    private fun matchToTransaction(
        match: MatchResult,
        profile: PdfBankProfile,
    ): ParsedTransaction? {
        val dateStr = match.groupValues.getOrNull(profile.dateGroup)?.trim() ?: return null
        val description = match.groupValues.getOrNull(profile.descriptionGroup)?.trim() ?: return null
        val amount: Double =
            when {
                profile.amountGroup != null -> {
                    val raw = match.groupValues.getOrNull(profile.amountGroup) ?: return null
                    parseAmount(raw) ?: return null
                }

                profile.amountInGroup != null && profile.amountOutGroup != null -> {
                    val inStr = match.groupValues.getOrNull(profile.amountInGroup)?.trim() ?: ""
                    val outStr = match.groupValues.getOrNull(profile.amountOutGroup)?.trim() ?: ""
                    when {
                        inStr.isNotBlank() -> parseAmount(inStr) ?: return null
                        outStr.isNotBlank() -> -(parseAmount(outStr) ?: return null)
                        else -> return null
                    }
                }

                else -> {
                    return null
                }
            }
        val date = parseDate(dateStr, profile.dateFormat) ?: return null
        return ParsedTransaction(date = date, amount = amount, description = description, raw = match.value)
    }

    private fun parseAmount(raw: String): Double? {
        val cleaned =
            raw
                .trim()
                .removePrefix("+")
                .replace("£", "")
                .replace(",", "")
        return cleaned.toDoubleOrNull()
    }

    private fun parseDate(
        dateStr: String,
        format: String,
    ): LocalDate? =
        try {
            when (format) {
                "dd/MM/yyyy" -> {
                    val parts = dateStr.split('/', '-', '.')
                    if (parts.size == 3) {
                        LocalDate(year = parts[2].toInt(), month = parts[1].toInt(), day = parts[0].toInt())
                    } else {
                        null
                    }
                }

                "yyyy-MM-dd" -> {
                    val parts = dateStr.split('-')
                    if (parts.size == 3) {
                        LocalDate(year = parts[0].toInt(), month = parts[1].toInt(), day = parts[2].toInt())
                    } else {
                        null
                    }
                }

                "dd MMM yyyy" -> {
                    parseLongMonthDate(dateStr, 4)
                }

                "dd MMM yy" -> {
                    parseLongMonthDate(dateStr, 2)
                }

                else -> {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }

    private fun parseLongMonthDate(
        dateStr: String,
        yearDigits: Int,
    ): LocalDate? {
        val parts = dateStr.trim().split(" ")
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = monthAbbr(parts[1]) ?: return null
        val year =
            when (yearDigits) {
                2 -> parts[2].toIntOrNull()?.let { if (it >= 50) 1900 + it else 2000 + it }
                else -> parts[2].toIntOrNull()
            } ?: return null
        return LocalDate(year = year, month = month, day = day)
    }

    private fun monthAbbr(abbr: String): Int? =
        when (abbr.lowercase()) {
            "jan" -> 1
            "feb" -> 2
            "mar" -> 3
            "apr" -> 4
            "may" -> 5
            "jun" -> 6
            "jul" -> 7
            "aug" -> 8
            "sep" -> 9
            "oct" -> 10
            "nov" -> 11
            "dec" -> 12
            else -> null
        }
}
