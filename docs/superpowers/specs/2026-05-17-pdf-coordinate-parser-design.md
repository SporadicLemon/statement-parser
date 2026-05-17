# PDF Coordinate-Based Parser — Design Spec
_Date: 2026-05-17_

## Problem

The current PDF parsing layer extracts plain text from PDFs and applies per-bank regex processors. This approach discards spatial (x/y) information that PDFs inherently contain, forcing the code to use whitespace-gap heuristics to distinguish "Paid In" from "Withdrawn" amounts and to handle multi-line descriptions. These heuristics are fragile and break across different statement versions.

The NatWest format makes this concrete: a transaction's description wraps across 2–3 text lines, and the amount columns are only distinguishable by their horizontal position in the original PDF.

## Approach

Replace the plain-text extraction + per-bank processor pattern with a coordinate-aware pipeline. Each stage of the pipeline operates on `commonMain` Kotlin — only the PDF byte extraction has platform `actual` implementations.

---

## Pipeline

```
PDF bytes
  → PdfTextExtractor        → List<TextFragment>
  → BankDetector            → PdfBankProfile
  → ColumnDetector          → ColumnLayout
  → TableRowAssembler       → List<RawTableRow>
  → TransactionParser       → List<ParsedTransaction>
```

---

## Stage 1: TextFragment & PdfTextExtractor

### TextFragment (commonMain)

```kotlin
data class TextFragment(
    val text: String,
    val x: Float,
    val y: Float,
    val page: Int,
)
```

Word-level granularity. Each word has its top-left x/y position and the page index it came from.

### PdfTextExtractor (expect/actual)

```kotlin
expect object PdfTextExtractor {
    fun extract(bytes: ByteArray): List<TextFragment>
}
```

| Platform | Implementation |
|----------|---------------|
| Android  | PdfBox-Android — subclass `PDFTextStripper`, override `writeString` to capture `TextPosition` x/y per word |
| iOS      | PDFKit — iterate `PDFPage`, use `characterBounds(at:)` to get per-character bounds, group into words. Requires iOS 16+; a plain-text fallback is used on iOS 11–15. |
| JVM      | Apache PDFBox (full) — same approach as Android |

---

## Stage 2: BankDetector & PdfBankProfile

### ColumnRole (commonMain)

```kotlin
enum class ColumnRole { DATE, DESCRIPTION, AMOUNT_IN, AMOUNT_OUT, AMOUNT, BALANCE }
```

`AMOUNT_IN`/`AMOUNT_OUT` covers banks with split debit/credit columns (NatWest, HSBC, Halifax).
`AMOUNT` covers banks with a single signed amount column (Monzo, Starling).

### PdfBankProfile (commonMain)

```kotlin
data class PdfBankProfile(
    val bank: Bank,
    val detectionKeywords: List<String>,
    val columnHeaders: Map<ColumnRole, String>,
    val dateFormat: String,
    val dateIncludesYear: Boolean,
    val transactionTypePrefixes: List<String> = emptyList(),
)
```

`transactionTypePrefixes` is used by `TransactionParser` to detect transaction boundaries when multiple transactions share the same date group and no new date cell appears. Banks that repeat the date on every row (e.g. Monzo) leave this empty.
```

### Built-in profiles

**NatWest**
```kotlin
PdfBankProfile(
    bank = Bank.NATWEST,
    detectionKeywords = listOf("NatWest", "National Westminster"),
    columnHeaders = mapOf(
        ColumnRole.DATE        to "Date",
        ColumnRole.DESCRIPTION to "Description",
        ColumnRole.AMOUNT_IN   to "Paid In",
        ColumnRole.AMOUNT_OUT  to "Withdrawn",
        ColumnRole.BALANCE     to "Balance",
    ),
    dateFormat = "dd MMM",
    dateIncludesYear = false,
    transactionTypePrefixes = listOf(
        "Automated Credit", "OnLine Transaction", "Direct Debit",
        "Standing Order", "ATM", "XFER",
    ),
)
```

**Monzo**
```kotlin
PdfBankProfile(
    bank = Bank.MONZO,
    detectionKeywords = listOf("Monzo"),
    columnHeaders = mapOf(
        ColumnRole.DATE        to "Date",
        ColumnRole.DESCRIPTION to "Description (GBP)",
        ColumnRole.AMOUNT      to "Amount (GBP)",
        ColumnRole.BALANCE     to "Balance",
    ),
    dateFormat = "dd/MM/yyyy",
    dateIncludesYear = true,
)
```

### BankDetector (commonMain)

Scans the first page's `TextFragment` list for any `detectionKeywords` match (case-insensitive substring). Returns the first matching `PdfBankProfile`, or `null` if unrecognised.

---

## Stage 3: ColumnDetector → ColumnLayout

### ColumnLayout (commonMain)

```kotlin
data class ColumnLayout(
    val headerY: Float,
    val columns: Map<ColumnRole, ClosedRange<Float>>,
)
```

### Detection algorithm

1. Group all fragments by approximate y-position (within 2pt tolerance).
2. For each candidate row, concatenate fragments left-to-right and check whether it contains all of the profile's `columnHeaders` values.
3. The first row matching all headers is the header row. Record `headerY`.
4. For each column header string, find the fragment(s) whose concatenated text matches it. Use the x-position of the leftmost matching fragment as that column's centre.
5. Column x-boundaries are the midpoints between adjacent column centres. The leftmost column extends to x=0; the rightmost extends to page width.

**Multi-word header handling**: before matching, fragments on the same row within 8pt horizontal distance are concatenated. This handles `"Paid In"` arriving as two fragments or `"Amount (GBP)"` as three.

---

## Stage 4: TableRowAssembler → RawTableRow

```kotlin
data class RawTableRow(
    val date: String?,
    val description: String,
    val amountIn: String?,
    val amountOut: String?,
    val amount: String?,
    val balance: String?,
    val pageY: Float,
)
```

### Assembly algorithm

1. Discard all fragments at or above `headerY`.
2. Group remaining fragments by page+y (within 2pt tolerance) → visual lines.
3. For each visual line, assign each fragment to a `ColumnRole` based on its x falling within `ColumnLayout.columns`.
4. Emit one `RawTableRow` per visual line. Description-column fragments on the same line are joined with a space.
5. Skip lines where all columns are empty (blank rows, page separators).

---

## Stage 5: TransactionParser → ParsedTransaction

### Transaction boundary detection

A new transaction begins when:
- The `date` field of a `RawTableRow` is non-null, **OR**
- The description starts with a known transaction-type prefix: `"Automated Credit"`, `"OnLine Transaction"`, `"Direct Debit"`, `"Standing Order"`, `"ATM"`, `"XFER"`

This handles NatWest's format where a date appears once per date-group and multiple transactions follow beneath it without repeating the date.

### Date resolution

- If `dateIncludesYear = false`: scan the first page for the pattern `"Period Covered DD MMM YYYY to DD MMM YYYY"` and extract the end year. Fall back to current year.
- Parse using `dateFormat` with the resolved year appended where needed.

### Amount sign

- `AMOUNT_IN` → positive
- `AMOUNT_OUT` → negate
- `AMOUNT` → parse as-is (Monzo already includes sign)

### Description assembly

Concatenate `description` values from all `RawTableRow`s belonging to the same transaction, separated by a single space. Strip redundant whitespace.

---

## Output Model

### Unified ParsedTransaction (replaces both existing types)

```kotlin
data class ParsedTransaction(
    val date: LocalDate,
    val description: String,
    val amount: BigDecimal,        // positive = in, negative = out
    val runningBalance: BigDecimal?,
    val metadata: Map<String, String> = emptyMap(),
)
```

### ParsedStatement (simplified)

```kotlin
data class ParsedStatement(
    val transactions: List<ParsedTransaction>,
    val accountInfo: ParsedAccountInfo?,   // null if not extractable
    val detectedBank: Bank?,
)
```

`suggestedMapping` and `rawHeaders` are removed — they were CSV-only leakage into the shared model.

---

## Public API

```kotlin
class StatementParser {
    // CSV / OFX — behaviour unchanged
    fun parse(content: String, format: StatementFormat, mapping: ColumnMapping? = null): Result<ParsedStatement>

    // PDF — now platform-universal, bank auto-detected
    fun parsePdf(bytes: ByteArray): Result<ParsedStatement>

    // Utilities
    fun detectFormat(fileName: String, content: String): StatementFormat
    fun getProfiledBanks(): List<Bank>
}
```

`parsePdf` no longer takes a `bankHint` — bank identity is inferred from PDF content via `BankDetector`.

---

## What Is Removed

| Removed | Replaced by |
|---------|-------------|
| `BankStatementTransaction` (androidMain) | `ParsedTransaction` (commonMain) |
| `BankType` enum (androidMain) | `Bank` enum (commonMain) |
| `StatementParseResult` sealed interface | `Result<ParsedStatement>` |
| `BankStatementParser` interface | `StatementParser.parsePdf()` |
| `PdfBankStatementParser` class | pipeline stages in commonMain |
| Per-bank `BankProcessor` subclasses | `PdfBankProfile` data + generic pipeline |
| `StatementParser.parsePdf(bytes, bankHint)` | `StatementParser.parsePdf(bytes)` |
| `ParsedStatement.suggestedMapping` | removed |
| `ParsedStatement.rawHeaders` | removed |

---

## What Is Unchanged

- CSV parsing (`CsvParser`, `CsvBankProfiles`, `ColumnMapping`)
- OFX parsing (`OFXParser`)
- `FormatDetector`
- `DuplicateChecker`
- `Bank` enum values
- Gradle module structure
- KMP targets (Android, iOS, JVM)

## New: JVM PDF Support

JVM was previously a stub (`PdfTextExtractor.jvm.kt` returned empty/unsupported). This work adds a real JVM implementation using Apache PDFBox (full). This enables desktop and server-side parsing — no Android or iOS runtime required.

---

## Testing Strategy

- Unit tests for each pipeline stage in `commonTest` using hardcoded `List<TextFragment>` inputs
- Instrumented test on Android using the real NatWest PDF (`Statement_600402_84318767_01_May_2026.pdf`) asserting exact transaction count, amounts, and dates
- `FormatDetector` and CSV tests unaffected
