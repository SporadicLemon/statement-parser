package io.github.sporadiclemon.statementparser

class BankDetector(private val logger: ((String) -> Unit)? = null) {
    fun detect(
        fragments: List<TextFragment>,
        hintProfile: PdfBankProfile? = null,
    ): PdfBankProfile? {
        val firstPageText = buildString {
            for (f in fragments) {
                if (f.page != 0) continue
                if (isNotEmpty()) append(' ')
                append(f.text)
            }
        }
        logger?.invoke("[BankDetector] first-page text (${firstPageText.length} chars): ${firstPageText.take(200)}")
        val candidates = if (hintProfile != null) listOf(hintProfile) else PdfBankProfiles.all
        logger?.invoke("[BankDetector] checking ${candidates.size} profile(s): ${candidates.map { it.bank.displayName }}")

        // Lowercased once, so each keyword is a plain substring search rather than a
        // case-insensitive scan of the whole page per keyword per profile.
        val haystack = firstPageText.lowercase()
        return candidates.firstOrNull { profile ->
            val matched = profile.detectionKeywords.firstOrNull { haystack.contains(it.lowercase()) }
            if (matched != null) logger?.invoke("[BankDetector] matched ${profile.bank.displayName} via keyword \"$matched\"")
            else logger?.invoke("[BankDetector] no match for ${profile.bank.displayName} (tried: ${profile.detectionKeywords})")
            matched != null
        }
    }
}
