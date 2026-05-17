package io.github.sporadiclemon.statementparser

data class PdfBankProfile(
    val bank: Bank,
    val detectionKeywords: List<String>,
    val columnHeaders: Map<ColumnRole, String>,
    val dateFormat: String,
    val dateIncludesYear: Boolean,
    val transactionTypePrefixes: List<String> = emptyList(),
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
            ColumnRole.DESCRIPTION to "Description (GBP)",
            ColumnRole.AMOUNT      to "Amount (GBP)",
            ColumnRole.BALANCE     to "Balance",
        ),
        dateFormat = "dd/MM/yyyy",
        dateIncludesYear = true,
    )

    val all: List<PdfBankProfile> get() = listOf(NATWEST, MONZO)
}
