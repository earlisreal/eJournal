package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TradeTypeTest {

    private fun position(entry: String, exit: String) = ClosedPosition(
        symbol = "AAPL",
        entryDatetime = LocalDateTime.parse(entry),
        exitDatetime = LocalDateTime.parse(exit),
        averageEntryPrice = 10.0, averageExitPrice = 11.0,
        shares = 100.0, fees = 1.0, profitLoss = 99.0,
    )

    @Test
    fun sameCalendarDayIsDayTrade() {
        assertEquals(TradeType.DAY, classifyTradeType(position("2024-03-11T09:18", "2024-03-11T14:30")))
    }

    @Test
    fun differentDayIsSwing() {
        assertEquals(TradeType.SWING, classifyTradeType(position("2024-03-02T10:00", "2024-03-08T10:00")))
    }

    @Test
    fun overnightByOneMinuteIsSwing() {
        assertEquals(TradeType.SWING, classifyTradeType(position("2024-03-02T23:59", "2024-03-03T00:01")))
    }
}
