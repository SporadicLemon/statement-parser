package io.github.sporadiclemon.statementparser

/**
 * The main entry point for parsing bank statements in various formats.
 */
class StatementParser {

    private val formatDetector = FormatDetector()
    private val csvParser = CsvParser()
    private val ofxParser = OFXParser()

    /**
     * Returns a list of banks that have pre-defined CSV profiles.
     */
    fun getProfiledBanks(): List<Bank> =
        CsvBankProfiles.all.map { it.bank }.distinct()

    /**
     * Detects the format of a statement file based on its name and content.
     *
     * @param fileName The name of the file.
     * @param content The string content of the file.
     * @return The detected [StatementFormat].
     */
    fun detectFormat(fileName: String, content: String): StatementFormat =
        formatDetector.detect(fileName, content)

    /**
     * Detects a bank's CSV profile based on the column headers.
     *
     * @param headers The list of column headers.
     * @return The matching [CsvBankProfile], or null if none match.
     */
    fun detectBank(headers: List<String>): CsvBankProfile? {
        val headerSet = headers.map { it.trim().lowercase() }.toSet()
        return CsvBankProfiles.all.firstOrNull { profile ->
            profile.headerSignature.all { sig -> headerSet.contains(sig.lowercase()) }
        }
    }

    /**
     * Parses the string content of a statement (CSV or OFX).
     *
     * @param content The file content.
     * @param format The [StatementFormat] of the content.
     * @param mapping An optional custom [ColumnMapping] to use for CSV parsing.
     * @return A [Result] containing the [ParsedStatement].
     */
    fun parse(content: String, format: StatementFormat, mapping: ColumnMapping? = null): Result<ParsedStatement> =
        when (format) {
            StatementFormat.OFX -> ofxParser.parse(content)
            StatementFormat.CSV -> parseCsv(content, mapping)
            StatementFormat.PDF -> Result.failure(
                IllegalArgumentException("Use parsePdf(bytes) for PDF format")
            )
        }

    /**
     * Parses a PDF bank statement.
     *
     * @param bytes The raw bytes of the PDF file.
     * @return A failure result until the PDF pipeline is fully wired up.
     */
    fun parsePdf(bytes: ByteArray): Result<ParsedStatement> =
        Result.failure(UnsupportedOperationException("PDF pipeline not yet wired up"))

    private fun parseCsv(content: String, suppliedMapping: ColumnMapping?): Result<ParsedStatement> = runCatching {
        val headers = csvParser.parseHeaders(content)
        val bank = detectBank(headers)
        val resolvedMapping = suppliedMapping ?: bank?.mapping ?: guessMapping(headers)
        val transactions = csvParser.parse(content, resolvedMapping).getOrThrow()
        ParsedStatement(
            transactions = transactions,
            accountInfoResult = AccountInfoResult.NotAvailable(AccountInfoUnavailableReason.CsvFormat),
            detectedBank = bank?.bank,
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
