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
                    val parts = text.trim().split('/', '-', '.')
                    if (parts.size != 3) null
                    else LocalDate(year = parts[2].toInt(), monthNumber = parts[1].toInt(), dayOfMonth = parts[0].toInt())
                }
                "dd MMM" -> {
                    val parts = text.trim().split(" ")
                    if (parts.size != 2) return null
                    val day = parts[0].toIntOrNull() ?: return null
                    val month = MONTHS[parts[1].lowercase()] ?: return null
                    LocalDate(year = yearHint ?: currentYear(), monthNumber = month, dayOfMonth = day)
                }
                "dd MMM yyyy" -> {
                    val parts = text.trim().split(" ")
                    if (parts.size != 3) return null
                    val day = parts[0].toIntOrNull() ?: return null
                    val month = MONTHS[parts[1].lowercase()] ?: return null
                    val year = parts[2].toIntOrNull() ?: return null
                    LocalDate(year = year, monthNumber = month, dayOfMonth = day)
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
