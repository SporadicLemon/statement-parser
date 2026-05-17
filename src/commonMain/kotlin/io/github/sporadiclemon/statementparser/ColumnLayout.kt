package io.github.sporadiclemon.statementparser

data class ColumnLayout(
    val headerY: Float,
    val headerPage: Int,
    val columns: Map<ColumnRole, ClosedRange<Float>>,
)
