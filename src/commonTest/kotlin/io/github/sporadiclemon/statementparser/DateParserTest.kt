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

    @Test
    fun `parses MMM d with year hint`() {
        assertEquals(LocalDate(2026, 7, 27), DateParser.parse("Jul 27", "MMM d", yearHint = 2026))
        assertEquals(LocalDate(2026, 8, 2), DateParser.parse("Aug 2", "MMM d", yearHint = 2026))
    }

    @Test
    fun `MMM d ignores a trailing third token`() {
        // American Express prints a second date column right where the DATE/DESCRIPTION column
        // boundary falls, so that column's month name (never its day, which lands further right)
        // leaks into the same cell as the real transaction date: "Jul 27 Jul" rather than "Jul
        // 27". The first two tokens are still the transaction's own date, so reading only those
        // recovers it instead of failing the parse outright.
        assertEquals(LocalDate(2026, 7, 27), DateParser.parse("Jul 27 Jul", "MMM d", yearHint = 2026))
    }

    @Test
    fun `MMM d returns null when the day or month is missing`() {
        assertNull(DateParser.parse("Jul", "MMM d", yearHint = 2026))
        assertNull(DateParser.parse("27", "MMM d", yearHint = 2026))
    }
}
