package io.github.sporadiclemon.statementparser

/**
 * Represents the supported banks in the library.
 *
 * @property displayName The human-readable name of the bank.
 */
enum class Bank(val displayName: String) {
    /** Monzo Bank. */
    MONZO("Monzo"),
    /** Starling Bank. */
    STARLING("Starling"),
    /** Barclays Bank. */
    BARCLAYS("Barclays"),
    /** HSBC Bank. */
    HSBC("HSBC"),
    /** Lloyds Bank. */
    LLOYDS("Lloyds"),
    /** NatWest Bank. */
    NATWEST("NatWest"),
    /** Santander Bank. */
    SANTANDER("Santander"),
    /** Halifax Bank. */
    HALIFAX("Halifax"),
    /** American Express. */
    AMEX("American Express"),
}
