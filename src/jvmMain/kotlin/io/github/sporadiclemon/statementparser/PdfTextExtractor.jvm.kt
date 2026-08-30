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
                    addWord(wordChars.toString(), wordStart, i, textPositions)
                    wordChars.clear()
                    wordStart = -1
                }
            } else {
                if (wordStart < 0) wordStart = i
                wordChars.append(ch)
            }
        }
        if (wordStart >= 0 && wordChars.isNotEmpty()) {
            addWord(wordChars.toString(), wordStart, string.length, textPositions)
        }

        super.writeString(string, textPositions)
    }

    // [start, end) index the string; textPositions is parallel to it, so the word runs from the
    // left edge of its first glyph to the right edge of its last.
    private fun addWord(text: String, start: Int, end: Int, textPositions: List<TextPosition>) {
        if (start >= textPositions.size) return
        val first = textPositions[start]
        val last = textPositions[(end - 1).coerceAtMost(textPositions.size - 1)]
        fragments.add(
            TextFragment(
                text = text,
                x = first.xDirAdj,
                y = first.yDirAdj,
                page = currentPage - 1,
                width = (last.xDirAdj + last.widthDirAdj - first.xDirAdj).coerceAtLeast(0f),
            )
        )
    }
}
