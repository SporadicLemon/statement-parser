package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val result = parser.parseText(text, "Monzo").getOrThrow()
        assertEquals(1, result.transactions.size)
        assertEquals(-3.00, result.transactions[0].amount)
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

    // --- Starling: dd/MM/yyyy date format ---

    @Test fun `parses Starling extracted text with dd-MM-yyyy dates`() {
        val text = """
            Starling Bank
            Account Statement

            15/01/2024  Tesco Express  -4.50  295.50
            20/01/2024  Salary  1500.00  1795.50
        """.trimIndent()
        val result = parser.parseText(text, "Starling").getOrThrow()
        assertEquals(2, result.transactions.size)
        assertEquals(LocalDate(2024, 1, 15), result.transactions[0].date)
        assertEquals(-4.50, result.transactions[0].amount)
        assertEquals("Tesco Express", result.transactions[0].description)
        assertEquals(LocalDate(2024, 1, 20), result.transactions[1].date)
        assertEquals(1500.00, result.transactions[1].amount)
    }

    // --- amountInGroup / amountOutGroup split columns ---

    @Test fun `parses split amountInGroup and amountOutGroup columns`() {
        val splitProfile = PdfBankProfile(
            name = "SplitBank",
            bankNamePattern = Regex("SplitBank"),
            // group 3 = out (debit, optional), group 4 = in (credit, optional)
            // Fixed-width columns: each amount field is exactly 10 chars wide.
            transactionLinePattern = Regex(
                """^(\d{2}/\d{2}/\d{4})  (.+?)  ([\d,]+\.\d{2})?          ([\d,]+\.\d{2})?""",
                RegexOption.MULTILINE,
            ),
            dateGroup = 1,
            descriptionGroup = 2,
            amountGroup = null,
            amountInGroup = 4,
            amountOutGroup = 3,
            dateFormat = "dd/MM/yyyy",
        )
        val parserWithSplitProfile = PdfParser(listOf(splitProfile))
        // Debit row: out column populated, in column empty
        // Credit row: out column empty, in column populated
        val text = "15/01/2024  Tesco  4.50          \n20/01/2024  Salary            1500.00"
        val result = parserWithSplitProfile.parseText(text, "SplitBank").getOrThrow()
        assertEquals(2, result.transactions.size)
        assertEquals(-4.50, result.transactions[0].amount)
        assertEquals(1500.00, result.transactions[1].amount)
    }

    // --- NatWest: first amount after description, dd MMM yyyy ---
    // Sign direction is not yet determined without real PDF validation.

    @Test fun `parses NatWest extracted text capturing first amount after description`() {
        val text = """
            NatWest
            Account Statement

            15 Jan 2024  TESCO STORES 1234              4.50              1,195.50
            20 Jan 2024  BACS CREDIT SALARY                       2,500.00  3,695.50
        """.trimIndent()
        val result = parser.parseText(text, "NatWest").getOrThrow()
        assertEquals(2, result.transactions.size)
        assertEquals(LocalDate(2024, 1, 15), result.transactions[0].date)
        assertEquals(4.50, result.transactions[0].amount)
        assertEquals("TESCO STORES 1234", result.transactions[0].description)
        assertEquals(LocalDate(2024, 1, 20), result.transactions[1].date)
        assertEquals(2500.00, result.transactions[1].amount)
    }
}
