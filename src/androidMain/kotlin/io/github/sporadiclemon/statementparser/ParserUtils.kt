package io.github.sporadiclemon.statementparser

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

object ParserUtils {
    private val CURRENCY_CLEANUP_REGEX = Regex("[£,+]")

    fun cleanCurrencyString(amount: String): String {
        return amount.replace(CURRENCY_CLEANUP_REGEX, "").trim()
    }

    fun parseBigDecimal(amount: String): BigDecimal? {
        val cleaned = cleanCurrencyString(amount)
        if (cleaned.isEmpty()) return null
        return try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun parseDate(dateText: String, pattern: String): LocalDate? {
        return try {
            val formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
            LocalDate.parse(dateText, formatter)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * For NatWest style "DD Mmm" dates that need a year hint.
     */
    fun parseDateWithYear(dateText: String, pattern: String, year: Int): LocalDate? {
        return try {
            val formatter = DateTimeFormatterBuilder()
                .appendPattern(pattern)
                .parseDefaulting(java.time.temporal.ChronoField.YEAR, year.toLong())
                .toFormatter(Locale.ENGLISH)
            LocalDate.parse(dateText, formatter)
        } catch (e: Exception) {
            null
        }
    }
}
