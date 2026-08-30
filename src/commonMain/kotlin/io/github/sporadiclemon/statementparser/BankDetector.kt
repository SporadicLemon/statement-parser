package io.github.sporadiclemon.statementparser

class BankDetector(private val logger: ((String) -> Unit)? = null) {
    fun detect(
        fragments: List<TextFragment>,
        hintProfile: PdfBankProfile? = null,
    ): PdfBankProfile? {
        val firstPageText =
            fragments
                .filter { it.page == 0 }
                .joinToString(" ") { it.text }
        logger?.invoke("[BankDetector] first-page text (${firstPageText.length} chars): ${firstPageText.take(200)}")
        val candidates = if (hintProfile != null) listOf(hintProfile) else PdfBankProfiles.all
        logger?.invoke("[BankDetector] checking ${candidates.size} profile(s): ${candidates.map { it.bank.displayName }}")
        return candidates.firstOrNull { profile ->
            val matched = profile.detectionKeywords.firstOrNull { firstPageText.contains(it, ignoreCase = true) }
            if (matched != null) logger?.invoke("[BankDetector] matched ${profile.bank.displayName} via keyword \"$matched\"")
            else logger?.invoke("[BankDetector] no match for ${profile.bank.displayName} (tried: ${profile.detectionKeywords})")
            matched != null
        }
    }
}
