package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

class CsvParser {
    fun parseHeaders(content: String): List<String> {
        val first = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        return parseCsvRow(first)
    }

    fun parse(
        content: String,
        mapping: ColumnMapping,
    ): Result<List<ParsedTransaction>> =
        runCatching {
            // Single pass: filtering and dropping the header through the collection operators
            // would copy the whole line list twice more on a statement of any size.
            val lines = content.lines()
            val transactions = ArrayList<ParsedTransaction>(lines.size)
            var headerSeen = false
            for (line in lines) {
                if (line.isBlank()) continue
                if (!headerSeen) {
                    headerSeen = true
                    continue
                }
                parseRow(line, mapping)?.let { transactions.add(it) }
            }
            transactions
        }

    private fun parseRow(
        line: String,
        mapping: ColumnMapping,
    ): ParsedTransaction? {
        val cols = parseCsvRow(line)
        val dateStr = cols.getOrNull(mapping.dateIndex)?.trim() ?: return null
        val date = parseDate(dateStr, mapping.dateFormat) ?: return null
        val amount = resolveAmount(cols, mapping) ?: return null
        val description = cols.getOrNull(mapping.descriptionIndex)?.trim() ?: return null
        return ParsedTransaction(date = date, amount = amount, description = description, raw = line)
    }

    private fun resolveAmount(
        cols: List<String>,
        mapping: ColumnMapping,
    ): Double? =
        when {
            mapping.amountIndex != null -> {
                cols
                    .getOrNull(mapping.amountIndex)
                    ?.trim()
                    ?.replace(",", "")
                    ?.toDoubleOrNull()
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

            else -> {
                null
            }
        }

    private fun parseDate(
        dateStr: String,
        format: String,
    ): LocalDate? =
        try {
            when (format) {
                "dd/MM/yyyy" -> {
                    // Fixed-width dd/MM/yyyy with a consistent separator covers nearly every row
                    // and is read straight off the string, so no parts list is allocated per row.
                    val fixedWidth = dateStr.length == 10 &&
                        !dateStr[2].isDigit() && dateStr[2] == dateStr[5]
                    val parts = if (fixedWidth) null else dateStr.split('/', '-', '.')
                    if (parts != null && parts.size == 3) {
                        LocalDate(year = parts[2].toInt(), month = parts[1].toInt(), day = parts[0].toInt())
                    } else {
                        LocalDate(
                            year = dateStr.substring(6, 10).toInt(),
                            month = dateStr.substring(3, 5).toInt(),
                            day = dateStr.substring(0, 2).toInt(),
                        )
                    }
                }

                "yyyy-MM-dd" -> {
                    LocalDate(
                        year = dateStr.substring(0, 4).toInt(),
                        month = dateStr.substring(5, 7).toInt(),
                        day = dateStr.substring(8, 10).toInt(),
                    )
                }

                "MM/dd/yyyy" -> {
                    LocalDate(
                        year = dateStr.substring(6, 10).toInt(),
                        month = dateStr.substring(0, 2).toInt(),
                        day = dateStr.substring(3, 5).toInt(),
                    )
                }

                else -> {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }

    private fun parseCsvRow(line: String): List<String> {
        // The overwhelming majority of statement rows contain no quoted fields; those can be
        // cut straight out of the line rather than rebuilt a character at a time.
        if (line.indexOf('"') < 0) return splitUnquoted(line)
        return parseQuotedCsvRow(line)
    }

    private fun splitUnquoted(line: String): List<String> {
        val result = ArrayList<String>()
        var start = 0
        while (true) {
            val comma = line.indexOf(',', start)
            if (comma < 0) {
                result.add(line.substring(start))
                return result
            }
            result.add(line.substring(start, comma))
            start = comma + 1
        }
    }

    private fun parseQuotedCsvRow(line: String): List<String> {
        val result = ArrayList<String>()
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

                ',' -> {
                    if (!inQuotes) {
                        result.add(current.toString())
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }

                else -> {
                    current.append(char)
                }
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
