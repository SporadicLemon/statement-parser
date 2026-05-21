package io.github.sporadiclemon.statementparser

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectGetMinX
import platform.CoreGraphics.CGRectGetMinY
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.Foundation.create
import platform.PDFKit.PDFDocument

actual class PdfTextExtractor actual constructor() {
    @OptIn(ExperimentalForeignApi::class)
    actual fun extractText(bytes: ByteArray): String {
        val nsData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val document = PDFDocument(nsData) ?: return ""
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
        val nsData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val document = PDFDocument(nsData) ?: return emptyList()
        val isIos16Plus = NSProcessInfo.processInfo.operatingSystemVersion
            .useContents { majorVersion >= 16L }

        return if (isIos16Plus) {
            extractWithCoordinates(document)
        } else {
            throw UnsupportedOperationException("PDF coordinate extraction requires iOS 16+")
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun extractWithCoordinates(document: PDFDocument): List<TextFragment> {
        val fragments = mutableListOf<TextFragment>()
        for (pageIdx in 0 until document.pageCount().toInt()) {
            val page = document.pageAtIndex(pageIdx.toULong()) ?: continue
            val pageString = page.string ?: continue
            val charCount = page.numberOfCharacters.toInt()

            var wordStart = -1
            val wordBuilder = StringBuilder()

            for (charIdx in 0 until charCount) {
                val ch = pageString[charIdx]
                if (ch.isWhitespace()) {
                    if (wordStart >= 0 && wordBuilder.isNotBlank()) {
                        val bounds = page.characterBoundsAtIndex(wordStart.toLong())
                        fragments.add(
                            TextFragment(
                                text = wordBuilder.toString(),
                                x = CGRectGetMinX(bounds).toFloat(),
                                y = CGRectGetMinY(bounds).toFloat(),
                                page = pageIdx,
                            )
                        )
                    }
                    wordStart = -1
                    wordBuilder.clear()
                } else {
                    if (wordStart < 0) wordStart = charIdx
                    wordBuilder.append(ch)
                }
            }
            if (wordStart >= 0 && wordBuilder.isNotBlank()) {
                val bounds = page.characterBoundsAtIndex(wordStart.toLong())
                fragments.add(
                    TextFragment(
                        text = wordBuilder.toString(),
                        x = CGRectGetMinX(bounds).toFloat(),
                        y = CGRectGetMinY(bounds).toFloat(),
                        page = pageIdx,
                    )
                )
            }
        }
        return fragments
    }
}
