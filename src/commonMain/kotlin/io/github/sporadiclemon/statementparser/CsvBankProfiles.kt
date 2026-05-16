package io.github.sporadiclemon.statementparser

object CsvBankProfiles {
    val all: List<CsvBankProfile> = listOf(
        CsvBankProfile(
            name = "Monzo",
            headerSignature = setOf("Transaction ID", "Money Out", "Money In"),
            mapping = ColumnMapping(dateIndex=1, dateFormat="dd/MM/yyyy", amountIndex=null, amountInIndex=17, amountOutIndex=16, descriptionIndex=4),
        ),
        CsvBankProfile(
            name = "Starling",
            headerSignature = setOf("Counter Party", "Spending Category"),
            mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=4, amountInIndex=null, amountOutIndex=null, descriptionIndex=1),
        ),
        CsvBankProfile(
            name = "Barclays",
            headerSignature = setOf("Subcategory", "Memo"),
            mapping = ColumnMapping(dateIndex=1, dateFormat="dd/MM/yyyy", amountIndex=3, amountInIndex=null, amountOutIndex=null, descriptionIndex=5),
        ),
        CsvBankProfile(
            name = "HSBC",
            headerSignature = setOf("Description", "Amount", "Balance"),
            mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=2, amountInIndex=null, amountOutIndex=null, descriptionIndex=1),
        ),
        CsvBankProfile(
            name = "Lloyds",
            headerSignature = setOf("Transaction Type", "Sort Code", "Debit Amount", "Credit Amount"),
            mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=null, amountInIndex=6, amountOutIndex=5, descriptionIndex=4),
        ),
        CsvBankProfile(
            name = "NatWest",
            headerSignature = setOf("Transaction type", "Sort code", "Account number"),
            mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=3, amountInIndex=null, amountOutIndex=null, descriptionIndex=2),
        ),
        CsvBankProfile(
            name = "Santander",
            headerSignature = setOf("Date", "Description", "Amount"),
            mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=2, amountInIndex=null, amountOutIndex=null, descriptionIndex=1),
        ),
    )
}
