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

    val all: List<PdfBankProfile> = listOf(NATWEST, MONZO, HSBC, STARLING)
}
