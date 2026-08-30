package io.github.sporadiclemon.statementparser

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

actual class PdfTextExtractor actual constructor() {
    actual fun extractText(bytes: ByteArray): String {
        val doc = PDDocument.load(bytes)
        return try {
            PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
    }

    actual fun extract(bytes: ByteArray): List<TextFragment> {
        val doc = PDDocument.load(bytes)
        return try {
            val stripper = CoordinateStripper()
            stripper.getText(doc)
            stripper.fragments
        } finally {
            doc.close()
        }
    }
}

private class CoordinateStripper : PDFTextStripper() {
    val fragments = mutableListOf<TextFragment>()
    private var currentPage = 0

    override fun startPage(page: PDPage) {
        currentPage++
        super.startPage(page)
    }

    override fun writeString(string: String, textPositions: MutableList<TextPosition>) {
        var wordStart = -1
        val wordChars = StringBuilder()

        for (i in string.indices) {
            val ch = string[i]
            if (ch.isWhitespace()) {
                if (wordStart >= 0 && wordChars.isNotBlank()) {
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
        if (wordStart >= 0 && wordStart < textPositions.size && wordChars.isNotBlank()) {
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
