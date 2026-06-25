package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-logic tests for the eToro Account Activity semantics, exercised with plain row data so they
 * need no XLSX bytes. The XLSX reading layer is covered separately in jvmTest.
 */
class EtoroStatementTest {

    private val portfolioId = 7L

    // eToro AUS "Account Activity" sheet header, verbatim.
    private val header = listOf(
        "Date", "Type", "Details", "Amount", "Units / Contracts",
        "Realized Equity Change", "Realized Equity", "Balance", "Position ID", "Asset type", "NWA",
    )

    private fun row(date: String, type: String, details: String, amount: String, units: String) =
        listOf(date, type, details, amount, units, "0", "0", "0", "123", "Stocks", "0")

    // --- date parsing: eToro AUS uses DD/MM/YYYY ---

    @Test
    fun parsesDayFirstDateTime() {
        assertEquals(LocalDateTime.parse("2021-02-12T02:44:21"), parseEtoroDateTime("12/02/2021 02:44:21"))
    }

    @Test
    fun parsesDayGreaterThanTwelveProvingDayFirst() {
        // 15 can only be a day, so this pins the format as DD/MM, not MM/DD.
        assertEquals(LocalDateTime.parse("2021-03-15T09:40:20"), parseEtoroDateTime("15/03/2021 09:40:20"))
    }

    @Test
    fun rejectsImpossibleDate() {
        assertNull(parseEtoroDateTime("32/01/2021 00:00:00"))
        assertNull(parseEtoroDateTime("01/13/2021 00:00:00")) // month 13
        assertNull(parseEtoroDateTime("not a date"))
    }

    // --- detection ---

    @Test
    fun detectsByEtoroSheetNames() {
        assertTrue(isEtoroStatement(listOf("Account Summary", "Closed Positions", "Account Activity", "Dividends")))
    }

    @Test
    fun detectIsCaseInsensitive() {
        assertTrue(isEtoroStatement(listOf("account activity", "CLOSED POSITIONS")))
    }

    @Test
    fun rejectsNonEtoroWorkbook() {
        assertFalse(isEtoroStatement(listOf("Sheet1", "Trades")))
        assertFalse(isEtoroStatement(listOf("Account Activity"))) // needs both signature sheets
    }

    // --- Open Position -> BUY, Position closed -> SELL ---

    @Test
    fun openPositionBecomesBuyWithPriceFromAmountOverUnits() {
        val rows = listOf(header, row("25/03/2021 16:41:08", "Open Position", "AAPL/USD", "1000.00", "5"))
        val tx = parseEtoroActivity(rows, portfolioId).transactions.single()
        assertEquals(Action.BUY, tx.action)
        assertEquals("AAPL", tx.symbol)
        assertEquals(200.0, tx.price) // 1000 / 5
        assertEquals(5.0, tx.shares)
        assertEquals(0.0, tx.fees)
        assertEquals(LocalDateTime.parse("2021-03-25T16:41:08"), tx.datetime)
        assertEquals(portfolioId, tx.portfolioId)
    }

    @Test
    fun positionClosedBecomesSell() {
        val rows = listOf(header, row("22/04/2021 17:19:58", "Position closed", "AAPL/USD", "1100.00", "5"))
        val tx = parseEtoroActivity(rows, portfolioId).transactions.single()
        assertEquals(Action.SELL, tx.action)
        assertEquals(220.0, tx.price) // 1100 / 5
        assertEquals(5.0, tx.shares)
    }

    // --- symbol normalization: strip the /USD quote suffix ---

    @Test
    fun stripsQuoteSuffixAndStraySpace() {
        val rows = listOf(header, row("15/03/2021 09:40:21", "Position closed", "ETC /USD", "50", "2"))
        assertEquals("ETC", parseEtoroActivity(rows, portfolioId).transactions.single().symbol)
    }

    @Test
    fun keepsExchangeSuffixInSymbol() {
        val rows = listOf(header, row("05/04/2021 13:30:47", "Open Position", "SNX.US/USD", "2500", "20"))
        assertEquals("SNX.US", parseEtoroActivity(rows, portfolioId).transactions.single().symbol)
    }

    // --- non-trade rows are skipped and tallied, never emitted ---

    @Test
    fun skipsNonTradeLedgerRows() {
        val rows = listOf(
            header,
            row("12/02/2021 02:44:21", "Deposit", "50000.00 USDPHP OnlineBanking", "1034.02", "-"),
            row("30/04/2021 04:04:23", "Overnight fee", "SNX.US/USD", "1.47", "-"),
            row("12/02/2021 02:46:18", "Start Copy", "CryptoPortfolio", "0", "-"),
            row("23/04/2021 13:24:14", "Adjustment", "-", "27.21", "-"),
            row("20/06/2021 12:36:10", "Withdraw Request", "-", "-6995", "-"),
            row("25/03/2021 16:41:08", "Open Position", "AAPL/USD", "1000.00", "5"),
        )
        val result = parseEtoroActivity(rows, portfolioId)
        assertEquals(1, result.transactions.size) // only the Open Position
        assertEquals(5, result.skipped.nonTrade)
        assertEquals(0, result.skipped.unparsed)
    }

    // --- dedup key ---

    @Test
    fun buildsExternalIdWithEtoroPrefix() {
        val rows = listOf(header, row("25/03/2021 16:41:08", "Open Position", "AAPL/USD", "1000.00", "5"))
        val tx = parseEtoroActivity(rows, portfolioId).transactions.single()
        assertEquals("etoro:AAPL:2021-03-25T16:41:08:BUY:5.0#0", tx.externalId)
    }

    // --- partial closes: each close is its own SELL, order preserved ---

    @Test
    fun emitsEachLotSeparatelyInOrder() {
        val rows = listOf(
            header,
            row("05/04/2021 13:30:47", "Open Position", "LOW/USD", "2500", "10"),
            row("13/04/2021 16:40:52", "Position closed", "LOW/USD", "1241.20", "5"),
            row("29/04/2021 13:30:10", "Position closed", "LOW/USD", "1296.82", "5"),
        )
        val txns = parseEtoroActivity(rows, portfolioId).transactions
        assertEquals(3, txns.size)
        assertEquals(listOf(Action.BUY, Action.SELL, Action.SELL), txns.map { it.action })
        assertEquals(listOf(10.0, 5.0, 5.0), txns.map { it.shares })
    }

    // --- malformed trade rows are counted as unparsed, not silently dropped ---

    @Test
    fun countsZeroUnitTradeAsUnparsed() {
        val rows = listOf(header, row("25/03/2021 16:41:08", "Open Position", "AAPL/USD", "1000.00", "0"))
        val result = parseEtoroActivity(rows, portfolioId)
        assertEquals(0, result.transactions.size)
        assertEquals(1, result.skipped.unparsed)
    }
}
