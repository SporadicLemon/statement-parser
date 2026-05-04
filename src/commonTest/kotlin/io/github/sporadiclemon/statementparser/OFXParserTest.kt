package io.github.sporadiclemon.statementparser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OFXParserTest {

    private val parser = OFXParser()

    private val validOfx = """
        <?xml version="1.0" encoding="UTF-8"?>
        <OFX>
          <BANKMSGSRSV1>
            <STMTTRNRS>
              <STMTRS>
                <BANKACCTFROM>
                  <BANKID>123456</BANKID>
                  <ACCTID>87654321</ACCTID>
                </BANKACCTFROM>
                <BANKTRANLIST>
                  <STMTTRN>
                    <TRNTYPE>DEBIT</TRNTYPE>
                    <DTPOSTED>20240115</DTPOSTED>
                    <TRNAMT>-50.00</TRNAMT>
                    <FITID>trn001</FITID>
                    <NAME>TESCO STORES</NAME>
                  </STMTTRN>
                  <STMTTRN>
                    <TRNTYPE>CREDIT</TRNTYPE>
                    <DTPOSTED>20240120120000</DTPOSTED>
                    <TRNAMT>1500.00</TRNAMT>
                    <FITID>trn002</FITID>
                    <NAME>SALARY ACME LTD</NAME>
                  </STMTTRN>
                </BANKTRANLIST>
              </STMTRS>
            </STMTTRNRS>
          </BANKMSGSRSV1>
        </OFX>
    """.trimIndent()

    private val ofxMissingAccount = """
        <OFX>
          <BANKMSGSRSV1>
            <STMTTRNRS>
              <STMTRS>
                <BANKTRANLIST>
                  <STMTTRN>
                    <DTPOSTED>20240115</DTPOSTED>
                    <TRNAMT>-10.00</TRNAMT>
                    <NAME>Coffee</NAME>
                  </STMTTRN>
                </BANKTRANLIST>
              </STMTRS>
            </STMTTRNRS>
          </BANKMSGSRSV1>
        </OFX>
    """.trimIndent()

    @Test
    fun `parses transactions from valid OFX`() {
        val result = parser.parse(validOfx).getOrThrow()
        assertEquals(2, result.transactions.size)
    }

    @Test
    fun `parses debit transaction amount as negative`() {
        val result = parser.parse(validOfx).getOrThrow()
        assertEquals(-50.00, result.transactions[0].amount)
    }

    @Test
    fun `parses credit transaction amount as positive`() {
        val result = parser.parse(validOfx).getOrThrow()
        assertEquals(1500.00, result.transactions[1].amount)
    }

    @Test
    fun `parses transaction date from 8-digit DTPOSTED`() {
        val result = parser.parse(validOfx).getOrThrow()
        assertEquals(LocalDate(2024, 1, 15), result.transactions[0].date)
    }

    @Test
    fun `parses transaction date from 14-digit DTPOSTED`() {
        val result = parser.parse(validOfx).getOrThrow()
        assertEquals(LocalDate(2024, 1, 20), result.transactions[1].date)
    }

    @Test
    fun `parses transaction description from NAME tag`() {
        val result = parser.parse(validOfx).getOrThrow()
        assertEquals("TESCO STORES", result.transactions[0].description)
    }

    @Test
    fun `returns Found account info when BANKID and ACCTID present`() {
        val result = parser.parse(validOfx).getOrThrow()
        val info = assertIs<AccountInfoResult.Found>(result.accountInfoResult)
        assertEquals("123456", info.info.institutionName)
        assertEquals("87654321", info.info.accountNumber)
    }

    @Test
    fun `returns MissingFromFile when account block absent`() {
        val result = parser.parse(ofxMissingAccount).getOrThrow()
        val notAvailable = assertIs<AccountInfoResult.NotAvailable>(result.accountInfoResult)
        assertEquals(AccountInfoUnavailableReason.MissingFromFile, notAvailable.reason)
    }

    @Test
    fun `accountInfoResult is never CsvFormat for OFX`() {
        val result = parser.parse(ofxMissingAccount).getOrThrow()
        val notAvailable = assertIs<AccountInfoResult.NotAvailable>(result.accountInfoResult)
        assertTrue(notAvailable.reason != AccountInfoUnavailableReason.CsvFormat)
    }

    @Test
    fun `returns success but 0 transactions for completely malformed content`() {
        val result = parser.parse("not xml at all %%%")
        // Should succeed but return 0 transactions (graceful degradation)
        assertEquals(0, result.getOrThrow().transactions.size)
    }
}
