package io.github.sporadiclemon.statementparser

actual class PdfTextExtractor actual constructor() {
    actual fun extractText(bytes: ByteArray): String =
        throw UnsupportedOperationException("PDF text extraction is not yet supported on JVM")
}
