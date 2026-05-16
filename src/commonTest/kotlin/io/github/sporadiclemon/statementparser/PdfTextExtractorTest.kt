package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PdfTextExtractorTest {
    @Test fun `JVM stub throws UnsupportedOperationException`() {
        // On JVM (which runs commonTest), extractText should throw
        assertFailsWith<UnsupportedOperationException> {
            PdfTextExtractor().extractText(ByteArray(0))
        }
    }
}
