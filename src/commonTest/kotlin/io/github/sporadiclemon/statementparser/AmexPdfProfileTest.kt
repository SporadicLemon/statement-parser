package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * American Express's PDF layout, reproduced from the x/y positions a real statement uses.
 *
 * Two features make this table awkward. First, there is a second ("process") date column whose
 * header sits almost exactly at the midpoint between the transaction-date and description
 * anchors, so its month name leaks into the date cell as a third token and its day number leaks
 * into the description as a loose prefix - see [DateParserTest.MMM d ignores a trailing third
 * token][DateParserTest]. Second, the credit marker "CR" is not a suffix on the amount cell like
 * HSBC's card - it is printed on its own line, 12pt below the figure it marks, far enough to land
 * in a separate table row.
 *
 * The synthetic figures still reconcile the way the statement does: previous balance 200.00,
 * minus the one credit (100.00), plus the two debits (4.50 and 9.99), gives closing 114.49.
 */
class AmexPdfProfileTest {

    private val profile = PdfBankProfiles.AMEX

    // "Date" appears twice - transaction date, then process date - "Transaction Details" as one
    // phrase, "Foreign Spend" (unmapped by this profile) sitting between description and amount,
    // and "Amount" on its own.
    private val header = listOf(
        TextFragment("Date", x = 14f, y = 299f, page = 0),
        TextFragment("Date", x = 57f, y = 299f, page = 0),
        TextFragment("Transaction", x = 100f, y = 299f, page = 0),
        TextFragment("Details", x = 135f, y = 299f, page = 0),
        TextFragment("Foreign", x = 377f, y = 299f, page = 0),
        TextFragment("Spend", x = 399f, y = 299f, page = 0),
        TextFragment("Amount", x = 504f, y = 299f, page = 0),
        TextFragment("£", x = 529f, y = 299f, page = 0),
    )

    // A debit: bare amount, no CR line follows.
    private val debitRow = listOf(
        TextFragment("Jul", x = 14f, y = 318f, page = 0),
        TextFragment("27", x = 28f, y = 318f, page = 0),
        TextFragment("Jul", x = 57f, y = 318f, page = 0),
        TextFragment("27", x = 71f, y = 318f, page = 0),
        TextFragment("COFFEE", x = 100f, y = 318f, page = 0),
        TextFragment("SHOP", x = 155f, y = 318f, page = 0),
        TextFragment("LONDON", x = 195f, y = 318f, page = 0),
        TextFragment("4.50", x = 520f, y = 318f, page = 0),
    )

    // A credit: the amount row, then "CR" alone 12pt below it as its own table row.
    private val creditAmountRow = listOf(
        TextFragment("Aug", x = 14f, y = 349f, page = 0),
        TextFragment("2", x = 28f, y = 349f, page = 0),
        TextFragment("Aug", x = 57f, y = 349f, page = 0),
        TextFragment("2", x = 71f, y = 349f, page = 0),
        TextFragment("PAYMENT", x = 100f, y = 349f, page = 0),
        TextFragment("RECEIVED", x = 145f, y = 349f, page = 0),
        TextFragment("100.00", x = 520f, y = 349f, page = 0),
    )
    private val creditMarkerRow = listOf(
        TextFragment("CR", x = 520f, y = 362f, page = 0),
    )

    // A foreign-currency debit: the Foreign Spend cell is printed without a leading digit before
    // the decimal point (".75", not "0.75"), which the numeric-cell pattern rejects - it must not
    // be mistaken for a second amount and corrupt the real, GBP figure.
    private val foreignRow = listOf(
        TextFragment("Aug", x = 14f, y = 380f, page = 0),
        TextFragment("5", x = 28f, y = 380f, page = 0),
        TextFragment("Aug", x = 57f, y = 380f, page = 0),
        TextFragment("5", x = 71f, y = 380f, page = 0),
        TextFragment("CLOUD", x = 100f, y = 380f, page = 0),
        TextFragment("HOST", x = 150f, y = 380f, page = 0),
        TextFragment("INC", x = 190f, y = 380f, page = 0),
        TextFragment(".75", x = 400f, y = 380f, page = 0),
        TextFragment("9.99", x = 520f, y = 380f, page = 0),
    )

    private val fragments = header + debitRow + creditAmountRow + creditMarkerRow + foreignRow

    private fun parse(): List<ParsedTransaction> {
        val layout = assertNotNull(ColumnDetector().detect(fragments, profile), "no column layout")
        val rows = TableRowAssembler().assemble(fragments, layout)
        return PdfTransactionParser().parse(rows, profile, statementYear = 2026)
    }

    @Test
    fun `Amex is registered as a PDF profile`() {
        assertEquals(Bank.AMEX, profile.bank)
        assertTrue(PdfBankProfiles.all.contains(profile))
    }

    @Test
    fun `Amex detection keyword matches its own page and nothing else`() {
        val amexPage = listOf(
            TextFragment("American", x = 72f, y = 42f, page = 0),
            TextFragment("Express®", x = 141f, y = 42f, page = 0),
        )
        assertEquals(Bank.AMEX, BankDetector().detect(amexPage)?.bank)

        val natwestPage = listOf(TextFragment("NatWest", x = 30f, y = 40f, page = 0))
        assertEquals(Bank.NATWEST, BankDetector().detect(natwestPage)?.bank)
    }

    @Test
    fun `column layout has no balance role`() {
        val layout = assertNotNull(ColumnDetector().detect(fragments, profile))
        assertEquals(setOf(ColumnRole.DATE, ColumnRole.DESCRIPTION, ColumnRole.AMOUNT), layout.columns.keys)
    }

    @Test
    fun `the leaked process-date month lands in the date cell but is tolerated`() {
        val layout = assertNotNull(ColumnDetector().detect(fragments, profile))
        val rows = TableRowAssembler().assemble(fragments, layout)
        val first = assertNotNull(rows.firstOrNull { it.description.startsWith("COFFEE") || it.description.contains("COFFEE") })
        // Both words of the transaction's own date, plus one leaked word from the process-date
        // column - never its day number, which lands far enough right to stay in DESCRIPTION.
        assertEquals("Jul 27 Jul", first.date)
    }

    @Test
    fun `parses three transactions with the correct sign for each`() {
        val txs = parse()
        assertEquals(3, txs.size)

        val debit = assertNotNull(txs.firstOrNull { it.description.contains("COFFEE") })
        assertEquals(-4.50, debit.amount, 0.001, "a bare amount is a debit")

        val credit = assertNotNull(txs.firstOrNull { it.description.contains("PAYMENT") })
        assertEquals(100.00, credit.amount, 0.001, "a CR on the following row makes it a credit")

        val foreign = assertNotNull(txs.firstOrNull { it.description.contains("CLOUD") })
        assertEquals(-9.99, foreign.amount, 0.001, "the stray Foreign Spend fragment must not corrupt the GBP amount")
    }

    @Test
    fun `dates parse despite the leaked token`() {
        val txs = parse()
        assertEquals(
            listOf("2026-07-27", "2026-08-02", "2026-08-05"),
            txs.map { it.date.toString() },
        )
    }

    @Test
    fun `the marker-only CR row is not itself a transaction`() {
        val txs = parse()
        assertTrue(txs.none { it.description.trim().equals("CR", ignoreCase = true) })
    }

    @Test
    fun `parsed amounts reconcile against the statement summary`() {
        val txs = parse()
        val previous = 200.00
        val closing = 114.49
        assertEquals(100.00, txs.filter { it.amount > 0 }.sumOf { it.amount }, 0.001, "credits")
        assertEquals(14.49, txs.filter { it.amount < 0 }.sumOf { -it.amount }, 0.001, "debits")
        // A credit card's closing balance moves the OPPOSITE way from a current account's: a
        // credit reduces what is owed and a debit increases it.
        assertEquals(closing, previous - txs.sumOf { it.amount }, 0.001, "closing balance")
    }
}
