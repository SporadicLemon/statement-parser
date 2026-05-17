package io.github.sporadiclemon.statementparser

actual class PdfTextExtractor actual constructor() {
    actual fun extract(bytes: ByteArray): List<TextFragment> = emptyList()
}
