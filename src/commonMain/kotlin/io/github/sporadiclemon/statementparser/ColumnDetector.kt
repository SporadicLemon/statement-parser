package io.github.sporadiclemon.statementparser

class ColumnDetector {

    fun detect(fragments: List<TextFragment>, profile: PdfBankProfile): ColumnLayout? {
        val rows = groupByRow(fragments)

        // Find the row containing all column header keywords
        val headerRow = rows.firstOrNull { row ->
            profile.columnHeaders.values.all { header ->
                val firstWord = header.split(" ").first()
                row.any { it.text.startsWith(firstWord, ignoreCase = true) }
            }
        } ?: return null

        val headerY = headerRow.minOf { it.y }
        val headerPage = headerRow.first().page

        // Map each ColumnRole to the x-position of its header fragment
        val centers = mutableMapOf<ColumnRole, Float>()
        profile.columnHeaders.forEach { (role, header) ->
            val firstWord = header.split(" ").first()
            val fragment = headerRow.firstOrNull { it.text.startsWith(firstWord, ignoreCase = true) }
                ?: return null
            centers[role] = fragment.x
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
