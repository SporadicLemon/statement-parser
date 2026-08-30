package io.github.sporadiclemon.statementparser

/**
 * A table cell holding a money figure.
 *
 * Statements differ in how they dress the number: a currency symbol (Starling prints "£80.00"
 * in its IN/OUT columns), a trailing credit marker (HSBC credit cards print "10.00CR"), or a
 * bare figure (NatWest, Monzo). All three are the same thing - a value in an amount column -
 * and must classify as one, or the figure is mistaken for description text.
 */
private val NUMERIC_AMOUNT = Regex("""^[-+]?[£$€]?-?\d[\d,]*\.\d{2}\s?(CR|DR)?$""", RegexOption.IGNORE_CASE)

private val AMOUNT_ROLES = setOf(
    ColumnRole.AMOUNT_IN, ColumnRole.AMOUNT_OUT, ColumnRole.AMOUNT, ColumnRole.BALANCE,
)

class TableRowAssembler(private val logger: ((String) -> Unit)? = null) {

    fun assemble(fragments: List<TextFragment>, layout: ColumnLayout): List<RawTableRow> {
        val relevant = fragments.filter { f ->
            if (f.page == layout.headerPage) f.y > layout.headerY else true
        }
        logger?.invoke("[TableRowAssembler] ${relevant.size} fragments after header filter (of ${fragments.size} total)")

        val rows = groupFragmentsByRow(relevant)
        logger?.invoke("[TableRowAssembler] ${rows.size} candidate rows")

        // Flatten the layout once: an x-band per role, tested in ColumnRole declaration order.
        val bands = ColumnRole.entries.mapNotNull { role -> layout.columns[role]?.let { role to it } }

        val roleCount = ColumnRole.entries.size
        // Reused across rows; each slot holds the fragment texts assigned to that role, in x order.
        val buckets = Array(roleCount) { mutableListOf<String>() }

        val result = ArrayList<RawTableRow>(rows.size)
        for (rowFragments in rows) {
            rowFragments.sortBy { it.x } // column text is joined left-to-right
            for (bucket in buckets) bucket.clear()

            var minY = Float.MAX_VALUE
            for (frag in rowFragments) {
                if (frag.y < minY) minY = frag.y
                val role = bands.firstOrNull { (_, range) -> frag.x in range }?.first ?: continue
                // A non-numeric value sitting in an amount column is overflowed description text.
                val effectiveRole =
                    if (role in AMOUNT_ROLES && !NUMERIC_AMOUNT.matches(frag.text.trim())) ColumnRole.DESCRIPTION
                    else role
                buckets[effectiveRole.ordinal].add(frag.text)
            }

            val description = columnText(buckets, ColumnRole.DESCRIPTION) ?: continue
            val row = RawTableRow(
                date        = columnText(buckets, ColumnRole.DATE),
                description = description,
                amountIn    = columnText(buckets, ColumnRole.AMOUNT_IN),
                amountOut   = columnText(buckets, ColumnRole.AMOUNT_OUT),
                amount      = columnText(buckets, ColumnRole.AMOUNT),
                balance     = columnText(buckets, ColumnRole.BALANCE),
                pageY       = minY,
            )
            logger?.invoke("[TableRowAssembler] row: date=${row.date} desc=\"${row.description}\" in=${row.amountIn} out=${row.amountOut} amt=${row.amount} bal=${row.balance}")
            result.add(row)
        }

        logger?.invoke("[TableRowAssembler] ${result.size} rows with description (skipped ${rows.size - result.size})")
        return result
    }

    private fun columnText(buckets: Array<MutableList<String>>, role: ColumnRole): String? =
        buckets[role.ordinal].takeIf { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.takeIf { it.isNotBlank() }
}
