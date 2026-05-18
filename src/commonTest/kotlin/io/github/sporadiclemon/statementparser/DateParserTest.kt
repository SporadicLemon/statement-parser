package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateParserTest {

    @Test
    fun `parses dd-slash-MM-slash-yyyy`() {
        assertEquals(LocalDate(2026, 4, 7), DateParser.parse("07/04/2026", "dd/MM/yyyy"))
    }

    @Test
    fun `parses dd MMM with year hint`() {
        assertEquals(LocalDate(2026, 4, 7), DateParser.parse("07 APR", "dd MMM", yearHint = 2026))
    }

    @Test
    fun `parses dd MMM with lowercase month`() {
        assertEquals(LocalDate(2026, 1, 15), DateParser.parse("15 jan", "dd MMM", yearHint = 2026))
    }

    @Test
    fun `parses dd MMM yyyy`() {
        assertEquals(LocalDate(2026, 4, 7), DateParser.parse("07 APR 2026", "dd MMM yyyy"))
    }

    @Test
    fun `returns null for malformed input`() {
        assertNull(DateParser.parse("not-a-date", "dd/MM/yyyy"))
        assertNull(DateParser.parse("32/01/2026", "dd/MM/yyyy"))
    }

    @Test
    fun `returns null for unknown format`() {
        assertNull(DateParser.parse("07-04-2026", "unknown"))
    }
}
