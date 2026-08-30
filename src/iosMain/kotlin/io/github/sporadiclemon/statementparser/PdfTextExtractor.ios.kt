package io.github.sporadiclemon.statementparser

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage

actual class PdfTextExtractor actual constructor() {

    @OptIn(ExperimentalForeignApi::class)
    actual fun extractText(bytes: ByteArray): String {
        val document = openDocument(bytes) ?: return ""
        val result = StringBuilder()
        for (i in 0 until document.pageCount().toInt()) {
            document.pageAtIndex(i.toULong())?.string?.let {
                result.append(it)
                result.append("\n")
            }
        }
        return result.toString()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun extract(bytes: ByteArray): List<TextFragment> {
        val document = openDocument(bytes) ?: return emptyList()
        if (!isIos16Plus) {
            throw UnsupportedOperationException("PDF coordinate extraction requires iOS 16+")
        }
        return extractWithCoordinates(document)
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun openDocument(bytes: ByteArray): PDFDocument? {
        // usePinned cannot address element 0 of an empty array, and PDFDocument(data:) returns
        // nil for anything that is not a readable PDF — which cinterop types as non-null, so the
        // nil surfaces as a thrown error here rather than as a null to test.
        if (bytes.isEmpty()) return null
        val nsData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        return runCatching { PDFDocument(nsData) }.getOrNull()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun extractWithCoordinates(document: PDFDocument): List<TextFragment> {
        val fragments = mutableListOf<TextFragment>()
        for (pageIdx in 0 until document.pageCount().toInt()) {
            val page = document.pageAtIndex(pageIdx.toULong()) ?: continue
            val pageString = page.string ?: continue
            // numberOfCharacters counts PDFKit characters and pageString is UTF-16 units; the two
            // can disagree, and indexing past the string would abort the whole parse.
            val charCount = minOf(page.numberOfCharacters.toInt(), pageString.length)

            var wordStart = -1
            for (charIdx in 0 until charCount) {
                if (pageString[charIdx].isWhitespace()) {
                    if (wordStart >= 0) {
                        fragments.add(fragment(page, pageString, wordStart, charIdx, pageIdx))
                        wordStart = -1
                    }
                } else if (wordStart < 0) {
                    wordStart = charIdx
                }
            }
            if (wordStart >= 0) {
                fragments.add(fragment(page, pageString, wordStart, charCount, pageIdx))
            }
        }
        return fragments
    }

    // The word is already delimited by [start, end), so it is cut from the page string directly
    // rather than accumulated a character at a time, and the bounds rect is placed into native
    // memory once instead of once per axis.
    @OptIn(ExperimentalForeignApi::class)
    private fun fragment(page: PDFPage, pageString: String, start: Int, end: Int, pageIdx: Int): TextFragment {
        val bounds = page.characterBoundsAtIndex(start.toLong())
        val (x, y) = bounds.useContents { origin.x.toFloat() to origin.y.toFloat() }
        return TextFragment(
            text = pageString.substring(start, end),
            x = x,
            y = y,
            page = pageIdx,
        )
    }

    private companion object {
        @OptIn(ExperimentalForeignApi::class)
        val isIos16Plus: Boolean by lazy {
            NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 16L }
        }
    }
}
