package io.github.sporadiclemon.statementparser

/**
 * Defines the mapping of CSV columns to transaction fields.
 *
 * @property dateIndex The 0-based index of the date column.
 * @property dateFormat The date format pattern (e.g., "dd/MM/yyyy").
 * @property amountIndex The 0-based index of the single amount column (if applicable).
 * @property amountInIndex The 0-based index of the "Money In" column (if separate).
 * @property amountOutIndex The 0-based index of the "Money Out" column (if separate).
 * @property descriptionIndex The 0-based index of the description column.
 */
data class ColumnMapping(
    val dateIndex: Int,
    val dateFormat: String,
    val amountIndex: Int?,
    val amountInIndex: Int?,
    val amountOutIndex: Int?,
    val descriptionIndex: Int,
)

/**
 * Defines a profile for a specific bank's CSV format.
 *
 * @property bank The [Bank] this profile belongs to.
 * @property headerSignature A set of column headers that uniquely identifies this bank's format.
 * @property mapping The [ColumnMapping] for this bank's CSV format.
 */
data class CsvBankProfile(
    val bank: Bank,
    val headerSignature: Set<String>,
    val mapping: ColumnMapping,
)
