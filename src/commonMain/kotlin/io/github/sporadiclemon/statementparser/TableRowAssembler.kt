package io.github.sporadiclemon.statementparser

class TableRowAssembler {

    fun assemble(fragments: List<TextFragment>, layout: ColumnLayout): List<RawTableRow> {
        val relevant = fragments.filter { f ->
            if (f.page == layout.headerPage) f.y > layout.headerY else true
        }

        val rows = ColumnDetector().groupByRow(relevant)

        val amountRoles = setOf(ColumnRole.AMOUNT_IN, ColumnRole.AMOUNT_OUT, ColumnRole.AMOUNT, ColumnRole.BALANCE)
        val numericRe = Regex("""^\d[\d,]*\.\d{2}$""")

        data class Classified(val fragment: TextFragment, val role: ColumnRole)

        return rows.mapNotNull { rowFragments ->
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
