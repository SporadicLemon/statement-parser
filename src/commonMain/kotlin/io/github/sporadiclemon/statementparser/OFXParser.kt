package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

class OFXParser {

    fun parse(content: String): Result<ParsedStatement> = runCatching {
        val bankId = extractTag(content, "BANKID")
        val acctId = extractTag(content, "ACCTID")

        val accountInfoResult = if (bankId != null || acctId != null) {
            AccountInfoResult.Found(ParsedAccountInfo(institutionName = bankId, accountNumber = acctId))
        } else {
            AccountInfoResult.NotAvailable(AccountInfoUnavailableReason.MissingFromFile)
        }

        val transactionBlocks = extractAllTags(content, "STMTTRN")
        val transactions = transactionBlocks.mapNotNull { parseTransaction(it) }

        ParsedStatement(
            transactions = transactions,
            accountInfoResult = accountInfoResult,
            detectedBank = null,
            suggestedMapping = null,
            rawHeaders = null,
        )
    }

    private fun parseTransaction(block: String): ParsedTransaction? {
        val dateStr = extractTag(block, "DTPOSTED") ?: return null
        val amountStr = extractTag(block, "TRNAMT") ?: return null
        val description = extractTag(block, "NAME") ?: extractTag(block, "MEMO") ?: return null
        val date = parseOfxDate(dateStr) ?: return null
        val amount = amountStr.trim().toDoubleOrNull() ?: return null
        return ParsedTransaction(date = date, amount = amount, description = description.trim(), raw = block)
    }

    private fun parseOfxDate(dateStr: String): LocalDate? {
        return try {
            val cleaned = dateStr.trim().take(8)
            if (cleaned.length < 8) return null
            LocalDate(
                year = cleaned.substring(0, 4).toInt(),
                month = kotlinx.datetime.Month(cleaned.substring(4, 6).toInt()),
                day = cleaned.substring(6, 8).toInt(),
            )
        } catch (_: Exception) { null }
    }

    private fun extractTag(content: String, tag: String): String? {
        // Handle both <TAG>value</TAG> and <TAG>value (SGML-style)
        // Try <TAG>value</TAG> first
        val xmlMatch = Regex("<$tag>([^<]+)</$tag>", RegexOption.IGNORE_CASE).find(content)
        if (xmlMatch != null) return xmlMatch.groupValues[1].trim()

        // Fallback to SGML style: <TAG>value followed by newline or next tag
        val sgmlMatch = Regex("<$tag>([^\\r\\n<]+)", RegexOption.IGNORE_CASE).find(content)
        return sgmlMatch?.groupValues?.get(1)?.trim()
    }

    private fun extractAllTags(content: String, tag: String): List<String> {
        // STMTTRN is usually a block with a closing tag in modern OFX, 
        // but can be just a start tag in older OFX.
        // We'll support both.
        
        val xmlMatches = Regex("<$tag>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE)
            .findAll(content).map { it.groupValues[1] }.toList()
            
        if (xmlMatches.isNotEmpty()) return xmlMatches
        
        // If no closing tags found, try to find blocks starting with <tag> until next <tag> or end of parent block
        // This is trickier with regex, but we can try split by <TAG>
        val parts = content.split(Regex("<$tag>", RegexOption.IGNORE_CASE))
        if (parts.size > 1) {
            return parts.drop(1)
        }
        
        return emptyList()
    }
}
