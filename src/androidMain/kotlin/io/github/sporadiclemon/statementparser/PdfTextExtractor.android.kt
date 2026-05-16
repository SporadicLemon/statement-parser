package io.github.sporadiclemon.statementparser

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

actual class PdfTextExtractor actual constructor() {
    actual fun extractText(bytes: ByteArray): String {
        val doc = PDDocument.load(bytes)
        return try {
            PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
    }
}
