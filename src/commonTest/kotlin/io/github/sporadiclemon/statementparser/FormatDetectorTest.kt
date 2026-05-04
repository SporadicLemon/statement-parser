package io.github.sporadiclemon.statementparser

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatDetectorTest {
    private val detector = FormatDetector()

    @Test fun `detects CSV by file extension`() {
        assertEquals(StatementFormat.CSV, detector.detect("transactions.csv", "Date,Amount,Description"))
    }
    @Test fun `detects OFX by ofx extension`() {
        assertEquals(StatementFormat.OFX, detector.detect("statement.ofx", "anything"))
    }
    @Test fun `detects OFX by qfx extension`() {
        assertEquals(StatementFormat.OFX, detector.detect("statement.qfx", "anything"))
    }
    @Test fun `detects OFX by xml declaration in content`() {
        assertEquals(StatementFormat.OFX, detector.detect("unknown.txt", "<?xml version=\"1.0\"?><OFX>"))
    }
    @Test fun `detects OFX by OFX tag in content`() {
        assertEquals(StatementFormat.OFX, detector.detect("unknown.txt", "<OFX><BANKMSGSRSV1>"))
    }
    @Test fun `detects OFX by OFXHEADER prefix`() {
        assertEquals(StatementFormat.OFX, detector.detect("unknown.txt", "OFXHEADER:100\nDATA:OFXSGML"))
    }
    @Test fun `defaults to CSV for unknown extension with comma content`() {
        assertEquals(StatementFormat.CSV, detector.detect("export.txt", "Date,Amount,Description\n01/01/2024,100.00,Salary"))
    }
}
