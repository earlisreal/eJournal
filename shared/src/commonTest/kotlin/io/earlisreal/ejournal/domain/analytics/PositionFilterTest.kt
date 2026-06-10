package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PositionFilterTest {

    private fun pos(symbol: String, entry: String, exit: String) = ClosedPosition(
        symbol = symbol,
        entryDatetime = LocalDateTime.parse(entry),
        exitDatetime = LocalDateTime.parse(exit),
        averageEntryPrice = 10.0, averageExitPrice = 11.0,
        shares = 100.0, fees = 1.0, profitLoss = 99.0,
    )

    private val day = pos("AAPL", "2024-03-11T09:00", "2024-03-11T15:00")
    private val swing = pos("BDO", "2024-03-02T09:00", "2024-03-20T15:00")
    private val all = listOf(day, swing)

    @Test
    fun rangeFiltersByExitDateInclusive() {
        val r = DateRange(LocalDate(2024, 3, 11), LocalDate(2024, 3, 11))
        assertEquals(listOf(day), filterPositions(all, r, Segment.ALL))
    }

    @Test
    fun nullBoundsMeanUnbounded() {
        assertEquals(all, filterPositions(all, DateRange(null, null), Segment.ALL))
    }

    @Test
    fun segmentDayKeepsOnlyDayTrades() {
        assertEquals(listOf(day), filterPositions(all, DateRange(null, null), Segment.DAY))
    }

    @Test
    fun segmentSwingKeepsOnlySwings() {
        assertEquals(listOf(swing), filterPositions(all, DateRange(null, null), Segment.SWING))
    }

    @Test
    fun rangeAndSegmentCombine() {
        val r = DateRange(LocalDate(2024, 3, 1), LocalDate(2024, 3, 15))
        assertEquals(emptyList<ClosedPosition>(), filterPositions(all, r, Segment.SWING))
    }
}
