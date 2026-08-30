package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate

class DuplicateChecker {

    /**
     * Matches [parsed] against [existing] by (date, amount, description), consuming one matching
     * [existing] entry per match rather than testing set membership. A statement can legitimately
     * contain two transactions with the same date, amount and description - a real American
     * Express statement carries two separate £15.00 charges at the same merchant on the same day
     * - so if only one of them was recorded before, only one of the freshly parsed pair should be
     * treated as the duplicate; the other is a genuinely new transaction that happens to look
     * identical. Set-based matching would have flagged both as duplicates of the single prior one.
     */
    fun check(existing: List<ExistingTransaction>, parsed: List<ParsedTransaction>): DuplicateCheckResult {
        if (existing.isEmpty()) return DuplicateCheckResult(newTransactions = parsed, duplicates = emptyList())

        val remainingCounts = existing
            .groupingBy { Key(it.date, it.amount, normalise(it.description)) }
            .eachCountTo(HashMap())

        val newTransactions = ArrayList<ParsedTransaction>(parsed.size)
        val duplicates = ArrayList<ParsedTransaction>()
        for (transaction in parsed) {
            val key = Key(transaction.date, transaction.amount, normalise(transaction.description))
            val remaining = remainingCounts[key] ?: 0
            if (remaining > 0) {
                remainingCounts[key] = remaining - 1
                duplicates.add(transaction)
            } else {
                newTransactions.add(transaction)
            }
        }
        return DuplicateCheckResult(newTransactions = newTransactions, duplicates = duplicates)
    }

    // A structured key rather than a delimiter-joined string: no concatenation per transaction,
    // and a description containing the delimiter cannot collide with another transaction.
    private data class Key(val date: LocalDate, val amount: Double, val description: String)

    private fun normalise(description: String): String = description.trim().lowercase()
}
