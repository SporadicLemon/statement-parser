package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

/**
 * Tags this parser reads. Their patterns are compiled once at class-init rather than on every
 * lookup — a statement with N transactions performs 4N tag lookups, and compiling a Regex per
 * lookup dominated parsing cost.
 */
private val OFX_TAGS = listOf("BANKID", "ACCTID", "STMTTRN", "DTPOSTED", "TRNAMT", "NAME", "MEMO")

private val XML_TAG_PATTERNS: Map<String, Regex> =
    OFX_TAGS.associateWith { xmlPattern(it) }

private val SGML_TAG_PATTERNS: Map<String, Regex> =
    OFX_TAGS.associateWith { sgmlPattern(it) }

private val XML_BLOCK_PATTERNS: Map<String, Regex> =
    OFX_TAGS.associateWith { blockPattern(it) }

private val OPEN_TAG_PATTERNS: Map<String, Regex> =
    OFX_TAGS.associateWith { openTagPattern(it) }

private fun xmlPattern(tag: String) = Regex("<$tag>([^<]+)</$tag>", RegexOption.IGNORE_CASE)
private fun sgmlPattern(tag: String) = Regex("<$tag>([^\\r\\n<]+)", RegexOption.IGNORE_CASE)
private fun blockPattern(tag: String) = Regex("<$tag>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE)
private fun openTagPattern(tag: String) = Regex("<$tag>", RegexOption.IGNORE_CASE)

class OFXParser {
    fun parse(content: String): Result<ParsedStatement> =
        runCatching {
            val bankId = extractTag(content, "BANKID")
            val acctId = extractTag(content, "ACCTID")

            val accountInfo =
                if (bankId != null || acctId != null) ParsedAccountInfo(bankId, acctId)
                else null

            val transactionBlocks = extractAllTags(content, "STMTTRN")
            val transactions = transactionBlocks.mapNotNull { parseTransaction(it) }

            ParsedStatement(
                transactions = transactions,
                accountInfoResult = accountInfo?.let { AccountInfoResult.Found(it) } 
                    ?: AccountInfoResult.NotAvailable(AccountInfoUnavailableReason.MissingFromFile),
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
                month = cleaned.substring(4, 6).toInt(),
                day = cleaned.substring(6, 8).toInt(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTag(content: String, tag: String): String? {
        val xmlMatch = (XML_TAG_PATTERNS[tag] ?: xmlPattern(tag)).find(content)
        if (xmlMatch != null) return xmlMatch.groupValues[1].trim()
        val sgmlMatch = (SGML_TAG_PATTERNS[tag] ?: sgmlPattern(tag)).find(content)
        return sgmlMatch?.groupValues?.get(1)?.trim()
    }

    private fun extractAllTags(content: String, tag: String): List<String> {
        val xmlMatches = (XML_BLOCK_PATTERNS[tag] ?: blockPattern(tag))
            .findAll(content).map { it.groupValues[1] }.toList()
        if (xmlMatches.isNotEmpty()) return xmlMatches
        val parts = content.split(OPEN_TAG_PATTERNS[tag] ?: openTagPattern(tag))
        return if (parts.size > 1) parts.drop(1) else emptyList()
    }
}
