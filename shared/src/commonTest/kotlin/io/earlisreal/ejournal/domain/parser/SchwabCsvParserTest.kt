package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchwabCsvParserTest {

    private val parser = SchwabCsvParser()
    private val portfolioId = 5L

    private val title = "\"Transactions  for account ...000 as of 09/27/2022 02:03:53 AM ET\""
    private val header = "\"Date\",\"Action\",\"Symbol\",\"Description\",\"Quantity\",\"Price\",\"Fees & Comm\",\"Amount\""
    private val sell = "\"05/06/2025\",\"Sell\",\"BNDX\",\"VANGUARD TOTAL INTERNATIONAL BND ETF\",\"8\",\"\$247.37\",\"\$0.06\",\"\$1978.90\""
    private val buy = "\"02/09/2021\",\"Buy\",\"BNDX\",\"VANGUARD TOTAL INTERNATIONAL BND ETF\",\"81\",\"\$57.9999\",\"\",\"-\$4697.99\""
    private val dividend = "\"03/04/2021\",\"Cash Dividend\",\"BNDX\",\"VANGUARD TOTAL INTERNATIONAL BND ETF\",\"\",\"\",\"\",\"\$3.65\""
    private val wire = "\"01/01/2024\",\"Wire Sent\",\"\",\"WIRED FUNDS DISBURSED\",\"\",\"\",\"\",\"-\$100.00\""
    private val optionLeg = "\"06/01/2025\",\"Sell to Open\",\"AAPL 06/20/2025 200.00 C\",\"CALL AAPL\",\"1\",\"\$3.50\",\"\$0.66\",\"\$349.34\""
    private val footer = "\"Transactions Total\",\"\",\"\",\"\",\"\",\"\",\"\",\"\$94.33\","

    private fun csv(vararg rows: String): ByteArray =
        (title + "\n" + header + "\n" + rows.joinToString("\n")).encodeToByteArray()

    @Test
    fun detectsItsOwnHeaderPastTheTitlePreamble() {
        assertTrue(parser.detect(csv(buy)))
    }

    @Test
    fun rejectsRobinhoodHeader() {
        val rh = "Activity Date,Process Date,Settle Date,Instrument,Description,Trans Code,Quantity,Price,Amount"
        assertFalse(parser.detect(rh.encodeToByteArray()))
    }

    @Test
    fun parsesBuyAndSellWithMoneyCleaning() {
        val result = parser.parse(csv(buy, sell), portfolioId)
        assertEquals(2, result.transactions.size)
        val b = result.transactions.first { it.action == Action.BUY }
        assertEquals("BNDX", b.symbol)
        assertEquals(81.0, b.shares)
        assertEquals(57.9999, b.price)
        assertEquals(0.0, b.fees) // blank Fees & Comm
        assertEquals(LocalDateTime.parse("2021-02-09T00:00:00"), b.datetime)
        val s = result.transactions.first { it.action == Action.SELL }
        assertEquals(8.0, s.shares)
        assertEquals(247.37, s.price)
        assertEquals(0.06, s.fees)
    }

    @Test
    fun skipsNonTradeRowsAndFooter() {
        val result = parser.parse(csv(buy, dividend, wire, footer), portfolioId)
        assertEquals(1, result.transactions.size)
        assertEquals(2, result.skipped.nonTrade) // dividend + wire (footer not counted)
    }

    @Test
    fun skipsOptionLegs() {
        val result = parser.parse(csv(buy, optionLeg), portfolioId)
        assertEquals(1, result.transactions.size)
        assertEquals(1, result.skipped.options)
    }

    @Test
    fun buildsExternalId() {
        val tx = parser.parse(csv(sell), portfolioId).transactions.single()
        assertEquals("schwab:BNDX:2025-05-06T00:00:00:SELL:8.0#0", tx.externalId)
    }
}
