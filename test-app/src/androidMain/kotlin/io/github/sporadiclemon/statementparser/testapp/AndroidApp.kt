package io.github.sporadiclemon.statementparser.testapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.sporadiclemon.statementparser.AccountInfoResult
import io.github.sporadiclemon.statementparser.AccountInfoUnavailableReason
import io.github.sporadiclemon.statementparser.Bank
import io.github.sporadiclemon.statementparser.BankType
import io.github.sporadiclemon.statementparser.ParsedStatement
import io.github.sporadiclemon.statementparser.ParsedTransaction
import io.github.sporadiclemon.statementparser.PdfBankStatementParser
import io.github.sporadiclemon.statementparser.PdfTextExtractor
import io.github.sporadiclemon.statementparser.StatementParseResult

@Composable
fun AndroidApp() {
    val pdfParser = remember { PdfBankStatementParser() }
    val textExtractor = remember { PdfTextExtractor() }

    App(
        onParsePdf = { bytes, bankHint ->
            try {
                val text = textExtractor.extractText(bytes)
                val lines = text.split("\n")

                val bank =
                    when (bankHint?.lowercase()) {
                        "hsbc" -> Bank.HSBC
                        "natwest" -> Bank.NATWEST
                        "monzo" -> Bank.MONZO
                        "halifax" -> Bank.HALIFAX
                        "santander" -> Bank.SANTANDER
                        "starling" -> Bank.STARLING
                        else -> null
                    }

                val bankType =
                    when (bank) {
                        Bank.HSBC -> BankType.HSBC
                        Bank.NATWEST -> BankType.NATWEST
                        Bank.MONZO -> BankType.MONZO
                        Bank.HALIFAX -> BankType.HALIFAX
                        Bank.SANTANDER -> BankType.SANTANDER
                        Bank.STARLING -> BankType.STARLING
                        else -> null
                    }

                if (bankType == null) {
                    Result.failure(
                        Exception("Please select a supported bank for PDF parsing (HSBC, NatWest, Monzo, Halifax, Santander, Starling)"),
                    )
                } else {
                    when (val parseResult = pdfParser.parseDocument(lines, bankType, null)) {
                        is StatementParseResult.Success -> {
                            val transactions =
                                parseResult.transactions.map {
                                    ParsedTransaction(
                                        date = it.date,
                                        amount = it.amount.toDouble(),
                                        description = it.description,
                                        raw = "",
                                    )
                                }
                            Result.success(
                                ParsedStatement(
                                    transactions = transactions,
                                    accountInfoResult =
                                        AccountInfoResult.NotAvailable(
                                            AccountInfoUnavailableReason.MissingFromFile,
                                        ),
                                    detectedBank = bank,
                                    suggestedMapping = null,
                                    rawHeaders = null,
                                ),
                            )
                        }

                        is StatementParseResult.NoTransactionsFound -> {
                            Result.failure(Exception("No transactions found in PDF"))
                        }

                        is StatementParseResult.InvalidStatement -> {
                            Result.failure(Exception("Invalid statement: ${parseResult.reason}"))
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        },
    )
}
