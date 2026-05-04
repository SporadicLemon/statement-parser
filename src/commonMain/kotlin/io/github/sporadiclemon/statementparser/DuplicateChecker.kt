package io.github.sporadiclemon.statementparser

class DuplicateChecker {

    fun check(existing: List<ExistingTransaction>, parsed: List<ParsedTransaction>): DuplicateCheckResult {
        val existingKeys = existing.mapTo(HashSet()) { key(it) }
        val (duplicates, newTransactions) = parsed.partition { key(it) in existingKeys }
        return DuplicateCheckResult(newTransactions = newTransactions, duplicates = duplicates)
    }

    private fun key(t: ExistingTransaction): String = "${t.date}|${t.amount}|${t.description.trim().lowercase()}"
    private fun key(t: ParsedTransaction): String = "${t.date}|${t.amount}|${t.description.trim().lowercase()}"
}
