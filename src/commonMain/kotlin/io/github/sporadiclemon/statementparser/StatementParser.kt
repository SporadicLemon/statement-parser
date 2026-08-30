package io.github.sporadiclemon.statementparser

/** A four-digit year anywhere in the statement header, used when dates omit the year. */
private val STATEMENT_YEAR = Regex("""\b(20\d{2})\b""")

/** A UK eight-digit account number. */
private val ACCOUNT_NUMBER = Regex("""\b(\d{8})\b""")

/**
 * The main entry point for parsing bank statements in various formats.
 *
 * @param debugLogger Optional callback that receives verbose diagnostic messages at each stage of
 *   PDF parsing. Pass `::println` for quick debugging, or a logcat wrapper on Android. Null (default)
 *   produces no output.
 */
class StatementParser(private val debugLogger: ((String) -> Unit)? = null) {

    private val formatDetector = FormatDetector()
    private val csvParser = CsvParser()
    private val ofxParser = OFXParser()

    private fun log(msg: String) = debugLogger?.invoke(msg)

    /**
     * Returns a list of banks that have pre-defined CSV profiles.
     */
    fun getProfiledBanks(): List<Bank> =
        (CsvBankProfiles.all.map { it.bank } + PdfBankProfiles.all.map { it.bank }).distinct()

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
        val headerSet = headers.mapTo(HashSet(headers.size)) { it.trim().lowercase() }
        return CsvBankProfiles.all.firstOrNull { profile ->
            CsvBankProfiles.lowercaseSignature(profile).all { sig -> sig in headerSet }
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
     * Parses a PDF bank statement using the coordinate-based pipeline.
     *
     * @param bytes The raw bytes of the PDF file.
     * @param hintProfile An optional [PdfBankProfile] to check first, skipping the full profile scan.
     * @return A [Result] containing the [ParsedStatement].
     */
    fun parsePdf(bytes: ByteArray, hintProfile: PdfBankProfile? = null): Result<ParsedStatement> = runCatching {
        if (bytes.isEmpty()) throw IllegalArgumentException("PDF bytes must not be empty")

        val fragments = PdfTextExtractor().extract(bytes)
        log("[PDF] extracted ${fragments.size} fragments across ${fragments.maxOfOrNull { it.page }.let { if (it != null) it + 1 else 0 }} page(s)")
        if (fragments.isEmpty()) throw IllegalStateException("No text extracted from PDF")

        val profile = BankDetector(debugLogger).detect(fragments, hintProfile)
            ?: throw IllegalArgumentException("Unrecognised bank — no matching PDF profile found")
        log("[PDF] detected bank: ${profile.bank.displayName}")

        val year = if (!profile.dateIncludesYear) extractStatementYear(fragments) else null
        log("[PDF] statement year: $year (dateIncludesYear=${profile.dateIncludesYear})")

        val layout = ColumnDetector(debugLogger).detect(fragments, profile)
            ?: throw IllegalStateException("Could not detect table columns in PDF")
        log("[PDF] column layout: header on page ${layout.headerPage} y=${layout.headerY}")
        layout.columns.forEach { (role, range) -> log("[PDF]   $role → x=[${range.start}, ${range.endInclusive}]") }

        val rows = TableRowAssembler(debugLogger).assemble(fragments, layout)
        log("[PDF] assembled ${rows.size} rows")

        val transactions = PdfTransactionParser(debugLogger).parse(rows, profile, statementYear = year)
        log("[PDF] parsed ${transactions.size} transactions")

        ParsedStatement(
            transactions = transactions,
            accountInfoResult = extractAccountInfo(fragments, profile)?.let { AccountInfoResult.Found(it) }
                ?: AccountInfoResult.NotAvailable(AccountInfoUnavailableReason.MissingFromFile),
            detectedBank = profile.bank,
            suggestedMapping = null,
            rawHeaders = null,
        )
    }

    private fun extractStatementYear(fragments: List<TextFragment>): Int? =
        fragments.asSequence()
            .filter { it.page == 0 }
            .firstNotNullOfOrNull { STATEMENT_YEAR.find(it.text)?.groupValues?.get(1)?.toIntOrNull() }

    private fun extractAccountInfo(fragments: List<TextFragment>, profile: PdfBankProfile): ParsedAccountInfo? {
        val accountNumber = fragments.asSequence()
            .filter { it.page == 0 }
            .firstNotNullOfOrNull { ACCOUNT_NUMBER.find(it.text)?.groupValues?.get(1) }
            ?: return null
        return ParsedAccountInfo(
            institutionName = profile.bank.displayName,
            accountNumber = accountNumber,
        )
    }

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
