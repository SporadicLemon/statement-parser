package io.github.sporadiclemon.statementparser

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

actual class PdfTextExtractor actual constructor() {
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
        val trimmed = string.trim()
        if (trimmed.isNotEmpty() && textPositions.isNotEmpty()) {
            fragments.add(
                TextFragment(
                    text = trimmed,
                    x = textPositions.first().xDirAdj,
                    y = textPositions.first().yDirAdj,
                    page = currentPage - 1,
                )
            )
        }
        super.writeString(string, textPositions)
    }
}
