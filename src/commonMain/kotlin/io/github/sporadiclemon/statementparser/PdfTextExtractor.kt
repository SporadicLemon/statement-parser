package io.github.sporadiclemon.statementparser

expect class PdfTextExtractor() {
    fun extract(bytes: ByteArray): List<TextFragment>
}
