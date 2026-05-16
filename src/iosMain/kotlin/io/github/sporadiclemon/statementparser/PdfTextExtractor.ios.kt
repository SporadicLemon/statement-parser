package io.github.sporadiclemon.statementparser

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument

actual class PdfTextExtractor actual constructor() {
    @OptIn(ExperimentalForeignApi::class)
    actual fun extractText(bytes: ByteArray): String {
        val nsData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val document = PDFDocument(nsData) ?: return ""
        return buildString {
            for (i in 0 until document.pageCount().toInt()) {
                val page = document.pageAtIndex(i.toULong()) ?: continue
                append(page.string ?: "")
                append("\n")
            }
        }
    }
}
