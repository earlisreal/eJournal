package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebullCsvParserTest {

    private val parser = WebullCsvParser()
    private val portfolioId = 8L

    private val header = "Name,Symbol,Side,Status,Filled,Total Qty,Price,Avg Price,Time-in-Force,Placed Time,Filled Time"
    private val buy = "Apple Inc,AAPL,Buy,Filled,10,10,175.00,175.00,DAY,12/23/2021 09:53:38 EST,12/23/2021 09:53:38 EST"
    private val sell = "Apple Inc,AAPL,Sell,Filled,10,10,180.00,180.20,DAY,12/24/2021 10:15:00 EST,12/24/2021 10:15:00 EST"
    private val cancelled = "Tesla Inc,TSLA,Buy,Cancelled,0,10,200.00,,DAY,12/23/2021 10:00:00 EST,"

    private fun csv(vararg rows: String): ByteArray =
        (header + "\n" + rows.joinToString("\n")).encodeToByteArray()

    @Test
    fun detectsItsOwnHeader() {
        assertTrue(parser.detect(csv(buy)))
    }

    @Test
    fun rejectsMoomooHeader() {
        // moomoo's "Filled@Avg Price" contains "Avg Price" but lacks "Filled Time" — must not match Webull.
        val moomoo = "\"Side\",\"Symbol\",\"Name\",\"Order Price\",\"Order Qty\",\"Order Amount\",\"Status\",\"Filled@Avg Price\",\"Order Time\""
        assertFalse(parser.detect(moomoo.encodeToByteArray()))
    }

    @Test
    fun parsesFilledOrdersUsingFilledQtyAvgPriceAndIntradayTime() {
        val result = parser.parse(csv(buy, sell), portfolioId)
        assertEquals(2, result.transactions.size)
        val b = result.transactions.first { it.action == Action.BUY }
        assertEquals("AAPL", b.symbol)
        assertEquals(10.0, b.shares)
        assertEquals(175.0, b.price)
        assertEquals(0.0, b.fees)
        assertEquals(LocalDateTime.parse("2021-12-23T09:53:38"), b.datetime)
        val s = result.transactions.first { it.action == Action.SELL }
        assertEquals(180.20, s.price) // Avg Price, not the limit Price
    }

    @Test
    fun skipsCancelledOrders() {
        val result = parser.parse(csv(buy, cancelled), portfolioId)
        assertEquals(1, result.transactions.size)
        assertEquals(1, result.skipped.nonTrade)
    }

    @Test
    fun buildsIntradayExternalId() {
        val tx = parser.parse(csv(buy), portfolioId).transactions.single()
        assertEquals("webull:AAPL:2021-12-23T09:53:38:BUY:10.0#0", tx.externalId)
    }
}
