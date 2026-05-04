package io.github.sporadiclemon.statementparser

object BankProfiles {
    val all: List<BankProfile> = listOf(
        BankProfile(
            name = "Monzo",
            headerSignature = setOf("Transaction ID", "Money Out", "Money In"),
            mapping = ColumnMapping(dateIndex=1, dateFormat="dd/MM/yyyy", amountIndex=null, amountInIndex=17, amountOutIndex=16, descriptionIndex=4),
        ),
        BankProfile(
            name = "Starling",
            headerSignature = setOf("Counter Party", "Spending Category"),
            mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=4, amountInIndex=null, amountOutIndex=null, descriptionIndex=1),
        ),
        BankProfile(
            name = "Barclays",
            headerSignature = setOf("Subcategory", "Memo"),
            mapping = ColumnMapping(dateIndex=1, dateFormat="dd/MM/yyyy", amountIndex=3, amountInIndex=null, amountOutIndex=null, descriptionIndex=5),
        ),
        BankProfile(
            name = "HSBC",
            headerSignature = setOf("Description", "Amount", "Balance"),
            mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=2, amountInIndex=null, amountOutIndex=null, descriptionIndex=1),
        ),
        BankProfile(
            name = "Lloyds",
            headerSignature = setOf("Transaction Type", "Sort Code", "Debit Amount", "Credit Amount"),
            mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=null, amountInIndex=6, amountOutIndex=5, descriptionIndex=4),
        ),
        BankProfile(
            name = "NatWest",
            headerSignature = setOf("Transaction type", "Sort code", "Account number"),
            mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=3, amountInIndex=null, amountOutIndex=null, descriptionIndex=2),
        ),
        BankProfile(
            name = "Santander",
            headerSignature = setOf("Date", "Description", "Amount"),
            mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=2, amountInIndex=null, amountOutIndex=null, descriptionIndex=1),
        ),
    )
}
