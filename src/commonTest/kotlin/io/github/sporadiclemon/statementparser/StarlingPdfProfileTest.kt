package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Starling's PDF layout, end to end.
 *
 * The fragments below reproduce the geometry of a real Starling statement - the same x
 * positions, the same 12pt row pitch, the same two-line "ACCOUNT / BALANCE" heading and the
 * same "£" prefix on every figure - but every payee, date and amount is invented.
 *
 * The synthetic figures still reconcile the way a statement does: opening 12.00, in 48.00,
 * out 20.00, closing 40.00, with the running balance printed on each row.
 */
class StarlingPdfProfileTest {

    private val profile = PdfBankProfiles.STARLING

    // Heading row, the stray second half of "ACCOUNT BALANCE", and the opening-balance line.
    private val preamble = listOf(
        TextFragment("DATE", x = 30f, y = 315f, page = 0),
        TextFragment("TYPE", x = 93f, y = 315f, page = 0),
        TextFragment("TRANSACTION", x = 191f, y = 315f, page = 0),
        TextFragment("IN", x = 435f, y = 315f, page = 0),
        TextFragment("OUT", x = 484f, y = 315f, page = 0),
        TextFragment("ACCOUNT", x = 527f, y = 315f, page = 0),

        TextFragment("BALANCE", x = 530f, y = 327f, page = 0),

        TextFragment("OPENING", x = 93f, y = 342f, page = 0),
        TextFragment("BALANCE", x = 131f, y = 342f, page = 0),
        TextFragment("£12.00", x = 539f, y = 342f, page = 0),
    )

    private val transactionRows = listOf(
        // Money in: the figure sits in the IN column at x=419.
        TextFragment("05/01/2024", x = 30f, y = 354f, page = 0),
        TextFragment("FASTER", x = 93f, y = 354f, page = 0),
        TextFragment("PAYMENT", x = 122f, y = 354f, page = 0),
        TextFragment("ACME", x = 191f, y = 354f, page = 0),
        TextFragment("WIDGETS", x = 231f, y = 354f, page = 0),
        TextFragment("(REF/9)", x = 264f, y = 354f, page = 0),
        TextFragment("£40.00", x = 419f, y = 354f, page = 0),
        TextFragment("£52.00", x = 535f, y = 354f, page = 0),

        // Money out: the figure sits in the OUT column at x=475.
        TextFragment("09/01/2024", x = 30f, y = 366f, page = 0),
        TextFragment("ONLINE", x = 93f, y = 366f, page = 0),
        TextFragment("PAYMENT", x = 124f, y = 366f, page = 0),
        TextFragment("GLOBEX", x = 191f, y = 366f, page = 0),
        TextFragment("STORES", x = 233f, y = 366f, page = 0),
        TextFragment("£15.00", x = 475f, y = 366f, page = 0),
        TextFragment("£37.00", x = 539f, y = 366f, page = 0),

        TextFragment("14/01/2024", x = 30f, y = 378f, page = 0),
        TextFragment("ONLINE", x = 93f, y = 378f, page = 0),
        TextFragment("PAYMENT", x = 124f, y = 378f, page = 0),
        TextFragment("GLOBEX", x = 191f, y = 378f, page = 0),
        TextFragment("STORES", x = 233f, y = 378f, page = 0),
        TextFragment("£5.00", x = 475f, y = 378f, page = 0),
        TextFragment("£32.00", x = 539f, y = 378f, page = 0),

        TextFragment("21/01/2024", x = 30f, y = 390f, page = 0),
        TextFragment("FASTER", x = 93f, y = 390f, page = 0),
        TextFragment("PAYMENT", x = 122f, y = 390f, page = 0),
        TextFragment("INITECH", x = 191f, y = 390f, page = 0),
        TextFragment("LTD", x = 231f, y = 390f, page = 0),
        TextFragment("(REF/4)", x = 264f, y = 390f, page = 0),
        TextFragment("£8.00", x = 419f, y = 390f, page = 0),
        TextFragment("£40.00", x = 539f, y = 390f, page = 0),
    )

    private val fragments = preamble + transactionRows

    private fun parse(): List<ParsedTransaction> {
        val layout = assertNotNull(ColumnDetector().detect(fragments, profile), "no column layout")
        return PdfTransactionParser().parse(TableRowAssembler().assemble(fragments, layout), profile)
    }

    @Test
    fun `Starling is registered as a PDF profile`() {
        assertEquals(Bank.STARLING, profile.bank)
        assertTrue(PdfBankProfiles.all.contains(profile))
    }

    @Test
    fun `Starling detection keywords match page furniture`() {
        val page = listOf(
            TextFragment("24hr", x = 385f, y = 39f, page = 0),
            TextFragment("www.starlingbank.com", x = 451f, y = 52f, page = 0),
        )
        assertEquals(Bank.STARLING, BankDetector().detect(page)?.bank)

        // The footer wording works on its own too, split across per-word fragments.
        val footer = listOf(
            TextFragment("Starling", x = 29f, y = 811f, page = 0),
            TextFragment("Bank", x = 53f, y = 811f, page = 0),
            TextFragment("Limited", x = 70f, y = 811f, page = 0),
            TextFragment("is", x = 94f, y = 811f, page = 0),
        )
        assertEquals(Bank.STARLING, BankDetector().detect(footer)?.bank)
    }

    @Test
    fun `a payee named Starling on another bank's statement does not trigger Starling`() {
        // A real NatWest statement carries "PAUL STARLING MONIES VIA MOBILE" as a payee, so a
        // bare "Starling" keyword would hijack detection.
        val natwestPage = listOf(
            TextFragment("NatWest", x = 30f, y = 40f, page = 0),
            TextFragment("PAUL", x = 95f, y = 200f, page = 0),
            TextFragment("STARLING", x = 130f, y = 200f, page = 0),
            TextFragment("MONIES", x = 190f, y = 200f, page = 0),
            TextFragment("VIA", x = 240f, y = 200f, page = 0),
            TextFragment("MOBILE", x = 260f, y = 200f, page = 0),
        )
        assertEquals(Bank.NATWEST, BankDetector().detect(natwestPage)?.bank)

        // Without the NatWest marker the same payee still matches nothing at all.
        assertNull(BankDetector().detect(natwestPage.drop(1)))
    }

    @Test
    fun `detects the two-line ACCOUNT BALANCE heading from its first-line half`() {
        val layout = assertNotNull(ColumnDetector().detect(fragments, profile))
        assertEquals(315f, layout.headerY)
        assertEquals(setOf(
            ColumnRole.DATE, ColumnRole.DESCRIPTION,
            ColumnRole.AMOUNT_IN, ColumnRole.AMOUNT_OUT, ColumnRole.BALANCE,
        ), layout.columns.keys)
    }

    @Test
    fun `description column spans TYPE and TRANSACTION so the date cell stays a date`() {
        // Anchoring DESCRIPTION on "Transaction" instead would put the band midpoint at
        // (30+191)/2 = 110.5, dragging "FASTER"@93 into the date cell; the date would then
        // fail to parse and the row would be dropped.
        val layout = assertNotNull(ColumnDetector().detect(fragments, profile))
        val date = assertNotNull(layout.columns[ColumnRole.DATE])
        assertTrue(93f !in date, "the TYPE column must not fall inside the date band")

        val rows = TableRowAssembler().assemble(fragments, layout)
        val first = assertNotNull(rows.firstOrNull { it.date == "05/01/2024" })
        assertEquals("FASTER PAYMENT ACME WIDGETS (REF/9)", first.description)
    }

    @Test
    fun `parses four transactions with signed amounts and running balances`() {
        val txs = parse()
        assertEquals(4, txs.size)

        val expectedAmounts = listOf(40.00, -15.00, -5.00, 8.00)
        val expectedBalances = listOf(52.00, 37.00, 32.00, 40.00)
        txs.forEachIndexed { i, tx ->
            assertEquals(expectedAmounts[i], tx.amount, 0.001, "amount of transaction $i")
            assertEquals(expectedBalances[i], assertNotNull(tx.runningBalance), 0.001, "balance of transaction $i")
        }
        assertEquals(
            listOf("2024-01-05", "2024-01-09", "2024-01-14", "2024-01-21"),
            txs.map { it.date.toString() },
        )
    }

    @Test
    fun `descriptions merge the type and transaction columns`() {
        assertEquals(
            listOf(
                "FASTER PAYMENT ACME WIDGETS (REF/9)",
                "ONLINE PAYMENT GLOBEX STORES",
                "ONLINE PAYMENT GLOBEX STORES",
                "FASTER PAYMENT INITECH LTD (REF/4)",
            ),
            parse().map { it.description },
        )
    }

    @Test
    fun `the opening balance line is not a transaction`() {
        val txs = parse()
        assertTrue(txs.none { it.description.contains("OPENING", ignoreCase = true) })
        assertTrue(txs.none { it.amount in 11.999..12.001 })
    }

    @Test
    fun `parsed amounts reconcile against the statement summary`() {
        val txs = parse()
        val opening = 12.00
        assertEquals(48.00, txs.filter { it.amount > 0 }.sumOf { it.amount }, 0.001, "payments in")
        assertEquals(20.00, txs.filter { it.amount < 0 }.sumOf { -it.amount }, 0.001, "payments out")
        assertEquals(40.00, opening + txs.sumOf { it.amount }, 0.001, "closing balance")
    }
}
