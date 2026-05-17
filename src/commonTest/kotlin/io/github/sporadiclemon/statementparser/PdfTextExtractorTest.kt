package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfTextExtractorTest {
    @Test fun `extract returns empty list for empty input`() {
        val fragments = PdfTextExtractor().extract(ByteArray(0))
        assertEquals(emptyList(), fragments)
    }
}
