# PDF Parsing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add PDF bank statement parsing (Android + iOS) to the statement-parser KMP library using platform-native text extraction and bank-specific regex profiles.

**Architecture:** A `expect class PdfTextExtractor` extracts raw text from PDF bytes using PdfBox-Android on Android and PDFKit on iOS. `PdfParser` (commonMain) applies `PdfBankProfile` regex patterns to that text and returns a `ParsedStatement`. `StatementParser.parsePdf(bytes, bankHint?)` is the single new public entry point. JVM gets a stub that throws `UnsupportedOperationException`.

**Tech Stack:** Kotlin Multiplatform 2.2.20, PdfBox-Android 2.0.27.0 (Apache 2.0), PDFKit (iOS built-in, iOS 11+), kotlin-test

---

## File Map

**Create:**
- `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfBankProfile.kt`
- `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfParser.kt`
- `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.kt` (expect)
- `src/androidMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.android.kt`
- `src/iosMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.ios.kt`
- `src/jvmMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.jvm.kt`
- `src/commonTest/kotlin/io/github/sporadiclemon/statementparser/PdfParserTest.kt`
- `src/commonTest/kotlin/io/github/sporadiclemon/statementparser/PdfBankProfilesTest.kt`

**Modify:**
- `gradle/libs.versions.toml` — add pdfbox-android version + library entry
- `build.gradle.kts` — add `androidMain` dependency block
- `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/StatementFormat.kt` — add `PDF`
- `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/FormatDetector.kt` — detect `.pdf` extension
- `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/StatementParser.kt` — add `parsePdf`

---

## Task 1: Add PdfBox-Android dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add pdfbox-android to libs.versions.toml**

In `gradle/libs.versions.toml`, add under `[versions]`:
```toml
pdfbox-android = "2.0.27.0"
```

And under `[libraries]`:
```toml
pdfbox-android = { group = "com.tom-roush", name = "pdfbox-android", version.ref = "pdfbox-android" }
```

- [ ] **Step 2: Add androidMain dependency block to build.gradle.kts**

In `build.gradle.kts`, inside the `sourceSets { }` block (alongside the existing `commonMain` and `commonTest` blocks), add:
```kotlin
androidMain.dependencies {
    implementation(libs.pdfbox.android)
}
```

- [ ] **Step 3: Sync and verify compilation**

Run:
```bash
./gradlew assemble
```
Expected: BUILD SUCCESSFUL (no errors about missing dependencies).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts
git commit -m "chore: add pdfbox-android dependency for PDF text extraction"
```

---

## Task 2: Add StatementFormat.PDF and update FormatDetector

**Files:**
- Modify: `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/StatementFormat.kt`
- Modify: `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/FormatDetector.kt`
- Modify: `src/commonTest/kotlin/io/github/sporadiclemon/statementparser/FormatDetectorTest.kt`

- [ ] **Step 1: Write the failing tests**

Add these two tests to `FormatDetectorTest.kt`, inside the existing `FormatDetectorTest` class:

```kotlin
@Test fun `detects PDF by file extension`() {
    assertEquals(StatementFormat.PDF, detector.detect("statement.pdf", ""))
}

@Test fun `detects PDF by uppercase extension`() {
    assertEquals(StatementFormat.PDF, detector.detect("statement.PDF", ""))
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew jvmTest --tests "io.github.sporadiclemon.statementparser.FormatDetectorTest"
```
Expected: FAIL — `StatementFormat.PDF` does not exist yet.

- [ ] **Step 3: Add PDF to StatementFormat**

Replace the entire content of `StatementFormat.kt`:
```kotlin
package io.github.sporadiclemon.statementparser

enum class StatementFormat { CSV, OFX, PDF }
```

- [ ] **Step 4: Add PDF detection to FormatDetector**

In `FormatDetector.kt`, add `if (ext == "pdf") return StatementFormat.PDF` immediately after the existing `qfx` check:

```kotlin
class FormatDetector {
    fun detect(fileName: String, content: String): StatementFormat {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "ofx" || ext == "qfx") return StatementFormat.OFX
        if (ext == "csv") return StatementFormat.CSV
        if (ext == "pdf") return StatementFormat.PDF
        val trimmed = content.trimStart()
        if (trimmed.startsWith("<?xml") ||
            trimmed.contains("<OFX>", ignoreCase = true) ||
            trimmed.startsWith("OFXHEADER")
        ) return StatementFormat.OFX
        return StatementFormat.CSV
    }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./gradlew jvmTest --tests "io.github.sporadiclemon.statementparser.FormatDetectorTest"
```
Expected: PASS — all 9 tests green.

- [ ] **Step 6: Commit**

```bash
git add src/commonMain/kotlin/io/github/sporadiclemon/statementparser/StatementFormat.kt \
        src/commonMain/kotlin/io/github/sporadiclemon/statementparser/FormatDetector.kt \
        src/commonTest/kotlin/io/github/sporadiclemon/statementparser/FormatDetectorTest.kt
git commit -m "feat: add StatementFormat.PDF and FormatDetector support for .pdf extension"
```

---

## Task 3: PdfTextExtractor expect class and all actuals

**Files:**
- Create: `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.kt`
- Create: `src/androidMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.android.kt`
- Create: `src/iosMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.ios.kt`
- Create: `src/jvmMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.jvm.kt`

- [ ] **Step 1: Create the expect class in commonMain**

Create `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.kt`:
```kotlin
package io.github.sporadiclemon.statementparser

expect class PdfTextExtractor() {
    fun extractText(bytes: ByteArray): String
}
```

- [ ] **Step 2: Create the JVM actual (stub)**

Create `src/jvmMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.jvm.kt`:
```kotlin
package io.github.sporadiclemon.statementparser

actual class PdfTextExtractor actual constructor() {
    actual fun extractText(bytes: ByteArray): String =
        throw UnsupportedOperationException("PDF text extraction is not yet supported on JVM")
}
```

- [ ] **Step 3: Create the Android actual**

Create `src/androidMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.android.kt`:
```kotlin
package io.github.sporadiclemon.statementparser

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

actual class PdfTextExtractor actual constructor() {
    actual fun extractText(bytes: ByteArray): String {
        val doc = PDDocument.load(bytes)
        return try {
            PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
    }
}
```

> **Important:** PdfBox-Android requires one-time initialisation before first use. The consuming app must call `PDFBoxResourceLoader.init(applicationContext)` in its `Application.onCreate()`. Document this in the library README when shipping.

- [ ] **Step 4: Create the iOS actual**

Create `src/iosMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.ios.kt`:
```kotlin
package io.github.sporadiclemon.statementparser

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument

actual class PdfTextExtractor actual constructor() {
    @OptIn(ExperimentalForeignApi::class)
    actual fun extractText(bytes: ByteArray): String {
        val nsData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val document = PDFDocument(nsData) ?: return ""
        return buildString {
            for (i in 0 until document.pageCount().toInt()) {
                val page = document.pageAtIndex(i.toULong()) ?: continue
                append(page.string ?: "")
                append("\n")
            }
        }
    }
}
```

- [ ] **Step 5: Verify compilation across all targets**

```bash
./gradlew assemble
```
Expected: BUILD SUCCESSFUL — all targets compile with their respective actuals.

- [ ] **Step 6: Commit**

```bash
git add src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.kt \
        src/androidMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.android.kt \
        src/iosMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.ios.kt \
        src/jvmMain/kotlin/io/github/sporadiclemon/statementparser/PdfTextExtractor.jvm.kt
git commit -m "feat: add PdfTextExtractor expect/actual for Android (PdfBox) and iOS (PDFKit)"
```

---

## Task 4: PdfBankProfile data model and profile resolution

**Files:**
- Create: `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfBankProfile.kt`
- Create: `src/commonTest/kotlin/io/github/sporadiclemon/statementparser/PdfBankProfilesTest.kt`

- [ ] **Step 1: Write failing tests**

Create `src/commonTest/kotlin/io/github/sporadiclemon/statementparser/PdfBankProfilesTest.kt`:
```kotlin
package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfBankProfilesTest {

    @Test fun `resolves Monzo profile by bank name in text`() {
        val text = "Monzo Bank Limited\nStatement period: Jan 2024"
        val profile = PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        assertEquals("Monzo", profile?.name)
    }

    @Test fun `resolves Starling profile by bank name in text`() {
        val text = "Starling Bank\nAccount Statement"
        val profile = PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        assertEquals("Starling", profile?.name)
    }

    @Test fun `resolves HSBC profile by bank name in text`() {
        val text = "HSBC UK Bank plc\nSort Code: 40-12-34"
        val profile = PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        assertEquals("HSBC", profile?.name)
    }

    @Test fun `resolves Lloyds profile by bank name in text`() {
        val text = "Lloyds Bank plc\nRegistered in England and Wales"
        val profile = PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        assertEquals("Lloyds", profile?.name)
    }

    @Test fun `returns null for unrecognised bank text`() {
        val text = "Random Finance Co\nStatement"
        val profile = PdfBankProfiles.all.firstOrNull { it.bankNamePattern.containsMatchIn(text) }
        assertNull(profile)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew jvmTest --tests "io.github.sporadiclemon.statementparser.PdfBankProfilesTest"
```
Expected: FAIL — `PdfBankProfile` and `PdfBankProfiles` do not exist yet.

- [ ] **Step 3: Create PdfBankProfile.kt**

Create `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfBankProfile.kt`:
```kotlin
package io.github.sporadiclemon.statementparser

data class PdfBankProfile(
    val name: String,
    val bankNamePattern: Regex,
    val transactionLinePattern: Regex,
    val dateGroup: Int,
    val descriptionGroup: Int,
    val amountGroup: Int?,
    val amountInGroup: Int?,
    val amountOutGroup: Int?,
    val dateFormat: String,
)

object PdfBankProfiles {
    val all: List<PdfBankProfile> = listOf(
        PdfBankProfile(
            name = "Monzo",
            bankNamePattern = Regex("Monzo Bank", RegexOption.IGNORE_CASE),
            transactionLinePattern = Regex(
                """^(\d{1,2} \w{3} \d{4})\s{2,}(.+?)\s{2,}([+-]?£[\d,]+\.\d{2})""",
                RegexOption.MULTILINE,
            ),
            dateGroup = 1,
            descriptionGroup = 2,
            amountGroup = 3,
            amountInGroup = null,
            amountOutGroup = null,
            dateFormat = "dd MMM yyyy",
        ),
        PdfBankProfile(
            name = "Starling",
            bankNamePattern = Regex("Starling Bank", RegexOption.IGNORE_CASE),
            transactionLinePattern = Regex(
                """^(\d{2}/\d{2}/\d{4})\s{2,}(.+?)\s{2,}([+-]?[\d,]+\.\d{2})""",
                RegexOption.MULTILINE,
            ),
            dateGroup = 1,
            descriptionGroup = 2,
            amountGroup = 3,
            amountInGroup = null,
            amountOutGroup = null,
            dateFormat = "dd/MM/yyyy",
        ),
        PdfBankProfile(
            name = "HSBC",
            bankNamePattern = Regex("HSBC", RegexOption.IGNORE_CASE),
            transactionLinePattern = Regex(
                """^(\d{2} \w{3} \d{2})\s{2,}(.+?)\s{2,}([\d,]+\.\d{2})\s+([\d,]+\.\d{2})""",
                RegexOption.MULTILINE,
            ),
            dateGroup = 1,
            descriptionGroup = 2,
            amountGroup = 3,
            amountInGroup = null,
            amountOutGroup = null,
            dateFormat = "dd MMM yy",
        ),
        PdfBankProfile(
            name = "Lloyds",
            bankNamePattern = Regex("Lloyds Bank", RegexOption.IGNORE_CASE),
            transactionLinePattern = Regex(
                """^(\d{2} \w{3} \d{4})\s+(.+?)\s+([\d,]+\.\d{2})D?\s""",
                RegexOption.MULTILINE,
            ),
            dateGroup = 1,
            descriptionGroup = 2,
            amountGroup = 3,
            amountInGroup = null,
            amountOutGroup = null,
            dateFormat = "dd MMM yyyy",
        ),
    )
}
```

> **Note:** These regex patterns are reasonable starting points based on typical UK bank PDF layouts. They MUST be validated against real PDF statement samples before shipping. See Task 7 for the validation workflow.

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew jvmTest --tests "io.github.sporadiclemon.statementparser.PdfBankProfilesTest"
```
Expected: PASS — all 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfBankProfile.kt \
        src/commonTest/kotlin/io/github/sporadiclemon/statementparser/PdfBankProfilesTest.kt
git commit -m "feat: add PdfBankProfile data model and initial bank profiles"
```

---

## Task 5: PdfParser (commonMain)

**Files:**
- Create: `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfParser.kt`
- Create: `src/commonTest/kotlin/io/github/sporadiclemon/statementparser/PdfParserTest.kt`

- [ ] **Step 1: Write failing tests**

Create `src/commonTest/kotlin/io/github/sporadiclemon/statementparser/PdfParserTest.kt`:
```kotlin
package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PdfParserTest {
    private val parser = PdfParser()

    // --- Profile auto-detection ---

    @Test fun `detects Monzo from extracted text and parses transactions`() {
        val text = """
            Monzo Bank Limited
            Account Statement January 2024

            01 Jan 2024  Tesco Superstore  -£4.50  £1,200.00
            15 Jan 2024  Amazon Prime  -£7.99  £1,192.01
            20 Jan 2024  Employer Salary  +£2,500.00  £3,692.01
        """.trimIndent()

        val result = parser.parseText(text, null).getOrThrow()
        assertEquals(3, result.transactions.size)

        assertEquals(LocalDate(2024, 1, 1), result.transactions[0].date)
        assertEquals(-4.50, result.transactions[0].amount)
        assertEquals("Tesco Superstore", result.transactions[0].description)

        assertEquals(LocalDate(2024, 1, 20), result.transactions[2].date)
        assertEquals(2500.00, result.transactions[2].amount)
    }

    @Test fun `uses bankHint to resolve profile without scanning text`() {
        val text = """
            Some Generic Header

            01 Jan 2024  Coffee Shop  -£3.00  £500.00
        """.trimIndent()
        val result = parser.parseText(text, "Monzo")
        assertNotNull(result.getOrNull())
    }

    @Test fun `returns failure when no profile matches and no bankHint`() {
        val text = "Random Finance Ltd\n\nNo transactions here."
        val result = parser.parseText(text, null)
        assertTrue(result.isFailure)
    }

    @Test fun `returns failure for unknown bankHint`() {
        val text = "Monzo Bank Limited\n01 Jan 2024  Tesco  -£4.50  £100.00"
        val result = parser.parseText(text, "UnknownBank")
        assertTrue(result.isFailure)
    }

    // --- Date format: dd MMM yyyy ---

    @Test fun `parses dd MMM yyyy date format`() {
        val text = """
            Monzo Bank Limited

            14 Apr 2024  Amazon  -£29.99  £500.00
        """.trimIndent()
        val result = parser.parseText(text, "Monzo").getOrThrow()
        assertEquals(LocalDate(2024, 4, 14), result.transactions[0].date)
    }

    // --- Amount parsing ---

    @Test fun `parses negative amount from £ prefixed string`() {
        val text = """
            Monzo Bank Limited

            03 Mar 2024  Tesco  -£12.50  £488.00
        """.trimIndent()
        val result = parser.parseText(text, "Monzo").getOrThrow()
        assertEquals(-12.50, result.transactions[0].amount)
    }

    @Test fun `parses positive amount from £ prefixed string`() {
        val text = """
            Monzo Bank Limited

            03 Mar 2024  Salary  +£2,000.00  £2,488.00
        """.trimIndent()
        val result = parser.parseText(text, "Monzo").getOrThrow()
        assertEquals(2000.00, result.transactions[0].amount)
    }

    // --- ParsedStatement fields ---

    @Test fun `parsed statement has correct account info unavailable reason`() {
        val text = "Monzo Bank Limited\n\n01 Jan 2024  Coffee  -£3.00  £97.00"
        val result = parser.parseText(text, "Monzo").getOrThrow()
        assertTrue(result.accountInfoResult is AccountInfoResult.NotAvailable)
    }

    @Test fun `skips lines that do not match transaction pattern`() {
        val text = """
            Monzo Bank Limited
            Statement Period: 1 January 2024 to 31 January 2024
            Opening Balance: £1,200.00

            01 Jan 2024  Tesco  -£4.50  £1,195.50

            Closing Balance: £1,195.50
        """.trimIndent()
        val result = parser.parseText(text, "Monzo").getOrThrow()
        assertEquals(1, result.transactions.size)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew jvmTest --tests "io.github.sporadiclemon.statementparser.PdfParserTest"
```
Expected: FAIL — `PdfParser` does not exist yet.

- [ ] **Step 3: Create PdfParser.kt**

Create `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfParser.kt`:
```kotlin
package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

internal class PdfParser {
    private val extractor = PdfTextExtractor()

    fun parse(bytes: ByteArray, bankHint: String? = null): Result<ParsedStatement> =
        parseText(extractor.extractText(bytes), bankHint)

    internal fun parseText(text: String, bankHint: String?): Result<ParsedStatement> = runCatching {
        val profile = resolveProfile(text, bankHint)
            ?: throw IllegalArgumentException(
                "No PDF bank profile matched. Supported banks: ${PdfBankProfiles.all.joinToString { it.name }}"
            )
        val transactions = extractTransactions(text, profile)
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

    private fun extractTransactions(text: String, profile: PdfBankProfile): List<ParsedTransaction> =
        profile.transactionLinePattern.findAll(text).mapNotNull { match ->
            matchToTransaction(match, profile)
        }.toList()

    private fun matchToTransaction(match: MatchResult, profile: PdfBankProfile): ParsedTransaction? {
        val dateStr = match.groupValues.getOrNull(profile.dateGroup)?.trim() ?: return null
        val description = match.groupValues.getOrNull(profile.descriptionGroup)?.trim() ?: return null
        val amount: Double = when {
            profile.amountGroup != null -> {
                val raw = match.groupValues.getOrNull(profile.amountGroup) ?: return null
                parseAmount(raw) ?: return null
            }
            profile.amountInGroup != null && profile.amountOutGroup != null -> {
                val inStr = match.groupValues.getOrNull(profile.amountInGroup)?.trim() ?: ""
                val outStr = match.groupValues.getOrNull(profile.amountOutGroup)?.trim() ?: ""
                when {
                    inStr.isNotBlank() -> parseAmount(inStr) ?: return null
                    outStr.isNotBlank() -> -(parseAmount(outStr) ?: return null)
                    else -> return null
                }
            }
            else -> return null
        }
        val date = parseDate(dateStr, profile.dateFormat) ?: return null
        return ParsedTransaction(date = date, amount = amount, description = description, raw = match.value)
    }

    private fun parseAmount(raw: String): Double? {
        val cleaned = raw.trim()
            .removePrefix("+")
            .replace("£", "")
            .replace(",", "")
        return cleaned.toDoubleOrNull()
    }

    private fun parseDate(dateStr: String, format: String): LocalDate? = try {
        when (format) {
            "dd/MM/yyyy" -> {
                val parts = dateStr.split('/', '-', '.')
                if (parts.size == 3) LocalDate(year = parts[2].toInt(), month = parts[1].toInt(), day = parts[0].toInt())
                else null
            }
            "yyyy-MM-dd" -> {
                val parts = dateStr.split('-')
                if (parts.size == 3) LocalDate(year = parts[0].toInt(), month = parts[1].toInt(), day = parts[2].toInt())
                else null
            }
            "dd MMM yyyy" -> parseLongMonthDate(dateStr, 4)
            "dd MMM yy" -> parseLongMonthDate(dateStr, 2)
            else -> null
        }
    } catch (_: Exception) { null }

    private fun parseLongMonthDate(dateStr: String, yearDigits: Int): LocalDate? {
        val parts = dateStr.trim().split(" ")
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = monthAbbr(parts[1]) ?: return null
        val year = when (yearDigits) {
            2 -> parts[2].toIntOrNull()?.let { if (it >= 50) 1900 + it else 2000 + it }
            else -> parts[2].toIntOrNull()
        } ?: return null
        return LocalDate(year = year, month = month, day = day)
    }

    private fun monthAbbr(abbr: String): Int? = when (abbr.lowercase()) {
        "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
        "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
        else -> null
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew jvmTest --tests "io.github.sporadiclemon.statementparser.PdfParserTest"
```
Expected: PASS — all 9 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfParser.kt \
        src/commonTest/kotlin/io/github/sporadiclemon/statementparser/PdfParserTest.kt
git commit -m "feat: add PdfParser with profile-based text-to-transaction parsing"
```

---

## Task 6: Add StatementParser.parsePdf

**Files:**
- Modify: `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/StatementParser.kt`
- Modify: `src/commonTest/kotlin/io/github/sporadiclemon/statementparser/StatementParserTest.kt`

- [ ] **Step 1: Read StatementParserTest.kt to understand existing test structure**

Open `src/commonTest/kotlin/io/github/sporadiclemon/statementparser/StatementParserTest.kt` and note the existing test patterns.

- [ ] **Step 2: Write the failing test**

Add to `StatementParserTest.kt` inside the existing `StatementParserTest` class:
```kotlin
@Test fun `parsePdf returns failure for empty bytes`() {
    val parser = StatementParser()
    val result = parser.parsePdf(ByteArray(0), bankHint = "Monzo")
    assertTrue(result.isFailure)
}
```

- [ ] **Step 3: Run test to confirm it fails**

```bash
./gradlew jvmTest --tests "io.github.sporadiclemon.statementparser.StatementParserTest"
```
Expected: FAIL — `parsePdf` does not exist yet.

- [ ] **Step 4: Add parsePdf to StatementParser**

In `StatementParser.kt`, add the `pdfParser` field and `parsePdf` method. The full updated file:
```kotlin
package io.github.sporadiclemon.statementparser

class StatementParser {

    private val formatDetector = FormatDetector()
    private val csvParser = CsvParser()
    private val ofxParser = OFXParser()
    private val pdfParser = PdfParser()

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
            StatementFormat.PDF -> Result.failure(
                IllegalArgumentException("Use parsePdf(bytes) for PDF format")
            )
        }

    fun parsePdf(bytes: ByteArray, bankHint: String? = null): Result<ParsedStatement> =
        pdfParser.parse(bytes, bankHint)

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
```

- [ ] **Step 5: Run all common tests**

```bash
./gradlew jvmTest
```
Expected: PASS — all tests green, including the new `parsePdf` test.

- [ ] **Step 6: Commit**

```bash
git add src/commonMain/kotlin/io/github/sporadiclemon/statementparser/StatementParser.kt \
        src/commonTest/kotlin/io/github/sporadiclemon/statementparser/StatementParserTest.kt
git commit -m "feat: add StatementParser.parsePdf entry point"
```

---

## Task 7: Validate and fix bank profile regex patterns

The regex patterns in `PdfBankProfiles` are based on typical UK bank PDF layouts. Each must be validated against real PDF statement samples before shipping.

**Files:**
- Modify: `src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfBankProfile.kt`
- Modify: `src/commonTest/kotlin/io/github/sporadiclemon/statementparser/PdfParserTest.kt`

**Validation workflow for each bank:**

- [ ] **Step 1: Extract text from a real PDF for each bank**

For each bank (Monzo, Starling, HSBC, Lloyds), obtain a real PDF statement. Write a temporary JVM main or test that calls:

```kotlin
// Temporary validation helper — delete after use
fun main() {
    val bytes = java.io.File("statement-monzo.pdf").readBytes()
    val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes)
    println(com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc))
    doc.close()
}
```

Paste the output into a text file and examine it carefully. Identify the exact character sequence of a transaction line.

- [ ] **Step 2: Adjust the regex pattern to match real output**

For each bank whose pattern needs adjustment, update the `transactionLinePattern` in `PdfBankProfile.kt`. The pattern must capture:
- Group at `dateGroup` index: the full date string
- Group at `descriptionGroup` index: the merchant/payee name
- Group at `amountGroup` (or `amountInGroup`/`amountOutGroup`) index: the amount string (may include `£`, `+`, `-`, `,`)

- [ ] **Step 3: Add a real-text test case for each bank**

For each bank, add a test to `PdfParserTest.kt` using a representative multi-line extracted text sample (copy from the real output, sanitise any personal data):

```kotlin
@Test fun `parses real Monzo extracted text format`() {
    // Paste representative extracted text here (with personal data removed)
    val text = """
        Monzo Bank Limited
        ...actual format...
        14 Apr 2024  Tesco Express  -£6.40  £593.60
    """.trimIndent()
    val result = parser.parseText(text, "Monzo").getOrThrow()
    assertTrue(result.transactions.isNotEmpty())
    assertEquals(-6.40, result.transactions.first().amount, 0.001)
}
```

Repeat for Starling, HSBC, and Lloyds.

- [ ] **Step 4: Run all tests**

```bash
./gradlew jvmTest
```
Expected: PASS — all tests green with real-format samples.

- [ ] **Step 5: Commit validated profiles**

```bash
git add src/commonMain/kotlin/io/github/sporadiclemon/statementparser/PdfBankProfile.kt \
        src/commonTest/kotlin/io/github/sporadiclemon/statementparser/PdfParserTest.kt
git commit -m "feat: validate and fix PDF bank profile regex patterns against real statements"
```

---

## Task 8: Update README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add PDF usage section and PdfBox init note to README**

Add the following section to `README.md` after the existing Usage section:

```markdown
## PDF Parsing

```kotlin
val parser = StatementParser()

// Detect by filename
val format = parser.detectFormat("statement.pdf", "")
// format == StatementFormat.PDF

// Parse a PDF statement
val bytes = file.readBytes()
val result = parser.parsePdf(bytes)                          // auto-detects bank
val result = parser.parsePdf(bytes, bankHint = "Monzo")     // skip auto-detection

result.getOrThrow().transactions.forEach {
    println("${it.date}: ${it.description} (${it.amount})")
}
```

### Supported PDF Banks

| Bank     | Detection            |
|----------|----------------------|
| Monzo    | "Monzo Bank" in text |
| Starling | "Starling Bank"      |
| HSBC     | "HSBC"               |
| Lloyds   | "Lloyds Bank"        |

### Android Setup

PdfBox-Android requires one-time initialisation. In your `Application` class:

```kotlin
override fun onCreate() {
    super.onCreate()
    PDFBoxResourceLoader.init(applicationContext)
}
```

### Platform Support

| Platform | PDF Support |
|----------|------------|
| Android  | ✓ (PdfBox-Android) |
| iOS      | ✓ (PDFKit, iOS 11+) |
| JVM      | Not yet supported |
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add PDF parsing usage and Android setup instructions to README"
```
