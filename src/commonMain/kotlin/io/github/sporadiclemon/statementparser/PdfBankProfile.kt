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
    val all: List<PdfBankProfile> =
        listOf(
            PdfBankProfile(
                name = "Monzo",
                bankNamePattern = Regex("Monzo Bank", RegexOption.IGNORE_CASE),
                transactionLinePattern =
                    Regex(
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
                transactionLinePattern =
                    Regex(
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
                transactionLinePattern =
                    Regex(
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
                transactionLinePattern =
                    Regex(
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
            // NatWest: captures the first amount after the description (debit or credit).
            // Sign direction requires real PDF validation — update in Task 7.
            PdfBankProfile(
                name = "NatWest",
                bankNamePattern = Regex("NatWest", RegexOption.IGNORE_CASE),
                transactionLinePattern =
                    Regex(
                        """^(\d{2} \w{3} \d{4})\s{2,}(.+?)\s{2,}([\d,]+\.\d{2})""",
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
