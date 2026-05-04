package io.github.sporadiclemon.statementparser

class StatementParser {

    private val formatDetector = FormatDetector()
    private val csvParser = CsvParser()
    private val ofxParser = OFXParser()

    fun detectFormat(fileName: String, content: String): StatementFormat =
        formatDetector.detect(fileName, content)

    fun detectBank(headers: List<String>): BankProfile? {
        val headerSet = headers.map { it.trim().lowercase() }.toSet()
        return BankProfiles.all.firstOrNull { profile ->
            profile.headerSignature.all { sig -> headerSet.contains(sig.lowercase()) }
        }
    }

    fun parse(content: String, format: StatementFormat, mapping: ColumnMapping? = null): Result<ParsedStatement> =
        when (format) {
            StatementFormat.OFX -> ofxParser.parse(content)
            StatementFormat.CSV -> parseCsv(content, mapping)
        }

    private fun parseCsv(content: String, suppliedMapping: ColumnMapping?): Result<ParsedStatement> = runCatching {
        val headers = csvParser.parseHeaders(content)
        val bank = detectBank(headers)
        val resolvedMapping = suppliedMapping ?: bank?.mapping ?: guessMapping(headers)
        val transactions = csvParser.parse(content, resolvedMapping).getOrThrow()
        ParsedStatement(
            transactions = transactions,
            accountInfoResult = AccountInfoResult.NotAvailable(AccountInfoUnavailableReason.CsvFormat),
            detectedBank = bank,
            suggestedMapping = if (bank == null) resolvedMapping else null,
            rawHeaders = if (bank == null) headers else null,
        )
    }

    private fun guessMapping(headers: List<String>): ColumnMapping {
        val lower = headers.map { it.lowercase() }
        val dateIndex = lower.indexOfFirst { "date" in it }.coerceAtLeast(0)
        val descIndex = lower.indexOfFirst { "desc" in it || "name" in it || "merchant" in it || "narration" in it }
            .let { if (it < 0) 1 else it }
        val amountIndex = lower.indexOfFirst { it == "amount" || it == "value" }
            .let { if (it < 0) 2 else it }
        return ColumnMapping(
            dateIndex = dateIndex,
            dateFormat = "dd/MM/yyyy",
            amountIndex = amountIndex,
            amountInIndex = null,
            amountOutIndex = null,
            descriptionIndex = descIndex,
        )
    }
}
