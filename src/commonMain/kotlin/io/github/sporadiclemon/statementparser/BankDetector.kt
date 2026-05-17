package io.github.sporadiclemon.statementparser

class BankDetector {
    fun detect(fragments: List<TextFragment>): PdfBankProfile? {
        val firstPageText = fragments
            .filter { it.page == 0 }
            .joinToString(" ") { it.text }
        return PdfBankProfiles.all.firstOrNull { profile ->
            profile.detectionKeywords.any { keyword ->
                firstPageText.contains(keyword, ignoreCase = true)
            }
        }
    }
}
