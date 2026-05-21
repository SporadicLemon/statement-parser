package io.github.sporadiclemon.statementparser

class BankDetector {
    fun detect(
        fragments: List<TextFragment>,
        hintProfile: PdfBankProfile? = null,
    ): PdfBankProfile? {
        val firstPageText =
            fragments
                .filter { it.page == 0 }
                .joinToString(" ") { it.text }
        val candidates = if (hintProfile != null) listOf(hintProfile) else PdfBankProfiles.all
        return candidates.firstOrNull { profile ->
            profile.detectionKeywords.any { keyword ->
                firstPageText.contains(keyword, ignoreCase = true)
            }
        }
    }
}
