package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PositionSortTest {

    private fun pos(symbol: String, exit: String, pnl: Double) = ClosedPosition(
        symbol = symbol,
        entryDatetime = LocalDateTime.parse("2024-03-01T09:00"),
        exitDatetime = LocalDateTime.parse(exit),
        averageEntryPrice = 10.0, averageExitPrice = 11.0,
        shares = 100.0, fees = 1.0, profitLoss = pnl,
    )

    private val a = pos("AAPL", "2024-03-05T10:00", 100.0)
    private val b = pos("BDO", "2024-03-11T10:00", -50.0)
    private val list = listOf(a, b)

    @Test
    fun exitDescendingIsNewestFirst() {
        assertEquals(listOf(b, a), sortPositions(list, SortColumn.EXIT, SortDirection.DESC))
    }

    @Test
    fun pnlAscendingIsWorstFirst() {
        assertEquals(listOf(b, a), sortPositions(list, SortColumn.PNL, SortDirection.ASC))
    }

    @Test
    fun symbolAscendingIsAlphabetical() {
        assertEquals(listOf(a, b), sortPositions(list, SortColumn.SYMBOL, SortDirection.ASC))
    }
}
