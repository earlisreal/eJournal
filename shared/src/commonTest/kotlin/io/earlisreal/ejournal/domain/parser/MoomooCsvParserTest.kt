package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoomooCsvParserTest {

    private val parser = MoomooCsvParser()
    private val portfolioId = 7L

    private val header =
        """"Side","Symbol","Name","Order Price","Order Qty","Order Amount","Status","Filled@Avg Price","Order Time","Order Type","Time-in-Force","Allow Pre-Market","Session","Trigger price","Position Opening","Markets","Currency","Order Source","Fill Qty","Fill Price","Fill Amount","Fill Time","Markets","Currency","Counterparty","Remarks","Platform Fees","Settlement Fees","Consolidated Audit Trail Fees","SEC Fees","Trading Activity Fees","Total""""

    // A filled simple buy (one fill).
    private val suneBuy =
        """"Buy","SUNE","SUNation Energy","2.47","172","424.84","Filled","172@2.47","Jun 8, 2026 06:51:04 ET","Limit","Day","","RTH + Pre/Post-Mkt","","","US","USD","","172","2.47","424.84","Jun 8, 2026 06:51:05 ET","US","USD","","","0.99","0.52","0","","","1.51""""

    // A filled buy whose 183 shares filled across two executions; the second fill is a continuation row.
    private val skyqBuy =
        """"Buy","SKYQ","Sky Quarry","2.43","183","444.69","Filled","183@2.36454","Jun 8, 2026 04:57:06 ET","Limit","Day","","RTH + Pre/Post-Mkt","","","US","USD","","83","2.37","196.71","Jun 8, 2026 04:57:06 ET","US","USD","","","0.99","0.55","0","","","1.54""""
    private val skyqBuyContinuation =
        """"","","","","","","","","","","","","","","","","","","100","2.36","236.00","Jun 8, 2026 04:57:06 ET","US","USD","","","","","","","",""""

    // Partially Cancelled but 1 share actually filled — must be imported.
    private val chaiSellPartial =
        """"Sell","CHAI","Core AI","4.21","47","197.87","Partially Cancelled","1@4.21","Jun 9, 2026 04:23:34 ET","Limit","Day","","RTH + Pre/Post-Mkt","","","US","USD","","1","4.21","4.21","Jun 9, 2026 04:23:39 ET","US","USD","","","0.99","0","0","0.01","0.01","1.01""""

    private val lxehFailed =
        """"Buy","LXEH","Lixiang Education","3.06","139","425.34","Failed","0@0.00","Jun 8, 2026 06:20:23 ET","Limit","Day","","RTH + Pre/Post-Mkt","","","US","USD","","","","","","","","","","","","","","",""""
    private val skyqSellCancelled =
        """"Sell","SKYQ","Sky Quarry","2.32","183","424.56","Cancelled","0@0.00","Jun 8, 2026 04:59:05 ET","Limit","Day","","RTH + Pre/Post-Mkt","","","US","USD","","","","","","","","","","","","","","",""""

    // Quantity carries a thousands separator inside the quotes.
    private val devsSell =
        """"Sell","DEVS","DevvStream","0.5589","1,127","629.88","Filled","1,127@0.5589","Jun 5, 2026 07:27:53 ET","Limit","Day","","RTH + Pre/Post-Mkt","","","US","USD","","1,127","0.5589","629.88","Jun 5, 2026 07:27:53 ET","US","USD","","","0.99","3.38","0","0.01","0.22","4.6""""

    private fun csv(vararg rows: String): ByteArray =
        (header + "\n" + rows.joinToString("\n")).encodeToByteArray()

    @Test
    fun detectsItsOwnHeader() {
        assertTrue(parser.detect(csv(suneBuy)))
    }

    @Test
    fun rejectsTradeZeroHeader() {
        val tz = "Account,T/D,S/D,Currency,Type,Side,Symbol,Qty,Price,Exec Time,Comm,SEC,TAF,NSCC,Nasdaq,ECN Remove,ECN Add,Gross Proceeds,Net Proceeds,Clr Broker,Liq,Note"
        assertFalse(parser.detect(tz.encodeToByteArray()))
    }

    @Test
    fun parsesFilledBuyOrder() {
        val tx = parser.parse(csv(suneBuy), portfolioId).single()
        assertEquals("SUNE", tx.symbol)
        assertEquals(Action.BUY, tx.action)
        assertEquals(2.47, tx.price)
        assertEquals(172.0, tx.shares)
        assertEquals(1.51, tx.fees)
        assertEquals(LocalDateTime.parse("2026-06-08T06:51:05"), tx.datetime)
        assertEquals(portfolioId, tx.portfolioId)
    }

    @Test
    fun usesWholeOrderQuantityAndAvgPriceForPartialFills() {
        val result = parser.parse(csv(skyqBuy, skyqBuyContinuation), portfolioId)
        val tx = result.single { it.symbol == "SKYQ" }
        assertEquals(183.0, tx.shares)        // not 83 from the first fill
        assertEquals(2.36454, tx.price)       // order avg, not a single fill price
        assertEquals(1.54, tx.fees)           // whole-order Total
        assertEquals(1, result.size)          // continuation row produced no extra txn
    }

    @Test
    fun importsPartiallyCancelledOrderWithFill() {
        val tx = parser.parse(csv(chaiSellPartial), portfolioId).single()
        assertEquals("CHAI", tx.symbol)
        assertEquals(Action.SELL, tx.action)
        assertEquals(1.0, tx.shares)
        assertEquals(4.21, tx.price)
        assertEquals(1.01, tx.fees)
        assertEquals(LocalDateTime.parse("2026-06-09T04:23:39"), tx.datetime)
    }

    @Test
    fun skipsCancelledAndFailedOrders() {
        assertTrue(parser.parse(csv(lxehFailed, skyqSellCancelled), portfolioId).isEmpty())
    }

    @Test
    fun parsesThousandsSeparatorInQuantity() {
        val tx = parser.parse(csv(devsSell), portfolioId).single()
        assertEquals(1127.0, tx.shares)
        assertEquals(0.5589, tx.price)
        assertEquals(4.6, tx.fees)
    }

    @Test
    fun buildsDeterministicExternalIdFromOrderTime() {
        val tx = parser.parse(csv(suneBuy), portfolioId).single()
        assertEquals("moomoo:SUNE:2026-06-08T06:51:04:BUY:172.0", tx.externalId)
    }
}
