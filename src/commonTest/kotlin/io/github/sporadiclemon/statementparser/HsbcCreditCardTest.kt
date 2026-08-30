package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The HSBC credit card table, reproduced from synthetic fragments at the x/y positions a real
 * statement uses: a heading split over three lines, two date columns, no balance, and credits
 * marked by a trailing "CR".
 */
class HsbcCreditCardTest {

    private val profile = PdfBankProfiles.HSBC_CREDIT_CARD

    // "Your Transaction Details" title, "Amount" on its own line, then the column labels.
    private val headingFragments = listOf(
        TextFragment("Your",        x = 54f,  y = 205f, page = 1),
        TextFragment("Transaction", x = 84f,  y = 205f, page = 1),
        TextFragment("Details",     x = 156f, y = 205f, page = 1),
        TextFragment("Amount",      x = 517f, y = 207f, page = 1),
        TextFragment("Received",    x = 54f,  y = 215f, page = 1),
        TextFragment("By",          x = 86f,  y = 215f, page = 1),
        TextFragment("Us",          x = 96f,  y = 215f, page = 1),
        TextFragment("Transaction", x = 117f, y = 215f, page = 1),
        TextFragment("Date",        x = 158f, y = 215f, page = 1),
        TextFragment("Details",     x = 194f, y = 215f, page = 1),
    )

    /** One table line: posting date, transaction date, merchant words, then the figure. */
    private fun transactionRow(
        y: Float,
        posted: List<String>,
        transacted: List<String>,
        merchant: List<Pair<String, Float>>,
        amount: String,
    ): List<TextFragment> = buildList {
        add(TextFragment(posted[0], x = 54f, y = y, page = 1))
        add(TextFragment(posted[1], x = 66f, y = y, page = 1))
        add(TextFragment(posted[2], x = 84f, y = y, page = 1))
        add(TextFragment(transacted[0], x = 119f, y = y, page = 1))
        add(TextFragment(transacted[1], x = 131f, y = y, page = 1))
        add(TextFragment(transacted[2], x = 149f, y = y, page = 1))
        merchant.forEach { (text, x) -> add(TextFragment(text, x = x, y = y, page = 1)) }
        add(TextFragment(amount, x = 522f, y = y, page = 1))
    }

    private val debitRow = transactionRow(
        y = 254f,
        posted = listOf("27", "Jul", "26"),
        transacted = listOf("24", "Jul", "26"),
        merchant = listOf("PURPLE" to 197f, "TEAPOT" to 250f, "CAFE" to 300f),
        amount = "12.34",
    )

    private val creditRow = transactionRow(
        y = 266f,
        posted = listOf("03", "Aug", "26"),
        transacted = listOf("01", "Aug", "26"),
        merchant = listOf("PURPLE" to 197f, "TEAPOT" to 250f, "REFUND" to 300f),
        amount = "12.34CR",
    )

    private val fragments = headingFragments + debitRow + creditRow

    @Test
    fun `card profile is detected before the current account profile`() {
        assertTrue(
            PdfBankProfiles.all.indexOf(profile) < PdfBankProfiles.all.indexOf(PdfBankProfiles.HSBC),
            "the card profile must be checked first, since a card statement also says \"HSBC\"",
        )
    }

    @Test
    fun `card detection keyword cannot match a current account statement`() {
        val currentAccountText = listOf(
            TextFragment("HSBC", x = 50f, y = 50f, page = 0),
            TextFragment("Your", x = 50f, y = 70f, page = 0),
            TextFragment("Bank", x = 80f, y = 70f, page = 0),
            TextFragment("Account", x = 110f, y = 70f, page = 0),
            TextFragment("Statement", x = 160f, y = 70f, page = 0),
        )
        assertSame(PdfBankProfiles.HSBC, BankDetector().detect(currentAccountText))
    }

    @Test
    fun `card detection keyword matches a card statement`() {
        val cardText = listOf(
            TextFragment("Your",      x = 380f, y = 57f, page = 0),
            TextFragment("Visa",      x = 416f, y = 57f, page = 0),
            TextFragment("Card",      x = 448f, y = 57f, page = 0),
            TextFragment("statement", x = 483f, y = 57f, page = 0),
            TextFragment("HSBC",      x = 474f, y = 157f, page = 0),
        )
        assertSame(profile, BankDetector().detect(cardText))
    }

    @Test
    fun `card profile has one amount column and no balance`() {
        assertTrue(profile.columnHeaders.containsKey(ColumnRole.AMOUNT))
        assertFalse(profile.columnHeaders.containsKey(ColumnRole.BALANCE))
        assertFalse(profile.columnHeaders.containsKey(ColumnRole.AMOUNT_IN))
        assertFalse(profile.columnHeaders.containsKey(ColumnRole.AMOUNT_OUT))
        assertEquals("CR", profile.creditMarkerSuffix)
    }

    @Test
    fun `detects columns across the heading split over two lines`() {
        val layout = ColumnDetector().detect(fragments, profile)
        assertNotNull(layout, "the heading spans two lines, so no single row holds every label")
        assertEquals(1, layout.headerPage)
        assertEquals(setOf(ColumnRole.DATE, ColumnRole.DESCRIPTION, ColumnRole.AMOUNT), layout.columns.keys)
    }

    // The heading rows below the widened header line survive the assembler's y filter, but carry
    // no figure; the parser drops them because their date column does not parse as a date.
    private fun tableRows(): List<RawTableRow> {
        val layout = ColumnDetector().detect(fragments, profile)!!
        return TableRowAssembler().assemble(fragments, layout).filter { it.amount != null }
    }

    @Test
    fun `date column takes only the posting date`() {
        val rows = tableRows()
        assertEquals(2, rows.size)
        assertEquals("27 Jul 26", rows[0].date)
        assertEquals("03 Aug 26", rows[1].date)
    }

    @Test
    fun `transaction date folds into the front of the description`() {
        val rows = tableRows()
        assertEquals("24 Jul 26 PURPLE TEAPOT CAFE", rows[0].description)
        assertEquals("12.34", rows[0].amount)
        assertNull(rows[0].balance)
    }

    @Test
    fun `a bare figure is a purchase and a CR figure is a refund`() {
        val layout = ColumnDetector().detect(fragments, profile)!!
        // The whole table, heading remnants included, straight through the parser.
        val rows = TableRowAssembler().assemble(fragments, layout)
        val transactions = PdfTransactionParser().parse(rows, profile)

        assertEquals(2, transactions.size)
        assertEquals(LocalDate(2026, 7, 27), transactions[0].date)
        assertEquals(-12.34, transactions[0].amount)
        assertEquals(LocalDate(2026, 8, 3), transactions[1].date)
        assertEquals(12.34, transactions[1].amount)
        assertEquals(0.0, transactions.sumOf { it.amount })
    }

    @Test
    fun `a profile without a credit marker leaves the sign alone`() {
        val row = RawTableRow(
            date = "01/04/2026",
            description = "PURPLE TEAPOT CAFE",
            amountIn = null,
            amountOut = null,
            amount = "12.34",
            balance = null,
            pageY = 200f,
        )
        val transactions = PdfTransactionParser().parse(listOf(row), PdfBankProfiles.MONZO)
        assertEquals(listOf(12.34), transactions.map { it.amount })
    }
}
