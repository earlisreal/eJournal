package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TradeZeroCsvParserTest {

    private val parser = TradeZeroCsvParser()
    private val portfolioId = 3L

    private val header =
        "Account,T/D,S/D,Currency,Type,Side,Symbol,Qty,Price,Exec Time,Comm,SEC,TAF,NSCC,Nasdaq,ECN Remove,ECN Add,Gross Proceeds,Net Proceeds,Clr Broker,Liq,Note"

    private val seBuy =
        "ESA06710,06/24/2021,06/28/2021,USD,2,B,SE,2,294.248,10:29:39,0.99,0,0,0.033,3.14E-05,0,0,-588.496,-589.5190314,LAMP,,"
    private val seSell =
        "ESA06710,06/28/2021,06/30/2021,USD,2,S,SE,2,285.41,10:37:56,0.99,0.01,0.01,0.033,3.14E-05,0,0,570.82,569.7769686,LAMP,,"

    private fun csv(vararg rows: String): ByteArray =
        (header + "\n" + rows.joinToString("\n")).encodeToByteArray()

    @Test
    fun detectsItsOwnHeader() {
        assertTrue(parser.detect(csv(seBuy)))
    }

    @Test
    fun rejectsMoomooHeader() {
        val moomoo = """"Side","Symbol","Name","Order Price","Order Qty","Order Amount","Status","Filled@Avg Price""""
        assertFalse(parser.detect(moomoo.encodeToByteArray()))
    }

    @Test
    fun parsesBuyExecution() {
        val tx = parser.parse(csv(seBuy), portfolioId).single()
        assertEquals("SE", tx.symbol)
        assertEquals(Action.BUY, tx.action)
        assertEquals(2.0, tx.shares)
        assertEquals(294.248, tx.price)
        assertEquals(LocalDateTime.parse("2021-06-24T10:29:39"), tx.datetime)
        assertEquals(portfolioId, tx.portfolioId)
    }

    @Test
    fun mapsSellSide() {
        val tx = parser.parse(csv(seSell), portfolioId).single()
        assertEquals(Action.SELL, tx.action)
    }

    @Test
    fun sumsAllFeeColumnsIncludingScientificNotation() {
        // Comm 0.99 + SEC 0 + TAF 0 + NSCC 0.033 + Nasdaq 3.14E-05 + ECN 0 + 0
        val tx = parser.parse(csv(seBuy), portfolioId).single()
        assertEquals(1.0230314, tx.fees, absoluteTolerance = 1e-9)
    }

    @Test
    fun buildsUnifiedNaturalKeyExternalId() {
        // Same scheme as the API sync (no "tzcsv:" prefix, price excluded) so the two sources dedup.
        val tx = parser.parse(csv(seBuy), portfolioId).single()
        assertEquals("tz:SE:2021-06-24T10:29:39:BUY:2.0#0", tx.externalId)
    }

    @Test
    fun givesDistinctIdsToIdenticalSameSecondFills() {
        // Two byte-identical executions (same symbol/side/qty/second) must not collide,
        // or INSERT OR IGNORE would silently drop the second fill.
        val result = parser.parse(csv(seBuy, seBuy), portfolioId)
        assertEquals(2, result.size)
        assertEquals(2, result.map { it.externalId }.toSet().size)
        assertEquals(
            listOf("tz:SE:2021-06-24T10:29:39:BUY:2.0#0", "tz:SE:2021-06-24T10:29:39:BUY:2.0#1"),
            result.map { it.externalId },
        )
    }

    @Test
    fun parsesThousandsSeparatorInQuotedQuantity() {
        val row =
            "ESA06710,06/24/2021,06/28/2021,USD,2,B,SE,\"1,500\",294.248,10:29:39,0.99,0,0,0.033,3.14E-05,0,0,-588.496,-589.519,LAMP,,"
        val tx = parser.parse(csv(row), portfolioId).single()
        assertEquals(1500.0, tx.shares)
    }

    @Test
    fun parsesMultipleRows() {
        assertEquals(2, parser.parse(csv(seBuy, seSell), portfolioId).size)
    }
}
