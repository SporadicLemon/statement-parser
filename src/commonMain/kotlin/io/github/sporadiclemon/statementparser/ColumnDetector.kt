package io.github.sporadiclemon.statementparser

import kotlin.math.abs

/** Fragments closer together than this on the y-axis belong to the same visual row. */
private const val ROW_Y_TOLERANCE = 2f

/** How far above or below the heading a stray column label may sit and still count as part of it. */
private const val HEADER_ROW_SLACK = 14f

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
        val anchors = LinkedHashMap<ColumnRole, Float>(headers.size)

        var bestRow: MutableList<TextFragment>? = null
        var bestMatches = 0
        val headerRow = rows.firstOrNull { row ->
            row.sortBy { it.x } // findPhrase matches consecutive fragments, so needs x order
            anchors.clear()
            val matched = headers.count { (role, header) ->
                val bounds = findPhrase(row, header)
                if (bounds != null) anchors[role] = anchorOf(role, bounds)
                bounds != null
            }
            if (matched > bestMatches) {
                bestMatches = matched
                bestRow = row
            }
            matched == headers.size
        } ?: retryAcrossNearbyRows(rows, bestRow, bestMatches, headers, anchors)

        if (headerRow == null) {
            logger?.invoke("[ColumnDetector] header row not found; searched for: ${profile.columnHeaders.values}")
            return null
        }

        val headerY = headerRow.minOf { it.y }
        val headerPage = headerRow.first().page
        logger?.invoke("[ColumnDetector] header row: page=$headerPage y=$headerY fragments=${headerRow.map { "\"${it.text}\"@${it.x.toInt()}" }}")
        anchors.forEach { (role, a) ->
            val edge = if (role in AMOUNT_ROLES) "right" else "left"
            logger?.invoke("[ColumnDetector] $role ← \"${profile.columnHeaders[role]}\" $edge edge @ ${a.toInt()}")
        }

        // Each column owns the x-band running to the midpoint between its anchor and its
        // neighbours'. Because text and amount anchors sit on the edge their own cells align to,
        // the midpoints fall in the gutters between columns rather than inside one of them.
        val sortedEntries = anchors.entries.sortedBy { it.value }
        val boundaries = LinkedHashMap<ColumnRole, ClosedRange<Float>>(sortedEntries.size)
        sortedEntries.forEachIndexed { i, (role, anchor) ->
            val left = if (i == 0) 0f else (sortedEntries[i - 1].value + anchor) / 2f
            val right = if (i == sortedEntries.lastIndex) Float.MAX_VALUE
                        else (anchor + sortedEntries[i + 1].value) / 2f
            boundaries[role] = left..right
        }

        return ColumnLayout(headerY = headerY, headerPage = headerPage, columns = boundaries)
    }

    /**
     * The x-coordinate a column's band is centred on.
     *
     * Text columns are left-aligned, so their heading's left edge marks where their cells begin.
     * Amount columns are right-aligned, so their cells end where the heading ends and start
     * further right the shorter the figure - a NatWest "Paid In(£)" heading begins 40pt left of
     * "Withdrawn(£)", yet "7.00" beneath it begins to the right of that heading's neighbour.
     * Anchoring an amount column on its heading's right edge measures it from the edge its own
     * figures line up on, so a short figure stays inside its column.
     */
    private fun anchorOf(role: ColumnRole, bounds: PhraseBounds): Float =
        if (role in AMOUNT_ROLES) bounds.right else bounds.left

    /**
     * Some statements set one column heading on its own line - an HSBC credit card puts "Amount"
     * a few points above "Received By Us / Transaction Date / Details" - so no single row holds
     * every phrase. When the best row found most of them, retry it widened to the rows sitting
     * within [HEADER_ROW_SLACK] of it on the same page.
     *
     * Only reached when the strict single-row search failed, so a statement whose heading really
     * is one row behaves exactly as before.
     */
    private fun retryAcrossNearbyRows(
        rows: List<MutableList<TextFragment>>,
        bestRow: MutableList<TextFragment>?,
        bestMatches: Int,
        headers: List<Map.Entry<ColumnRole, String>>,
        anchors: MutableMap<ColumnRole, Float>,
    ): MutableList<TextFragment>? {
        if (bestRow == null || bestMatches * 2 < headers.size) return null
        val anchorY = bestRow.minOf { it.y }
        val page = bestRow.first().page
        val widened = rows
            .filter { it.first().page == page && abs(it.minOf { f -> f.y } - anchorY) <= HEADER_ROW_SLACK }
            .flatten()
            .sortedBy { it.x }
            .toMutableList()

        anchors.clear()
        val all = headers.all { (role, header) ->
            val bounds = findPhrase(widened, header)
            if (bounds != null) anchors[role] = anchorOf(role, bounds)
            bounds != null
        }
        if (!all) return null
        logger?.invoke("[ColumnDetector] header spans rows within ${HEADER_ROW_SLACK}pt of y=$anchorY")
        return widened
    }

    /**
     * Where a matched header phrase sits: the left edge of its first word and the right edge of
     * its last. A phrase spans several fragments ("Paid" + "In(£)"), so the two are not the same
     * fragment and neither edge can be derived from the other.
     */
    private class PhraseBounds(val left: Float, val right: Float)

    // Finds a multi-word phrase in a row of fragments (already x-sorted).
    // Uses bidirectional prefix matching so "Paym" matches "Payment" (and vice-versa),
    // and consecutive words like ["Paid","out"] are distinguished from ["Paid","in"].
    private fun findPhrase(row: List<TextFragment>, phrase: String): PhraseBounds? {
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
            if (matched) return PhraseBounds(left = row[i].x, right = row[i + words.lastIndex].right)
        }
        return null
    }

    internal fun groupByRow(fragments: List<TextFragment>): List<List<TextFragment>> =
        groupFragmentsByRow(fragments)
}
