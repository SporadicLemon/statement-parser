package io.github.sporadiclemon.statementparser

class ColumnDetector {

    fun detect(fragments: List<TextFragment>, profile: PdfBankProfile): ColumnLayout? {
        val rows = groupByRow(fragments)

        // Find the row containing all column header phrases
        val headerRow = rows.firstOrNull { row ->
            profile.columnHeaders.values.all { header -> findPhraseX(row, header) != null }
        } ?: return null

        val headerY = headerRow.minOf { it.y }
        val headerPage = headerRow.first().page

        // Map each ColumnRole to the x-position of its header fragment
        val centers = mutableMapOf<ColumnRole, Float>()
        profile.columnHeaders.forEach { (role, header) ->
            centers[role] = findPhraseX(headerRow, header) ?: return null
        }

        val sortedEntries = centers.entries.sortedBy { it.value }
        val boundaries = mutableMapOf<ColumnRole, ClosedRange<Float>>()
        sortedEntries.forEachIndexed { i, (role, centerX) ->
            val left = if (i == 0) 0f else (sortedEntries[i - 1].value + centerX) / 2f
            val right = if (i == sortedEntries.lastIndex) Float.MAX_VALUE
                        else (centerX + sortedEntries[i + 1].value) / 2f
            boundaries[role] = left..right
        }

        return ColumnLayout(headerY = headerY, headerPage = headerPage, columns = boundaries)
    }

    // Finds the x-position of a multi-word phrase in a row of fragments.
    // Uses bidirectional prefix matching so "Paym" matches "Payment" (and vice-versa),
    // and consecutive words like ["Paid","out"] are distinguished from ["Paid","in"].
    private fun findPhraseX(row: List<TextFragment>, phrase: String): Float? {
        val words = phrase.trim().split(" ").filter { it.isNotEmpty() }
        val sorted = row.sortedBy { it.x }
        for (i in 0..sorted.size - words.size) {
            if (words.indices.all { j ->
                val fText = sorted[i + j].text
                fText.startsWith(words[j], ignoreCase = true) ||
                    words[j].startsWith(fText, ignoreCase = true)
            }) return sorted[i].x
        }
        return null
    }

    internal fun groupByRow(fragments: List<TextFragment>): List<List<TextFragment>> {
        val sorted = fragments.sortedWith(compareBy({ it.page }, { it.y }))
        val groups = mutableListOf<MutableList<TextFragment>>()
        var currentGroup = mutableListOf<TextFragment>()
        var lastPage = -1

        for (f in sorted) {
            val anchorY = currentGroup.firstOrNull()?.y ?: f.y
            if (f.page != lastPage || kotlin.math.abs(f.y - anchorY) > 2f) {
                if (currentGroup.isNotEmpty()) groups.add(currentGroup)
                currentGroup = mutableListOf()
                lastPage = f.page
            }
            currentGroup.add(f)
        }
        if (currentGroup.isNotEmpty()) groups.add(currentGroup)
        return groups
    }
}
