package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobinhoodCsvParserTest {

    private val parser = RobinhoodCsvParser()
    private val portfolioId = 6L

    private val header = "Activity Date,Process Date,Settle Date,Instrument,Description,Trans Code,Quantity,Price,Amount"
    private val buy = "9/18/2023,9/18/2023,9/19/2023,AAPL,Apple Inc.,Buy,10,\$175.00,(\$1750.00)"
    private val sell = "9/20/2023,9/20/2023,9/21/2023,AAPL,Apple Inc.,Sell,10,\$180.00,\$1800.00"
    private val dividend = "9/30/2023,9/30/2023,9/30/2023,AAPL,Apple Inc.,CDIV,,,\$2.40"
    private val optionBto = "9/15/2023,9/15/2023,9/18/2023,AAPL,Call \$180,BTO,1,\$2.50,(\$250.00)"

    private fun csv(vararg rows: String): ByteArray =
        (header + "\n" + rows.joinToString("\n")).encodeToByteArray()

    @Test
    fun detectsItsOwnHeader() {
        assertTrue(parser.detect(csv(buy)))
    }

    @Test
    fun rejectsSchwabHeader() {
        val schwab = "\"Date\",\"Action\",\"Symbol\",\"Description\",\"Quantity\",\"Price\",\"Fees & Comm\",\"Amount\""
        assertFalse(parser.detect(schwab.encodeToByteArray()))
    }

    @Test
    fun parsesBuyAndSell() {
        val result = parser.parse(csv(buy, sell), portfolioId)
        assertEquals(2, result.transactions.size)
        val b = result.transactions.first { it.action == Action.BUY }
        assertEquals("AAPL", b.symbol)
        assertEquals(10.0, b.shares)
        assertEquals(175.0, b.price)
        assertEquals(0.0, b.fees)
        assertEquals(LocalDateTime.parse("2023-09-18T00:00:00"), b.datetime)
    }

    @Test
    fun skipsDividendsAndOptions() {
        val result = parser.parse(csv(buy, dividend, optionBto), portfolioId)
        assertEquals(1, result.transactions.size)
        assertEquals(1, result.skipped.nonTrade) // CDIV
        assertEquals(1, result.skipped.options)  // BTO
    }

    @Test
    fun buildsExternalId() {
        val tx = parser.parse(csv(sell), portfolioId).transactions.single()
        assertEquals("rh:AAPL:2023-09-20T00:00:00:SELL:10.0#0", tx.externalId)
    }
}
