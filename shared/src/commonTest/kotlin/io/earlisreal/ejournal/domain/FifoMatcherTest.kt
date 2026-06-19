package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.TradeDirection
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FifoMatcherTest {

    private var nextId = 1L

    private fun tx(
        action: Action,
        price: Double,
        shares: Double,
        fees: Double,
        datetime: String,
        symbol: String = "AAPL",
        id: Long = nextId++,
    ) = Transaction(
        id = id,
        portfolioId = 1L,
        symbol = symbol,
        datetime = LocalDateTime.parse(datetime),
        action = action,
        price = price,
        shares = shares,
        fees = fees
    )

    @Test
    fun singleBuySingleSellProducesOneClosedPosition() {
        val transactions = listOf(
            tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 20.0, datetime = "2024-01-01T09:00"),
            tx(Action.SELL, price = 15.0, shares = 100.0, fees = 25.0, datetime = "2024-01-10T09:00")
        )
        val positions = FifoMatcher.computeClosedPositions(transactions)
        assertEquals(1, positions.size)
        assertEquals(TradeDirection.LONG, positions[0].direction)
        assertEquals(10.0, positions[0].averageEntryPrice)
        assertEquals(15.0, positions[0].averageExitPrice)
        assertEquals(100.0, positions[0].shares)
        assertEquals(45.0, positions[0].fees)           // 20 + 25
        assertEquals(455.0, positions[0].profitLoss)    // (15-10)*100 - 45
    }

    @Test
    fun multipleBuysSingleSellAggregatesIntoOneTrade() {
        // Scale in across two lots, then close fully in one sell -> ONE round-trip trade.
        val transactions = listOf(
            tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 20.0, datetime = "2024-01-01T09:00"),
            tx(Action.BUY,  price = 12.0, shares = 50.0,  fees = 10.0, datetime = "2024-01-02T09:00"),
            tx(Action.SELL, price = 15.0, shares = 150.0, fees = 30.0, datetime = "2024-01-10T09:00")
        )
        val positions = FifoMatcher.computeClosedPositions(transactions)
        assertEquals(1, positions.size)
        assertEquals(TradeDirection.LONG, positions[0].direction)
        assertEquals(150.0, positions[0].shares)
        assertEquals(1600.0 / 150.0, positions[0].averageEntryPrice, 1e-9)  // (100*10 + 50*12) / 150
        assertEquals(15.0, positions[0].averageExitPrice)
        assertEquals(60.0, positions[0].fees)            // 20 + 10 + 30
        assertEquals(590.0, positions[0].profitLoss)     // 150*15 - 1600 - 60
        assertEquals(3, positions[0].transactions.size)
    }

    @Test
    fun oneBuyMultipleSellsIsOneTradeWithAllTranches() {
        // The reported bug: one opening buy, three exits -> ONE trade, not three.
        val transactions = listOf(
            tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 30.0, datetime = "2024-01-01T09:00"),
            tx(Action.SELL, price = 15.0, shares = 50.0,  fees = 10.0, datetime = "2024-01-10T09:00"),
            tx(Action.SELL, price = 20.0, shares = 50.0,  fees = 10.0, datetime = "2024-01-11T09:00")
        )
        val positions = FifoMatcher.computeClosedPositions(transactions)
        assertEquals(1, positions.size)
        assertEquals(100.0, positions[0].shares)
        assertEquals(10.0, positions[0].averageEntryPrice)
        assertEquals(17.5, positions[0].averageExitPrice)   // (50*15 + 50*20) / 100
        assertEquals(50.0, positions[0].fees)               // 30 + 10 + 10
        assertEquals(700.0, positions[0].profitLoss)        // 1750 - 1000 - 50
        assertEquals(3, positions[0].transactions.size)     // all tranches available to the chart
    }

    @Test
    fun scaleInAndScaleOutIsOneTradeUntilFlat() {
        // Net only returns to flat at the very end -> a single trade spanning all four orders.
        val transactions = listOf(
            tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 0.0, datetime = "2024-01-01T09:00"),
            tx(Action.SELL, price = 12.0, shares = 50.0,  fees = 0.0, datetime = "2024-01-02T09:00"),
            tx(Action.BUY,  price = 11.0, shares = 50.0,  fees = 0.0, datetime = "2024-01-03T09:00"),
            tx(Action.SELL, price = 13.0, shares = 100.0, fees = 0.0, datetime = "2024-01-04T09:00")
        )
        val positions = FifoMatcher.computeClosedPositions(transactions)
        assertEquals(1, positions.size)
        assertEquals(150.0, positions[0].shares)
        assertEquals(1550.0 / 150.0, positions[0].averageEntryPrice, 1e-9)  // (50*10 + 50*10 + 50*11)/150
        assertEquals(1900.0 / 150.0, positions[0].averageExitPrice, 1e-9)   // (50*12 + 50*13 + 50*13)/150
        assertEquals(350.0, positions[0].profitLoss, 1e-9)                  // 1900 - 1550
        assertEquals(4, positions[0].transactions.size)
    }

    @Test
    fun shortRoundTripComputesDirectionAwarePnl() {
        // Sell-to-open then buy-to-cover -> one SHORT trade; profit when covered lower.
        val transactions = listOf(
            tx(Action.SELL, price = 20.0, shares = 100.0, fees = 10.0, datetime = "2024-01-01T09:00"),
            tx(Action.BUY,  price = 18.0, shares = 100.0, fees = 10.0, datetime = "2024-01-05T09:00")
        )
        val positions = FifoMatcher.computeClosedPositions(transactions)
        assertEquals(1, positions.size)
        assertEquals(TradeDirection.SHORT, positions[0].direction)
        assertEquals(20.0, positions[0].averageEntryPrice)   // open = sell
        assertEquals(18.0, positions[0].averageExitPrice)    // close = buy
        assertEquals(100.0, positions[0].shares)
        assertEquals(20.0, positions[0].fees)
        assertEquals(180.0, positions[0].profitLoss)         // (20-18)*100 - 20
        assertEquals(LocalDateTime.parse("2024-01-01T09:00"), positions[0].entryDatetime)
        assertEquals(LocalDateTime.parse("2024-01-05T09:00"), positions[0].exitDatetime)
    }

    @Test
    fun positionFlipProducesLongThenShortTrade() {
        // Long 100, then an oversell of 150: closes the long and opens a 50-share short.
        val buy   = tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 0.0, datetime = "2024-01-01T09:00", id = 1L)
        val sell  = tx(Action.SELL, price = 12.0, shares = 150.0, fees = 0.0, datetime = "2024-01-02T09:00", id = 2L)
        val cover = tx(Action.BUY,  price = 11.0, shares = 50.0,  fees = 0.0, datetime = "2024-01-03T09:00", id = 3L)
        val positions = FifoMatcher.computeClosedPositions(listOf(buy, sell, cover))
        assertEquals(2, positions.size)

        assertEquals(TradeDirection.LONG, positions[0].direction)
        assertEquals(100.0, positions[0].shares)
        assertEquals(200.0, positions[0].profitLoss)         // (12-10)*100

        assertEquals(TradeDirection.SHORT, positions[1].direction)
        assertEquals(50.0, positions[1].shares)
        assertEquals(12.0, positions[1].averageEntryPrice)   // opened by the straddling sell
        assertEquals(11.0, positions[1].averageExitPrice)
        assertEquals(50.0, positions[1].profitLoss)          // (12-11)*50

        // The straddling sell belongs to both trades.
        assertTrue(positions[0].transactions.any { it.id == 2L })
        assertTrue(positions[1].transactions.any { it.id == 2L })
    }

    @Test
    fun partiallyOpenLongTailEmitsRealizedPortion() {
        // Bought 100, sold 60, still holding 40 -> realized 60 shares shown now.
        val transactions = listOf(
            tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 20.0, datetime = "2024-01-01T09:00"),
            tx(Action.SELL, price = 15.0, shares = 60.0,  fees = 12.0, datetime = "2024-01-10T09:00")
        )
        val positions = FifoMatcher.computeClosedPositions(transactions)
        assertEquals(1, positions.size)
        assertEquals(TradeDirection.LONG, positions[0].direction)
        assertEquals(60.0, positions[0].shares)
        assertEquals(24.0, positions[0].fees)            // 20 * 60/100 (prorated entry) + 12 (exit)
        assertEquals(276.0, positions[0].profitLoss)     // (15-10)*60 - 24
    }

    @Test
    fun partiallyOpenShortTailEmitsRealizedPortion() {
        // Shorted 100, covered 60, still short 40 -> realized 60 shares shown now.
        val transactions = listOf(
            tx(Action.SELL, price = 20.0, shares = 100.0, fees = 20.0, datetime = "2024-01-01T09:00"),
            tx(Action.BUY,  price = 18.0, shares = 60.0,  fees = 12.0, datetime = "2024-01-05T09:00")
        )
        val positions = FifoMatcher.computeClosedPositions(transactions)
        assertEquals(1, positions.size)
        assertEquals(TradeDirection.SHORT, positions[0].direction)
        assertEquals(60.0, positions[0].shares)
        assertEquals(24.0, positions[0].fees)            // 20 * 60/100 (prorated open) + 12 (close)
        assertEquals(96.0, positions[0].profitLoss)      // (20-18)*60 - 24
    }

    @Test
    fun singleBuySingleSellIncludesBothTransactions() {
        val buy  = tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 20.0, datetime = "2024-01-01T09:00")
        val sell = tx(Action.SELL, price = 15.0, shares = 100.0, fees = 25.0, datetime = "2024-01-10T09:00")
        val positions = FifoMatcher.computeClosedPositions(listOf(buy, sell))
        assertEquals(1, positions.size)
        assertEquals(2, positions[0].transactions.size)
        assertEquals(Action.BUY,  positions[0].transactions[0].action)
        assertEquals(Action.SELL, positions[0].transactions[1].action)
    }

    @Test
    fun multipleSymbolsAreMatchedIndependently() {
        val transactions = listOf(
            tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 10.0, datetime = "2024-01-01T09:00", symbol = "AAPL"),
            tx(Action.BUY,  price = 50.0, shares = 20.0,  fees = 10.0, datetime = "2024-01-01T09:00", symbol = "GOOG"),
            tx(Action.SELL, price = 15.0, shares = 100.0, fees = 10.0, datetime = "2024-01-10T09:00", symbol = "AAPL"),
            tx(Action.SELL, price = 60.0, shares = 20.0,  fees = 10.0, datetime = "2024-01-10T09:00", symbol = "GOOG")
        )
        val positions = FifoMatcher.computeClosedPositions(transactions)
        assertEquals(2, positions.size)
        val symbols = positions.map { it.symbol }.toSet()
        assertEquals(setOf("AAPL", "GOOG"), symbols)
    }
}
