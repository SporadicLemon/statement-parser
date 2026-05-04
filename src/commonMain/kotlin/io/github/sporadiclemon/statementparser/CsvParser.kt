package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

class CsvParser {

    fun parseHeaders(content: String): List<String> {
        val first = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        return parseCsvRow(first)
    }

    fun parse(content: String, mapping: ColumnMapping): Result<List<ParsedTransaction>> = runCatching {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return@runCatching emptyList()
        lines.drop(1).mapNotNull { line -> parseRow(line, mapping) }
    }

    private fun parseRow(line: String, mapping: ColumnMapping): ParsedTransaction? {
        val cols = parseCsvRow(line)
        val dateStr = cols.getOrNull(mapping.dateIndex)?.trim() ?: return null
        val date = parseDate(dateStr, mapping.dateFormat) ?: return null
        val amount = resolveAmount(cols, mapping) ?: return null
        val description = cols.getOrNull(mapping.descriptionIndex)?.trim() ?: return null
        return ParsedTransaction(date = date, amount = amount, description = description, raw = line)
    }

    private fun resolveAmount(cols: List<String>, mapping: ColumnMapping): Double? {
        return when {
            mapping.amountIndex != null -> {
                cols.getOrNull(mapping.amountIndex)?.trim()?.replace(",", "")?.toDoubleOrNull()
            }
            mapping.amountInIndex != null && mapping.amountOutIndex != null -> {
                val inStr = cols.getOrNull(mapping.amountInIndex)?.trim()?.replace(",", "")
                val outStr = cols.getOrNull(mapping.amountOutIndex)?.trim()?.replace(",", "")
                
                val inAmt = inStr?.toDoubleOrNull() ?: 0.0
                val outAmt = outStr?.toDoubleOrNull() ?: 0.0
                
                when {
                    inAmt > 0.0 -> inAmt
                    outAmt > 0.0 -> -outAmt
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun parseDate(dateStr: String, format: String): LocalDate? = try {
        when (format) {
            "dd/MM/yyyy" -> {
                val parts = dateStr.split('/', '-', '.')
                if (parts.size == 3) {
                    LocalDate(year = parts[2].toInt(), month = parts[1].toInt(), day = parts[0].toInt())
                } else {
                    LocalDate(
                        year = dateStr.substring(6, 10).toInt(),
                        month = dateStr.substring(3, 5).toInt(),
                        day = dateStr.substring(0, 2).toInt(),
                    )
                }
            }
            "yyyy-MM-dd" -> LocalDate(
                year = dateStr.substring(0, 4).toInt(),
                month = dateStr.substring(5, 7).toInt(),
                day = dateStr.substring(8, 10).toInt(),
            )
            "MM/dd/yyyy" -> LocalDate(
                year = dateStr.substring(6, 10).toInt(),
                month = dateStr.substring(0, 2).toInt(),
                day = dateStr.substring(3, 5).toInt(),
            )
            else -> null
        }
    } catch (_: Exception) { null }

    private fun parseCsvRow(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            when (val char = line[i]) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> if (!inQuotes) {
                    result.add(current.toString())
                    current.clear()
                } else {
                    current.append(char)
                }
                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
