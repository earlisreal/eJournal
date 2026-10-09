package io.earlisreal.ejournal.domain

import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RealizedPnLByTransactionTest {

    private var nextId = 1L

    private fun tx(action: Action, price: Double, shares: Double, fees: Double, datetime: String) =
        Transaction(
            id = nextId++,
            portfolioId = 1L,
            symbol = "AAPL",
            datetime = LocalDateTime.parse(datetime),
            action = action,
            price = price,
            shares = shares,
            fees = fees,
        )

    @Test
    fun chainedReversalsAttributeOnlyMatchedSharesAndFees() {
        val positions = FifoMatcher.computeClosedPositions(
            listOf(
                tx(Action.BUY, 10.0, 100.0, 10.0, "2024-01-01T09:00"),
                tx(Action.SELL, 12.0, 150.0, 15.0, "2024-01-02T09:00"),
                tx(Action.BUY, 11.0, 40.0, 4.0, "2024-01-03T09:00"),
                tx(Action.SELL, 20.0, 10.0, 1.0, "2024-01-04T09:00"),
                tx(Action.BUY, 11.0, 50.0, 5.0, "2024-01-05T09:00"),
                tx(Action.SELL, 13.0, 30.0, 3.0, "2024-01-06T09:00"),
            )
        )

        assertEquals(listOf(180.0, 128.0, 54.0), positions.map { it.profitLoss })
        assertEquals(listOf(100.0, 60.0, 30.0), positions.map { it.shares })
        assertEquals(listOf(40.0, 20.0), positions[1].exits.map { it.shares })
        assertEquals(listOf(null, 32.0, null, 96.0), FifoMatcher.realizedPnLByTransaction(positions[1]))
        positions.forEach { position ->
            assertEquals(position.shares, position.exits.sumOf { it.shares }, 1e-9)
            assertEquals(position.profitLoss, FifoMatcher.realizedPnLByTransaction(position).filterNotNull().sum(), 1e-9)
        }
    }

    @Test
    fun openingFillRealizesNothingClosingFillRealizesNetPnl() {
        val position = FifoMatcher.computeClosedPositions(
            listOf(
                tx(Action.BUY, price = 10.0, shares = 100.0, fees = 20.0, datetime = "2024-01-01T09:00"),
                tx(Action.SELL, price = 15.0, shares = 100.0, fees = 25.0, datetime = "2024-01-10T09:00"),
            )
        ).single()

        val realized = FifoMatcher.realizedPnLByTransaction(position)

        assertEquals(2, realized.size)
        assertNull(realized[0])                       // the opening buy realizes nothing
        assertEquals(455.0, realized[1]!!, 1e-9)      // (15-10)*100 - 45 fees
    }

    @Test
    fun realizedPnlSumsToPositionProfitLossAcrossPartialExits() {
        val position = FifoMatcher.computeClosedPositions(
            listOf(
                tx(Action.BUY, price = 10.0, shares = 100.0, fees = 10.0, datetime = "2024-01-01T09:00"),
                tx(Action.SELL, price = 12.0, shares = 40.0, fees = 4.0, datetime = "2024-01-05T09:00"),
                tx(Action.SELL, price = 15.0, shares = 60.0, fees = 6.0, datetime = "2024-01-06T09:00"),
            )
        ).single()

        val realized = FifoMatcher.realizedPnLByTransaction(position)

        assertNull(realized[0])
        assertNotNull(realized[1])
        assertNotNull(realized[2])
        assertEquals(position.profitLoss, realized.filterNotNull().sum(), 1e-9)
    }

    @Test
    fun realizedPnlForShortPositionAttributesToCoveringBuy() {
        val position = FifoMatcher.computeClosedPositions(
            listOf(
                tx(Action.SELL, price = 20.0, shares = 100.0, fees = 10.0, datetime = "2024-01-01T09:00"),
                tx(Action.BUY, price = 15.0, shares = 100.0, fees = 10.0, datetime = "2024-01-05T09:00"),
            )
        ).single()

        val realized = FifoMatcher.realizedPnLByTransaction(position)

        assertNull(realized[0])                       // the opening short sell realizes nothing
        assertEquals(480.0, realized[1]!!, 1e-9)      // (20-15)*100 - 20 fees
    }
}
