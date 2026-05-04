package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicateCheckerTest {

    private val checker = DuplicateChecker()
    private val date = LocalDate(2024, 1, 15)

    private fun existing(description: String, amount: Double, date: LocalDate = this.date) =
        ExistingTransaction(date = date, amount = amount, description = description)

    private fun parsed(description: String, amount: Double, date: LocalDate = this.date) =
        ParsedTransaction(date = date, amount = amount, description = description, raw = "")

    @Test
    fun `all new when existing is empty`() {
        val result = checker.check(
            existing = emptyList(),
            parsed = listOf(parsed("Tesco", -4.50), parsed("Salary", 1500.00)),
        )
        assertEquals(2, result.newTransactions.size)
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `all duplicates when parsed matches existing exactly`() {
        val result = checker.check(
            existing = listOf(existing("Tesco", -4.50), existing("Salary", 1500.00)),
            parsed = listOf(parsed("Tesco", -4.50), parsed("Salary", 1500.00)),
        )
        assertTrue(result.newTransactions.isEmpty())
        assertEquals(2, result.duplicates.size)
    }

    @Test
    fun `mixed result splits correctly`() {
        val result = checker.check(
            existing = listOf(existing("Tesco", -4.50)),
            parsed = listOf(parsed("Tesco", -4.50), parsed("Salary", 1500.00)),
        )
        assertEquals(1, result.newTransactions.size)
        assertEquals("Salary", result.newTransactions[0].description)
        assertEquals(1, result.duplicates.size)
        assertEquals("Tesco", result.duplicates[0].description)
    }

    @Test
    fun `description matching is case insensitive`() {
        val result = checker.check(
            existing = listOf(existing("TESCO STORES", -4.50)),
            parsed = listOf(parsed("tesco stores", -4.50)),
        )
        assertEquals(1, result.duplicates.size)
    }

    @Test
    fun `description matching trims whitespace`() {
        val result = checker.check(
            existing = listOf(existing("Tesco", -4.50)),
            parsed = listOf(parsed("  Tesco  ", -4.50)),
        )
        assertEquals(1, result.duplicates.size)
    }

    @Test
    fun `same description and amount on different dates are not duplicates`() {
        val result = checker.check(
            existing = listOf(existing("Gym", -30.00, LocalDate(2024, 1, 1))),
            parsed = listOf(parsed("Gym", -30.00, LocalDate(2024, 2, 1))),
        )
        assertEquals(1, result.newTransactions.size)
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `same date and description but different amounts are not duplicates`() {
        val result = checker.check(
            existing = listOf(existing("Tesco", -4.50)),
            parsed = listOf(parsed("Tesco", -8.00)),
        )
        assertEquals(1, result.newTransactions.size)
        assertTrue(result.duplicates.isEmpty())
    }
}
