package io.github.sporadiclemon.statementparser

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

actual class PdfTextExtractor actual constructor() {
    actual fun extractText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val doc = try {
            Loader.loadPDF(bytes)
        } catch (_: java.io.IOException) {
            return ""
        }
        return try {
            PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
    }

    actual fun extract(bytes: ByteArray): List<TextFragment> {
        if (bytes.isEmpty()) return emptyList()
        val doc = try {
            Loader.loadPDF(bytes)
        } catch (_: java.io.IOException) {
            return emptyList()
        }
        return try {
            val stripper = JvmCoordinateStripper()
            stripper.getText(doc)
            stripper.fragments
        } finally {
            doc.close()
        }
    }
}

private class JvmCoordinateStripper : PDFTextStripper() {
    val fragments = mutableListOf<TextFragment>()
    private var currentPage = 0

    override fun startPage(page: PDPage) {
        currentPage++
        super.startPage(page)
    }

    @Throws(java.io.IOException::class)
    override fun writeString(string: String, textPositions: MutableList<TextPosition>) {
        var wordStart = -1
        val wordChars = StringBuilder()

        for (i in string.indices) {
            val ch = string[i]
            if (ch.isWhitespace()) {
                if (wordStart >= 0 && wordChars.isNotEmpty()) {
                    if (wordStart < textPositions.size) {
                        val pos = textPositions[wordStart]
                        fragments.add(
                            TextFragment(
                                text = wordChars.toString(),
                                x = pos.xDirAdj,
                                y = pos.yDirAdj,
                                page = currentPage - 1,
                            )
                        )
                    }
                    wordChars.clear()
                    wordStart = -1
                }
            } else {
                if (wordStart < 0) wordStart = i
                wordChars.append(ch)
            }
        }
        if (wordStart >= 0 && wordStart < textPositions.size && wordChars.isNotEmpty()) {
            val pos = textPositions[wordStart]
            fragments.add(
                TextFragment(
                    text = wordChars.toString(),
                    x = pos.xDirAdj,
                    y = pos.yDirAdj,
                    page = currentPage - 1,
                )
            )
        }

        super.writeString(string, textPositions)
    }
}
