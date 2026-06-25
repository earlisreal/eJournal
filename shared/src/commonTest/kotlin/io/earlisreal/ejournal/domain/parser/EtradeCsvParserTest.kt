package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EtradeCsvParserTest {

    private val parser = EtradeCsvParser()
    private val portfolioId = 9L

    private val header = "TransactionDate,TransactionType,SecurityType,Symbol,Quantity,Amount,Price,Commission,Description"
    private val buy = "05/26/20,Bought,EQ,AAPL,100,-31600.00,316.00,0.00,Bought 100 AAPL"
    private val sell = "06/01/20,Sold,EQ,AAPL,-100,32000.00,320.00,0.00,Sold 100 AAPL"
    private val dividend = "06/15/20,Dividend,EQ,AAPL,0,82.00,0.00,0.00,Qualified dividend"
    private val option = "06/10/20,Bought,OPTN,AAPL--200619C00320000,1,-250.00,2.50,0.65,Bought 1 contract"

    private fun csv(vararg rows: String): ByteArray =
        (header + "\n" + rows.joinToString("\n")).encodeToByteArray()

    @Test
    fun detectsItsClassicHeader() {
        assertTrue(parser.detect(csv(buy)))
    }

    @Test
    fun rejectsWebullHeader() {
        val webull = "Name,Symbol,Side,Status,Filled,Total Qty,Price,Avg Price,Time-in-Force,Placed Time,Filled Time"
        assertFalse(parser.detect(webull.encodeToByteArray()))
    }

    @Test
    fun parsesBoughtAndSoldWithSignedQuantity() {
        val result = parser.parse(csv(buy, sell), portfolioId)
        assertEquals(2, result.transactions.size)
        val b = result.transactions.first { it.action == Action.BUY }
        assertEquals("AAPL", b.symbol)
        assertEquals(100.0, b.shares)
        assertEquals(316.0, b.price)
        assertEquals(0.0, b.fees)
        assertEquals(LocalDateTime.parse("2020-05-26T00:00:00"), b.datetime)
        val s = result.transactions.first { it.action == Action.SELL }
        assertEquals(100.0, s.shares) // abs of -100
    }

    @Test
    fun skipsDividendsAndOptionSecurityType() {
        val result = parser.parse(csv(buy, dividend, option), portfolioId)
        assertEquals(1, result.transactions.size)
        assertEquals(1, result.skipped.nonTrade) // Dividend
        assertEquals(1, result.skipped.options)  // SecurityType OPTN
    }

    @Test
    fun buildsExternalId() {
        val tx = parser.parse(csv(buy), portfolioId).transactions.single()
        assertEquals("etrade:AAPL:2020-05-26T00:00:00:BUY:100.0#0", tx.externalId)
    }
}
