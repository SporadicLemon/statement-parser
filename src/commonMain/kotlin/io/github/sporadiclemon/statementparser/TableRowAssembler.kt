package io.github.sporadiclemon.statementparser

class TableRowAssembler(private val logger: ((String) -> Unit)? = null) {

    fun assemble(fragments: List<TextFragment>, layout: ColumnLayout): List<RawTableRow> {
        val relevant = fragments.filter { f ->
            if (f.page == layout.headerPage) f.y > layout.headerY else true
        }
        logger?.invoke("[TableRowAssembler] ${relevant.size} fragments after header filter (of ${fragments.size} total)")

        val rows = ColumnDetector().groupByRow(relevant)
        logger?.invoke("[TableRowAssembler] ${rows.size} candidate rows")

        val amountRoles = setOf(ColumnRole.AMOUNT_IN, ColumnRole.AMOUNT_OUT, ColumnRole.AMOUNT, ColumnRole.BALANCE)
        val numericRe = Regex("""^-?\d[\d,]*\.\d{2}$""")

        data class Classified(val fragment: TextFragment, val role: ColumnRole)

        val result = rows.mapNotNull { rowFragments ->
            val classified = rowFragments.mapNotNull { frag ->
                val role = ColumnRole.entries.firstOrNull { r ->
                    layout.columns[r]?.let { range -> frag.x in range } == true
                } ?: return@mapNotNull null
                val effectiveRole = if (role in amountRoles && !numericRe.matches(frag.text.trim())) {
                    ColumnRole.DESCRIPTION
                } else {
                    role
                }
                Classified(frag, effectiveRole)
            }

            fun columnText(role: ColumnRole): String? =
                classified.filter { it.role == role }
                    .sortedBy { it.fragment.x }
                    .joinToString(" ") { it.fragment.text }
                    .takeIf { it.isNotBlank() }

            val description = columnText(ColumnRole.DESCRIPTION) ?: return@mapNotNull null
            val row = RawTableRow(
                date        = columnText(ColumnRole.DATE),
                description = description,
                amountIn    = columnText(ColumnRole.AMOUNT_IN),
                amountOut   = columnText(ColumnRole.AMOUNT_OUT),
                amount      = columnText(ColumnRole.AMOUNT),
                balance     = columnText(ColumnRole.BALANCE),
                pageY       = rowFragments.minOf { it.y },
            )
            logger?.invoke("[TableRowAssembler] row: date=${row.date} desc=\"${row.description}\" in=${row.amountIn} out=${row.amountOut} amt=${row.amount} bal=${row.balance}")
            row
        }
        logger?.invoke("[TableRowAssembler] ${result.size} rows with description (skipped ${rows.size - result.size})")
        return result
    }
}
