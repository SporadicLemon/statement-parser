package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvParserTest {

    private val parser = CsvParser()

    // --- Header parsing ---

    @Test
    fun `parseHeaders returns column names from first line`() {
        val csv = "Date,Amount,Description\n01/01/2024,-50.00,Tesco"
        assertEquals(listOf("Date", "Amount", "Description"), parser.parseHeaders(csv))
    }

    @Test
    fun `parseHeaders handles quoted headers`() {
        val csv = "\"Date\",\"Amount\",\"Description\"\n01/01/2024,-50.00,Tesco"
        assertEquals(listOf("Date", "Amount", "Description"), parser.parseHeaders(csv))
    }

    // --- Monzo (split in/out columns) ---

    @Test
    fun `parses Monzo money-out row`() {
        val monzoMapping = CsvBankProfiles.all.first { it.bank == Bank.MONZO }.mapping
        // Monzo headers (18 cols): Transaction ID,Date,Time,Type,Name,Emoji,Category,Amount,Currency,Local amount,Local currency,Notes and #tags,Address,Receipt,Description,Category split,Money Out,Money In
        val row = "tx_001,15/01/2024,12:00:00,Payment,Tesco,,Groceries,-4.50,GBP,-4.50,GBP,,,,,, 4.50,"
        val result = parser.parse("header\n$row", monzoMapping).getOrThrow()
        assertEquals(1, result.size)
        assertEquals(LocalDate(2024, 1, 15), result[0].date)
        assertEquals(-4.50, result[0].amount)
        assertEquals("Tesco", result[0].description)
    }

    @Test
    fun `parses Monzo money-in row`() {
        val monzoMapping = CsvBankProfiles.all.first { it.bank == Bank.MONZO }.mapping
        val row = "tx_002,20/01/2024,09:00:00,Income,Employer,,Income,1500.00,GBP,1500.00,GBP,,,,,,, 1500.00"
        val result = parser.parse("header\n$row", monzoMapping).getOrThrow()
        assertEquals(1500.00, result[0].amount)
    }

    // --- Starling (single signed amount column) ---

    @Test
    fun `parses Starling debit row`() {
        val mapping = CsvBankProfiles.all.first { it.bank == Bank.STARLING }.mapping
        // Starling headers: Date,Counter Party,Reference,Type,Amount (GBP),Balance (GBP),Spending Category
        val row = "15/01/2024,Tesco,,FASTER_PAYMENT,-4.50,295.50,GROCERIES"
        val result = parser.parse("header\n$row", mapping).getOrThrow()
        assertEquals(LocalDate(2024, 1, 15), result[0].date)
        assertEquals(-4.50, result[0].amount)
        assertEquals("Tesco", result[0].description)
    }

    // --- Lloyds (split in/out columns) ---

    @Test
    fun `parses Lloyds debit row`() {
        val mapping = CsvBankProfiles.all.first { it.bank == Bank.LLOYDS }.mapping
        // Lloyds headers: Transaction Date,Transaction Type,Sort Code,Account Number,Transaction Description,Debit Amount,Credit Amount,Balance
        val row = "15/01/2024,DEB,12-34-56,12345678,Tesco,4.50,,295.50"
        val result = parser.parse("header\n$row", mapping).getOrThrow()
        assertEquals(LocalDate(2024, 1, 15), result[0].date)
        assertEquals(-4.50, result[0].amount)
        assertEquals("Tesco", result[0].description)
    }

    @Test
    fun `parses Lloyds credit row`() {
        val mapping = CsvBankProfiles.all.first { it.bank == Bank.LLOYDS }.mapping
        val row = "20/01/2024,CR,12-34-56,12345678,Salary,,1500.00,1795.50"
        val result = parser.parse("header\n$row", mapping).getOrThrow()
        assertEquals(1500.00, result[0].amount)
    }

    // --- Custom unknown-bank mapping ---

    @Test
    fun `parses with custom column mapping`() {
        val mapping = ColumnMapping(
            dateIndex = 0,
            dateFormat = "dd/MM/yyyy",
            amountIndex = 2,
            amountInIndex = null,
            amountOutIndex = null,
            descriptionIndex = 1,
        )
        val csv = "Date,Description,Amount\n15/01/2024,Coffee Shop,-3.50"
        val result = parser.parse(csv, mapping).getOrThrow()
        assertEquals(LocalDate(2024, 1, 15), result[0].date)
        assertEquals(-3.50, result[0].amount)
        assertEquals("Coffee Shop", result[0].description)
    }

    // --- Edge cases ---

    @Test
    fun `skips blank lines`() {
        val mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=2, amountInIndex=null, amountOutIndex=null, descriptionIndex=1)
        val csv = "Date,Description,Amount\n15/01/2024,Coffee,-3.50\n\n16/01/2024,Lunch,-8.00"
        val result = parser.parse(csv, mapping).getOrThrow()
        assertEquals(2, result.size)
    }

    @Test
    fun `skips row with unparseable date`() {
        val mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=2, amountInIndex=null, amountOutIndex=null, descriptionIndex=1)
        val csv = "Date,Description,Amount\nnot-a-date,Coffee,-3.50"
        val result = parser.parse(csv, mapping).getOrThrow()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `handles amounts with thousand separators`() {
        val mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=1, amountInIndex=null, amountOutIndex=null, descriptionIndex=2)
        val csv = "Date,Amount,Desc\n15/01/2024,\"1,500.00\",Salary"
        val result = parser.parse(csv, mapping).getOrThrow()
        assertEquals(1500.00, result[0].amount)
    }

    @Test
    fun `returns empty list for csv with header only`() {
        val mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=1, amountInIndex=null, amountOutIndex=null, descriptionIndex=2)
        val result = parser.parse("Date,Amount,Description", mapping).getOrThrow()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `handles amounts with multiple thousand groups`() {
        val mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=1, amountInIndex=null, amountOutIndex=null, descriptionIndex=2)
        val csv = "Date,Amount,Desc\n15/01/2024,\"12,345,678.90\",Bonus"
        val result = parser.parse(csv, mapping).getOrThrow()
        assertEquals(12345678.90, result[0].amount)
    }

    @Test
    fun `a comma used as a decimal separator is not corrupted into a thousandfold value`() {
        // "12,34" is not shaped like a thousands-grouped figure (a group of two digits, not
        // three), so it must not have its comma blindly stripped into "1234" - that would be a
        // hundredfold error with no sign anything went wrong. It should simply fail to parse.
        val mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=1, amountInIndex=null, amountOutIndex=null, descriptionIndex=2)
        val csv = "Date,Amount,Desc\n15/01/2024,\"12,34\",Coffee"
        val result = parser.parse(csv, mapping).getOrThrow()
        assertTrue(result.isEmpty(), "a comma-decimal figure should be dropped, not silently misparsed")
    }

    @Test
    fun `strips a leading UTF-8 BOM before reading headers`() {
        val csv = "﻿Date,Amount,Description\n15/01/2024,-3.50,Coffee"
        assertEquals(listOf("Date", "Amount", "Description"), parser.parseHeaders(csv))
    }

    @Test
    fun `strips a leading UTF-8 BOM before parsing rows`() {
        val mapping = ColumnMapping(dateIndex=0, dateFormat="dd/MM/yyyy", amountIndex=1, amountInIndex=null, amountOutIndex=null, descriptionIndex=2)
        val csv = "﻿Date,Amount,Description\n15/01/2024,-3.50,Coffee"
        val result = parser.parse(csv, mapping).getOrThrow()
        assertEquals(1, result.size)
        assertEquals(-3.50, result[0].amount)
    }
}
