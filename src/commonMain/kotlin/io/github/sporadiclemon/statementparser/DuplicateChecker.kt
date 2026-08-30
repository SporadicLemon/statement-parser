package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

class DuplicateChecker {

    fun check(existing: List<ExistingTransaction>, parsed: List<ParsedTransaction>): DuplicateCheckResult {
        if (existing.isEmpty()) return DuplicateCheckResult(newTransactions = parsed, duplicates = emptyList())

        val existingKeys = existing.mapTo(HashSet(existing.size)) { Key(it.date, it.amount, normalise(it.description)) }
        val (duplicates, newTransactions) =
            parsed.partition { Key(it.date, it.amount, normalise(it.description)) in existingKeys }
        return DuplicateCheckResult(newTransactions = newTransactions, duplicates = duplicates)
    }

    // A structured key rather than a delimiter-joined string: no concatenation per transaction,
    // and a description containing the delimiter cannot collide with another transaction.
    private data class Key(val date: LocalDate, val amount: Double, val description: String)

    private fun normalise(description: String): String = description.trim().lowercase()
}
