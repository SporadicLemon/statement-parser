package io.github.sporadiclemon.statementparser

expect class PdfTextExtractor() {
    fun extractText(bytes: ByteArray): String
}
