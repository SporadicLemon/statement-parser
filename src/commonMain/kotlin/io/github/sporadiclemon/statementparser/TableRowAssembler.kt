package io.github.sporadiclemon.statementparser

class TableRowAssembler {

    fun assemble(fragments: List<TextFragment>, layout: ColumnLayout): List<RawTableRow> {
        val relevant = fragments.filter { f ->
            if (f.page == layout.headerPage) f.y > layout.headerY else true
        }

        val columnDetector = ColumnDetector()
        val rows = columnDetector.groupByRow(relevant)

        return rows.mapNotNull { rowFragments ->
            val byRole = ColumnRole.entries.associateWith { role ->
                layout.columns[role]?.let { range ->
                    rowFragments.filter { it.x in range }.sortedBy { it.x }
                        .joinToString(" ") { it.text }.takeIf { it.isNotBlank() }
                }
            }

            val description = byRole[ColumnRole.DESCRIPTION] ?: return@mapNotNull null
            RawTableRow(
                date        = byRole[ColumnRole.DATE],
                description = description,
                amountIn    = byRole[ColumnRole.AMOUNT_IN],
                amountOut   = byRole[ColumnRole.AMOUNT_OUT],
                amount      = byRole[ColumnRole.AMOUNT],
                balance     = byRole[ColumnRole.BALANCE],
                pageY       = rowFragments.minOf { it.y },
            )
        }
    }
}
