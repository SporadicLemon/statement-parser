package io.github.sporadiclemon.statementparser

class FormatDetector {
    fun detect(fileName: String, content: String): StatementFormat {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "ofx" || ext == "qfx") return StatementFormat.OFX
        if (ext == "csv") return StatementFormat.CSV
        val trimmed = content.trimStart()
        if (trimmed.startsWith("<?xml") ||
            trimmed.contains("<OFX>", ignoreCase = true) ||
            trimmed.startsWith("OFXHEADER")
        ) return StatementFormat.OFX
        return StatementFormat.CSV
    }
}
