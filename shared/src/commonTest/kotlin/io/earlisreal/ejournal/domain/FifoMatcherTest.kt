package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FifoMatcherTest {

    private fun tx(
        action: Action,
        price: Double,
        shares: Double,
        fees: Double,
        datetime: String,
        symbol: String = "AAPL"
    ) = Transaction(
        id = 0L,
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
        assertEquals(10.0, positions[0].averageEntryPrice)
        assertEquals(15.0, positions[0].averageExitPrice)
        assertEquals(100.0, positions[0].shares)
        assertEquals(45.0, positions[0].fees)           // 20 + 25
        assertEquals(455.0, positions[0].profitLoss)    // (15-10)*100 - 45
    }

    @Test
    fun multipleBuysSingleSellUsesFifoOrder() {
        val transactions = listOf(
            tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 20.0, datetime = "2024-01-01T09:00"),
            tx(Action.BUY,  price = 12.0, shares = 50.0,  fees = 10.0, datetime = "2024-01-02T09:00"),
            tx(Action.SELL, price = 15.0, shares = 120.0, fees = 24.0, datetime = "2024-01-10T09:00")
        )
        val positions = FifoMatcher.computeClosedPositions(transactions)
        assertEquals(2, positions.size)
        assertEquals(100.0, positions[0].shares)
        assertEquals(10.0,  positions[0].averageEntryPrice)
        assertEquals(20.0,  positions[1].shares)
        assertEquals(12.0,  positions[1].averageEntryPrice)
    }

    @Test
    fun partialSellLeavesRemainingSharesOpen() {
        val transactions = listOf(
            tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 20.0, datetime = "2024-01-01T09:00"),
            tx(Action.SELL, price = 15.0, shares = 60.0,  fees = 12.0, datetime = "2024-01-10T09:00")
        )
        val positions = FifoMatcher.computeClosedPositions(transactions)
        assertEquals(1, positions.size)
        assertEquals(60.0, positions[0].shares)
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
    fun multipleBuysShareSellTransactionAcrossPositions() {
        val buy1 = tx(Action.BUY,  price = 10.0, shares = 100.0, fees = 20.0, datetime = "2024-01-01T09:00")
        val buy2 = tx(Action.BUY,  price = 12.0, shares = 50.0,  fees = 10.0, datetime = "2024-01-02T09:00")
        val sell = tx(Action.SELL, price = 15.0, shares = 150.0, fees = 30.0, datetime = "2024-01-10T09:00")
        val positions = FifoMatcher.computeClosedPositions(listOf(buy1, buy2, sell))
        assertEquals(2, positions.size)
        assertEquals(Action.BUY,  positions[0].transactions[0].action)
        assertEquals(10.0,        positions[0].transactions[0].price)
        assertEquals(Action.SELL, positions[0].transactions[1].action)
        assertEquals(Action.BUY,  positions[1].transactions[0].action)
        assertEquals(12.0,        positions[1].transactions[0].price)
        assertEquals(Action.SELL, positions[1].transactions[1].action)
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
