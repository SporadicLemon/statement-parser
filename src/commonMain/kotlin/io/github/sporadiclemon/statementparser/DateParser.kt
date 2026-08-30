package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.ExperimentalTime

object DateParser {

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    fun parse(text: String, format: String, yearHint: Int? = null): LocalDate? =
        try {
            when (format) {
                "dd/MM/yyyy" -> {
                    val parts = text.trim().split('/')
                    if (parts.size != 3) null
                    else LocalDate(year = parts[2].toInt(), month = parts[1].toInt(), day = parts[0].toInt())
                }
                "dd MMM" -> {
                    val parts = text.trim().split(" ")
                    val day = if (parts.size == 2) parts[0].toIntOrNull() else null
                    val month = if (parts.size == 2) MONTHS[parts[1].lowercase()] else null
                    if (day == null || month == null) null
                    else LocalDate(year = yearHint ?: currentYear(), month = month, day = day)
                }
                "dd MMM yyyy" -> {
                    val parts = text.trim().split(" ")
                    val day = if (parts.size == 3) parts[0].toIntOrNull() else null
                    val month = if (parts.size == 3) MONTHS[parts[1].lowercase()] else null
                    val year = if (parts.size == 3) parts[2].toIntOrNull() else null
                    if (day == null || month == null || year == null) null
                    else LocalDate(year = year, month = month, day = day)
                }
                "dd MMM yy" -> {
                    val parts = text.trim().split(" ")
                    val day = if (parts.size == 3) parts[0].toIntOrNull() else null
                    val month = if (parts.size == 3) MONTHS[parts[1].lowercase()] else null
                    val shortYear = if (parts.size == 3) parts[2].toIntOrNull() else null
                    if (day == null || month == null || shortYear == null) null
                    else LocalDate(year = 2000 + shortYear, month = month, day = day)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }

    @OptIn(ExperimentalTime::class)
    private fun currentYear(): Int =
        kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()).year
}
