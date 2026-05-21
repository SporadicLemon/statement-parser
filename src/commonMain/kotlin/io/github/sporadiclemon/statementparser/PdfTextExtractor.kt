package io.github.sporadiclemon.statementparser

expect class PdfTextExtractor() {
    fun extract(bytes: ByteArray): List<TextFragment>
    fun extractText(bytes: ByteArray): String
}
