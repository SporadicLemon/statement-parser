package io.github.sporadiclemon.statementparser

data class PdfBankProfile(
    val bank: Bank,
    val detectionKeywords: List<String>,
    val columnHeaders: Map<ColumnRole, String>,
    val dateFormat: String,
    val dateIncludesYear: Boolean,
    val transactionTypePrefixes: List<String> = emptyList(),
    /** When true, description rows may appear before and after the date+amount row. */
    val descriptionSurroundsAmountRow: Boolean = false,
    /**
     * Suffix marking a figure in the single [ColumnRole.AMOUNT] column as money in, used by
     * statements that carry the sign as a marker rather than a minus - a credit card prints
     * "10.00" for a purchase and "10.00CR" for a payment or refund.
     *
     * When set, a cell ending with it parses positive with the suffix stripped and a cell
     * without it parses negative. Null (the default) leaves the figure's own sign alone.
     */
    val creditMarkerSuffix: String? = null,
)

object PdfBankProfiles {
    val NATWEST = PdfBankProfile(
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

    val MONZO = PdfBankProfile(
        bank = Bank.MONZO,
        detectionKeywords = listOf("Monzo"),
        columnHeaders = mapOf(
            ColumnRole.DATE        to "Date",
            ColumnRole.DESCRIPTION to "Description",
            ColumnRole.AMOUNT      to "Amount",
            ColumnRole.BALANCE     to "Balance",
        ),
        dateFormat = "dd/MM/yyyy",
        dateIncludesYear = true,
        descriptionSurroundsAmountRow = true,
    )

    val HSBC = PdfBankProfile(
        bank = Bank.HSBC,
        detectionKeywords = listOf("HSBC"),
        columnHeaders = mapOf(
            ColumnRole.DATE        to "Date",
            ColumnRole.DESCRIPTION to "Payment",
            ColumnRole.AMOUNT_OUT  to "Paid out",
            ColumnRole.AMOUNT_IN   to "Paid in",
            ColumnRole.BALANCE     to "Balance",
        ),
        dateFormat = "dd MMM yy",
        dateIncludesYear = true,
    )

    val STARLING = PdfBankProfile(
        bank = Bank.STARLING,
        // Deliberately not a bare "Starling": the word turns up inside payee names on other
        // banks' statements ("PAUL STARLING MONIES VIA MOBILE" on a NatWest one), which would
        // hijack detection. Both keywords below only appear in Starling's own page furniture.
        detectionKeywords = listOf("www.starlingbank.com", "Starling Bank Limited"),
        columnHeaders = mapOf(
            ColumnRole.DATE        to "Date",
            // Anchored on "Type", not "Transaction". A column owns the band running to the
            // midpoint between it and its neighbours, so anchoring the description on the far
            // right-hand "Transaction" heading would push the date band out over the type text
            // ("Faster Payment"), corrupt the date cell and drop the row. Anchoring on "Type"
            // makes the description span the Type and Transaction columns together, which is
            // what we want anyway: "Faster Payment ACME LTD REF/123".
            ColumnRole.DESCRIPTION to "Type",
            ColumnRole.AMOUNT_IN   to "In",
            ColumnRole.AMOUNT_OUT  to "Out",
            // The heading reads "Account Balance" wrapped over two lines; "Account" is the
            // half that sits on the heading row itself.
            ColumnRole.BALANCE     to "Account",
        ),
        dateFormat = "dd/MM/yyyy",
        dateIncludesYear = true,
    )

    /**
     * HSBC credit card. A different table from [HSBC]'s current account: two date columns
     * ("Received By Us" and "Transaction Date"), no running balance, and one amount column
     * whose credits carry a trailing "CR".
     *
     * The heading is split over two lines - "Amount" sits a couple of points above
     * "Received By Us / Transaction Date / Details", which in turn sits below the
     * "Your Transaction Details" title. [ColumnDetector] recovers it by widening the best
     * matching row to its neighbours, so the phrases here have to be findable in the three
     * lines merged and sorted by x, where "Your" and "Transaction" from the title interleave
     * with the column labels. "Received" and "Details" survive that; "Received By Us" does not.
     *
     * "Details" then anchors the description at the title's "Details" (x≈156) rather than the
     * column's own (x≈194), which is what keeps the posting date whole: the date band runs to
     * the midpoint with the description anchor, so it takes "27 Jul 26" and stops short of the
     * transaction date, which folds into the front of the description.
     */
    val HSBC_CREDIT_CARD = PdfBankProfile(
        bank = Bank.HSBC,
        detectionKeywords = listOf("Visa Card statement"),
        columnHeaders = mapOf(
            ColumnRole.DATE        to "Received",
            ColumnRole.DESCRIPTION to "Details",
            ColumnRole.AMOUNT      to "Amount",
        ),
        dateFormat = "dd MMM yy",
        dateIncludesYear = true,
        creditMarkerSuffix = "CR",
    )

    // HSBC_CREDIT_CARD comes before HSBC: detection takes the first profile whose keyword
    // is on page 1, and a card statement says "HSBC" too.
    val all: List<PdfBankProfile> = listOf(NATWEST, MONZO, HSBC_CREDIT_CARD, HSBC, STARLING)
}
