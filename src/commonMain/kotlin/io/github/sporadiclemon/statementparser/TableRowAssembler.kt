package io.github.sporadiclemon.statementparser

class TableRowAssembler {

    fun assemble(fragments: List<TextFragment>, layout: ColumnLayout): List<RawTableRow> {
        val relevant = fragments.filter { f ->
            if (f.page == layout.headerPage) f.y > layout.headerY else true
        }

        val rows = ColumnDetector().groupByRow(relevant)

        return rows.mapNotNull { rowFragments ->
            fun columnText(role: ColumnRole): String? =
                layout.columns[role]?.let { range ->
                    rowFragments.filter { it.x in range }
                        .sortedBy { it.x }
                        .joinToString(" ") { it.text }
                        .takeIf { it.isNotBlank() }
                }

            val description = columnText(ColumnRole.DESCRIPTION) ?: return@mapNotNull null
            RawTableRow(
                date        = columnText(ColumnRole.DATE),
                description = description,
                amountIn    = columnText(ColumnRole.AMOUNT_IN),
                amountOut   = columnText(ColumnRole.AMOUNT_OUT),
                amount      = columnText(ColumnRole.AMOUNT),
                balance     = columnText(ColumnRole.BALANCE),
                pageY       = rowFragments.minOf { it.y },
            )
        }
    }
}
