package io.github.sporadiclemon.statementparser

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

actual class PdfTextExtractor actual constructor() {
    actual fun extractText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val doc = PDDocument.load(bytes)
        return try {
            PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
    }

    actual fun extract(bytes: ByteArray): List<TextFragment> {
        if (bytes.isEmpty()) return emptyList()
        val doc = PDDocument.load(bytes)
        return try {
            val stripper = CoordinateStripper()
            // writeText(..) instead of getText(..): the coordinates come from the writeString
            // callback, so the assembled page text would only be a full second copy of the
            // document held in memory.
            stripper.writeText(doc, DiscardingWriter())
            stripper.fragments
        } finally {
            doc.close()
        }
    }
}

/**
 * Sink for PDFTextStripper output that is not needed; only the coordinates matter.
 *
 * A new instance per extraction: java.io.Writer synchronises on itself, so sharing one would
 * serialise concurrent extractions on every write PDFBox makes.
 */
private class DiscardingWriter : java.io.Writer() {
    override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
    override fun flush() = Unit
    override fun close() = Unit
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
