package io.github.sporadiclemon.statementparser

/** OFX headers always sit at the very top of the file, so only the start needs scanning. */
private const val CONTENT_SNIFF_LIMIT = 2048

class FormatDetector {
    fun detect(fileName: String, content: String): StatementFormat {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "ofx" || ext == "qfx") return StatementFormat.OFX
        if (ext == "csv") return StatementFormat.CSV
        if (ext == "pdf") return StatementFormat.PDF

        // Sniff the content without copying it: trimStart() on a multi-megabyte CSV would
        // allocate a second copy of the whole file just to look at its first few characters.
        var start = 0
        while (start < content.length && content[start].isWhitespace()) start++
        if (content.startsWith("<?xml", start) || content.startsWith("OFXHEADER", start)) {
            return StatementFormat.OFX
        }
        val head = if (content.length - start > CONTENT_SNIFF_LIMIT) {
            content.substring(start, start + CONTENT_SNIFF_LIMIT)
        } else {
            content.substring(start)
        }
        if (head.contains("<OFX>", ignoreCase = true)) return StatementFormat.OFX
        return StatementFormat.CSV
    }
}
