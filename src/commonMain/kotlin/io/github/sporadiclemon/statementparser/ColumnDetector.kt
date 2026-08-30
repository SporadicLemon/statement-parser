package io.github.sporadiclemon.statementparser

import kotlin.math.abs

/** Fragments closer together than this on the y-axis belong to the same visual row. */
private const val ROW_Y_TOLERANCE = 2f

/**
 * Groups fragments into visual rows, ordered by page then y.
 *
 * Rows are returned mutable so callers that need left-to-right order can sort in place
 * without copying — only the rows they actually read.
 */
internal fun groupFragmentsByRow(fragments: List<TextFragment>): List<MutableList<TextFragment>> {
    if (fragments.isEmpty()) return emptyList()

    val sorted = fragments.sortedWith(compareBy({ it.page }, { it.y }))
    val groups = ArrayList<MutableList<TextFragment>>()
    var currentGroup = mutableListOf<TextFragment>()
    var anchorY = 0f
    var lastPage = -1

    for (f in sorted) {
        if (currentGroup.isEmpty()) {
            anchorY = f.y
        } else if (f.page != lastPage || abs(f.y - anchorY) > ROW_Y_TOLERANCE) {
            groups.add(currentGroup)
            currentGroup = mutableListOf()
            anchorY = f.y
        }
        lastPage = f.page
        currentGroup.add(f)
    }
    if (currentGroup.isNotEmpty()) groups.add(currentGroup)
    return groups
}

class ColumnDetector(private val logger: ((String) -> Unit)? = null) {

    fun detect(fragments: List<TextFragment>, profile: PdfBankProfile): ColumnLayout? {
        val rows = groupFragmentsByRow(fragments)
        logger?.invoke("[ColumnDetector] ${rows.size} rows from ${fragments.size} fragments")

        // Find the row containing all column header phrases, keeping the x-positions it yields
        // so the phrases are not located a second time.
        val headers = profile.columnHeaders.entries.toList()
        // Linked, not plain hash: ColumnLayout.columns is public API and its iteration
        // order must not vary between runs.
        val centers = LinkedHashMap<ColumnRole, Float>(headers.size)
        val headerRow = rows.firstOrNull { row ->
            row.sortBy { it.x } // findPhraseX matches consecutive fragments, so needs x order
            centers.clear()
            headers.all { (role, header) ->
                val x = findPhraseX(row, header)
                if (x != null) centers[role] = x
                x != null
            }
        }
        if (headerRow == null) {
            logger?.invoke("[ColumnDetector] header row not found; searched for: ${profile.columnHeaders.values}")
            return null
        }

        val headerY = headerRow.minOf { it.y }
        val headerPage = headerRow.first().page
        logger?.invoke("[ColumnDetector] header row: page=$headerPage y=$headerY fragments=${headerRow.map { "\"${it.text}\"@${it.x.toInt()}" }}")
        centers.forEach { (role, x) -> logger?.invoke("[ColumnDetector] $role ← \"${profile.columnHeaders[role]}\" @ ${x.toInt()}") }

        // Each column owns the x-band running to the midpoint between it and its neighbours.
        val sortedEntries = centers.entries.sortedBy { it.value }
        val boundaries = LinkedHashMap<ColumnRole, ClosedRange<Float>>(sortedEntries.size)
        sortedEntries.forEachIndexed { i, (role, centerX) ->
            val left = if (i == 0) 0f else (sortedEntries[i - 1].value + centerX) / 2f
            val right = if (i == sortedEntries.lastIndex) Float.MAX_VALUE
                        else (centerX + sortedEntries[i + 1].value) / 2f
            boundaries[role] = left..right
        }

        return ColumnLayout(headerY = headerY, headerPage = headerPage, columns = boundaries)
    }

    // Finds the x-position of a multi-word phrase in a row of fragments (already x-sorted).
    // Uses bidirectional prefix matching so "Paym" matches "Payment" (and vice-versa),
    // and consecutive words like ["Paid","out"] are distinguished from ["Paid","in"].
    private fun findPhraseX(row: List<TextFragment>, phrase: String): Float? {
        val words = phrase.trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        for (i in 0..row.size - words.size) {
            var matched = true
            for (j in words.indices) {
                val fText = row[i + j].text
                val word = words[j]
                if (!fText.startsWith(word, ignoreCase = true) && !word.startsWith(fText, ignoreCase = true)) {
                    matched = false
                    break
                }
            }
            if (matched) return row[i].x
        }
        return null
    }

    internal fun groupByRow(fragments: List<TextFragment>): List<List<TextFragment>> =
        groupFragmentsByRow(fragments)
}
