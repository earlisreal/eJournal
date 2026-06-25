package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TastytradeCsvParserTest {

    private val parser = TastytradeCsvParser()
    private val portfolioId = 12L

    private val header = "Date,Type,Sub Type,Action,Symbol,Instrument Type,Description,Value,Quantity,Average Price,Commissions,Fees,Multiplier,Root Symbol,Underlying Symbol,Expiration Date,Strike Price,Call or Put,Order #,Total,Currency"
    private val buy = "2026-05-20T07:53:38+1200,Trade,Buy to Open,BUY_TO_OPEN,JOBY,Equity,Bought 100 JOBY @ 15.33,1533.00,100,15.33,0.00,-0.13,,,,,,,468958586,1532.88,USD"
    private val sell = "2026-05-20T07:53:04+1200,Trade,Sell to Close,SELL_TO_CLOSE,ACHR,Equity,Sold 1 ACHR @ 5.93,5.93,1,5.93,0.00,-0.01,,,,,,,468969270,5.92,USD"
    private val option = "2026-05-21T03:15:45+1200,Trade,Sell to Open,SELL_TO_OPEN,SMR   260529C00012000,Equity Option,Sold 1 SMR 05/29/26 Call 12.00 @ 0.22,22.00,1,22.00,-1.00,-0.13,100,SMR,SMR,5/30/26,12,CALL,469145778,20.87,USD"
    private val interest = "2026-05-19T09:00:00+1200,Money Movement,Debit Interest,,,,FROM 04/16 THRU 05/15 @11    %,-12.07,0,,--,0.00,,,,,,,,-12.07,USD"

    private fun csv(vararg rows: String): ByteArray =
        (header + "\n" + rows.joinToString("\n")).encodeToByteArray()

    @Test
    fun detectsItsHeader() {
        assertTrue(parser.detect(csv(buy)))
    }

    @Test
    fun rejectsEtradeHeader() {
        val etrade = "TransactionDate,TransactionType,SecurityType,Symbol,Quantity,Amount,Price,Commission,Description"
        assertFalse(parser.detect(etrade.encodeToByteArray()))
    }

    @Test
    fun parsesEquityBuyAndSellViaActionNotSign() {
        val r = parser.parse(csv(buy, sell), portfolioId)
        assertEquals(2, r.transactions.size)
        val b = r.transactions.first { it.action == Action.BUY }
        assertEquals("JOBY", b.symbol)
        assertEquals(100.0, b.shares)
        assertEquals(15.33, b.price)
        assertEquals(0.13, b.fees, absoluteTolerance = 1e-9)
        assertEquals(LocalDateTime.parse("2026-05-20T07:53:38"), b.datetime) // +1200 offset dropped
        val s = r.transactions.first { it.action == Action.SELL }
        assertEquals("ACHR", s.symbol)
        assertEquals(1.0, s.shares)
        assertEquals(5.93, s.price)
    }

    @Test
    fun skipsOptionsAndMoneyMovement() {
        val r = parser.parse(csv(buy, option, interest), portfolioId)
        assertEquals(1, r.transactions.size)
        assertEquals(1, r.skipped.options)  // Equity Option
        assertEquals(1, r.skipped.nonTrade) // Money Movement
    }

    @Test
    fun buildsExternalIdWithOffsetDropped() {
        val tx = parser.parse(csv(buy), portfolioId).transactions.single()
        assertEquals("tasty:JOBY:2026-05-20T07:53:38:BUY:100.0#0", tx.externalId)
    }
}
