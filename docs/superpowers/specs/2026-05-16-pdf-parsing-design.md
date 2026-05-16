# PDF Parsing — Design Spec

**Date:** 2026-05-16  
**Status:** Approved

## Overview

Add PDF bank statement parsing to `statement-parser`. Text-based PDFs only (no OCR). Initial platform support: Android and iOS (JVM deferred). Parsing uses bank-specific regex profiles, consistent with the existing CSV/OFX profile-based approach.

## Architecture

PDF parsing is a third parsing path alongside CSV and OFX, split into two layers:

- **Extraction (platform-specific):** `PdfTextExtractor` is an `expect class` with `actual` implementations per platform. Android uses PdfBox-Android; iOS uses the built-in PDFKit framework via Kotlin/Native.
- **Parsing (commonMain):** `PdfParser` takes extracted text and a bank profile, applies regex patterns, and returns a `ParsedStatement`.

PDFs are binary and cannot share the existing `parse(content: String, format: StatementFormat)` API. `StatementParser` gains a dedicated `parsePdf(bytes: ByteArray, bankHint: String? = null): Result<ParsedStatement>` method. `PdfTextExtractor` is internal — consumers never interact with it directly.

## New Types

### StatementFormat

```kotlin
enum class StatementFormat { CSV, OFX, PDF }
```

### PdfBankProfile

```kotlin
data class PdfBankProfile(
    val name: String,
    val bankNamePattern: Regex,        // detects which bank from extracted text
    val transactionLinePattern: Regex, // matches one transaction line
    val dateGroup: Int,
    val descriptionGroup: Int,
    val amountGroup: Int?,
    val amountInGroup: Int?,
    val amountOutGroup: Int?,
    val dateFormat: String,
)

object PdfBankProfiles {
    val all: List<PdfBankProfile> = listOf(/* Monzo, Starling, HSBC, Lloyds */)
}
```

Auto-detection scans extracted text against each profile's `bankNamePattern`. The `bankHint` parameter on `parsePdf` bypasses detection when the bank is already known (matched case-insensitively by `name`).

`PdfBankProfile` is separate from the existing `BankProfile` — PDF parsing uses free-text regex matching, not column indices.

## Platform Text Extraction

### expect (commonMain)

```kotlin
expect class PdfTextExtractor() {
    fun extractText(bytes: ByteArray): String
}
```

### Android actual (androidMain)

Dependency: `com.tom-roush:pdfbox-android:2.0.27.0` (Apache 2.0).

```kotlin
actual class PdfTextExtractor actual constructor() {
    actual fun extractText(bytes: ByteArray): String {
        val doc = PDDocument.load(bytes)
        return PDFTextStripper().getText(doc).also { doc.close() }
    }
}
```

### iOS actual (iosMain)

No new dependency — PDFKit is built into iOS 11+.

```kotlin
actual class PdfTextExtractor actual constructor() {
    actual fun extractText(bytes: ByteArray): String {
        val data = NSData.create(bytes = bytes, length = bytes.size.toULong())
        val doc = PDFDocument(data) ?: return ""
        return (0 until doc.pageCount())
            .mapNotNull { doc.pageAtIndex(it)?.string }
            .joinToString("\n")
    }
}
```

## PDF Parsing (commonMain)

`PdfParser` is internal and operates entirely on extracted text strings:

```kotlin
internal class PdfParser {
    private val extractor = PdfTextExtractor()

    fun parse(bytes: ByteArray, bankHint: String? = null): Result<ParsedStatement> = runCatching {
        val text = extractor.extractText(bytes)
        val profile = resolveProfile(text, bankHint)
            ?: throw IllegalArgumentException("No PDF bank profile matched")
        val transactions = parseTransactions(text, profile)
        ParsedStatement(
            transactions = transactions,
            accountInfoResult = AccountInfoResult.NotAvailable(AccountInfoUnavailableReason.MissingFromFile),
            detectedBank = null,
            suggestedMapping = null,
            rawHeaders = null,
        )
    }

    private fun resolveProfile(text: String, bankHint: String?): PdfBankProfile? =
        if (bankHint != null)
            PdfBankProfiles.all.firstOrNull { it.name.equals(bankHint, ignoreCase = true) }
        else
            PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
}
```

`parseTransactions` iterates lines of extracted text, applies `transactionLinePattern`, and maps regex groups to `ParsedTransaction` fields — the same logic shape as CSV column mapping.

## Public API Changes

`StatementParser` gains one new method; all existing methods are unchanged:

```kotlin
fun parsePdf(bytes: ByteArray, bankHint: String? = null): Result<ParsedStatement> =
    pdfParser.parse(bytes, bankHint)
```

`FormatDetector` gains PDF detection by file extension:

```kotlin
if (ext == "pdf") return StatementFormat.PDF
```

## Dependencies

- `libs.versions.toml`: add `pdfbox-android = "2.0.27.0"` and library alias `pdfbox-android`
- `build.gradle.kts` `androidMain.dependencies`: `implementation(libs.pdfbox.android)`
- No iOS dependency changes (PDFKit is built-in)

## Initial Bank Profiles

Monzo, Starling, HSBC, Lloyds — matching the existing CSV profile set where PDF statements are available for those banks.

## Testing

- `PdfParser` logic tested in `commonTest` using pre-extracted text strings — no PDF bytes required, keeping tests fast and platform-independent.
- `PdfTextExtractor` platform implementations are validated via manual/integration testing on device.
- `PdfBankProfiles` regex patterns tested against representative extracted text samples per bank.

## Out of Scope

- OCR for scanned/image PDFs
- JVM platform support (deferred)
- Generic/heuristic PDF parsing (bank profiles required)
