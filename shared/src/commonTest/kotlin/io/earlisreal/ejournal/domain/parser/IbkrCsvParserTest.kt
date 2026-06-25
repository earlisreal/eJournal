package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IbkrCsvParserTest {

    private val parser = IbkrCsvParser()
    private val portfolioId = 11L

    private val header = "Trades,Header,DataDiscriminator,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price,C. Price,Proceeds,Comm/Fee,Basis,Realized P/L,MTM P/L,Code"
    private val buy = "Trades,Data,Order,Stocks,USD,AAPL,\"2024-01-15, 10:30:00\",50,185.50,185.50,0,-1.25,9275.00,0,0,O"
    private val sell = "Trades,Data,Order,Stocks,USD,AAPL,\"2024-04-22, 13:20:00\",-40,192.80,192.80,7712.00,-1.50,0,520.00,0,C"
    private val subtotal = "Trades,SubTotal,,Stocks,USD,AVGO,,66,,,4099.00,-3.35,3776.00,1203.75,625.75,"
    private val optionRow = "Trades,Data,Order,Equity and Index Options,USD,AAPL 240119C00150000,\"2024-01-10, 14:30:00\",10,2.50,2.50,2500,-1.00,0,0,0,O"
    private val dividendsSection =
        "Dividends,Header,Currency,Date,Description,Amount\n" +
            "Dividends,Data,USD,2024-02-01,AAPL Cash Dividend,12.50"

    private fun csv(vararg lines: String): ByteArray = lines.joinToString("\n").encodeToByteArray()

    @Test
    fun detectsTradesHeader() {
        assertTrue(parser.detect(csv(header, buy)))
    }

    @Test
    fun rejectsFidelityHeader() {
        val fid = "Run Date,Action,Symbol,Description,Type,Quantity,Price (\$),Commission (\$),Fees (\$),Accrued Interest (\$),Amount (\$),Cash Balance (\$),Settlement Date"
        assertFalse(parser.detect(fid.encodeToByteArray()))
    }

    @Test
    fun parsesBuySellViaSignAndSkipsSubtotal() {
        val r = parser.parse(csv(header, buy, sell, subtotal), portfolioId)
        assertEquals(2, r.transactions.size)
        val b = r.transactions.first { it.action == Action.BUY }
        assertEquals("AAPL", b.symbol)
        assertEquals(50.0, b.shares)
        assertEquals(185.50, b.price)
        assertEquals(1.25, b.fees) // abs of -1.25
        assertEquals(LocalDateTime.parse("2024-01-15T10:30:00"), b.datetime)
        val s = r.transactions.first { it.action == Action.SELL }
        assertEquals(40.0, s.shares) // abs of -40
        assertEquals(1.50, s.fees)
        assertEquals(0, r.skipped.nonTrade) // SubTotal not counted
    }

    @Test
    fun skipsOptionAssetCategory() {
        val r = parser.parse(csv(header, buy, header, optionRow), portfolioId)
        assertEquals(1, r.transactions.size)
        assertEquals(1, r.skipped.options)
    }

    @Test
    fun ignoresNonTradesSections() {
        val r = parser.parse(csv(dividendsSection, header, buy), portfolioId)
        assertEquals(1, r.transactions.size)
        assertEquals(0, r.skipped.nonTrade)
        assertEquals(0, r.skipped.options)
    }

    @Test
    fun buildsIntradayExternalId() {
        val tx = parser.parse(csv(header, buy), portfolioId).transactions.single()
        assertEquals("ibkr:AAPL:2024-01-15T10:30:00:BUY:50.0#0", tx.externalId)
    }
}
