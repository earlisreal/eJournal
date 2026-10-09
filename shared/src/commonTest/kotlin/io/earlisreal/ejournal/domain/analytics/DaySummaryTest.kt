package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.FifoMatcher
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DaySummaryTest {

    private fun tx(id: Long, action: Action, datetime: String, price: Double, shares: Double, fees: Double) =
        Transaction(id, 1L, "X", LocalDateTime.parse(datetime), action, price, shares, fees)

    @Test
    fun partialExitsAreAttributedToTheirExecutionDates() {
        val positions = FifoMatcher.computeClosedPositions(
            listOf(
                tx(1, Action.BUY, "2024-06-03T09:00", 10.0, 100.0, 10.0),
                tx(2, Action.SELL, "2024-06-04T10:00", 12.0, 40.0, 4.0),
                tx(3, Action.SELL, "2024-06-06T10:00", 15.0, 60.0, 6.0),
            )
        )
        val summaries = dailySummaries(calendarEntries(positions))

        assertEquals(setOf(LocalDate(2024, 6, 4), LocalDate(2024, 6, 6)), summaries.keys)
        assertEquals(DaySummary(LocalDate(2024, 6, 4), 72.0, 1), summaries[LocalDate(2024, 6, 4)])
        assertEquals(DaySummary(LocalDate(2024, 6, 6), 288.0, 1), summaries[LocalDate(2024, 6, 6)])
    }

    @Test
    fun sameDayFillsCombineButDistinctPositionsOfOneSymbolStaySeparate() {
        val positions = FifoMatcher.computeClosedPositions(
            listOf(
                tx(1, Action.BUY, "2024-06-03T09:00", 10.0, 100.0, 10.0),
                tx(2, Action.SELL, "2024-06-04T09:30", 12.0, 20.0, 2.0),
                tx(3, Action.SELL, "2024-06-04T10:30", 12.0, 20.0, 2.0),
                tx(4, Action.SELL, "2024-06-06T10:00", 15.0, 60.0, 6.0),
                tx(5, Action.BUY, "2024-06-06T11:00", 10.0, 5.0, 0.0),
                tx(6, Action.SELL, "2024-06-06T12:00", 12.0, 5.0, 0.0),
            ).reversed()
        )
        val entries = calendarEntries(positions)

        assertEquals(listOf(40.0, 60.0, 5.0), entries.map { it.shares })
        assertEquals(listOf(72.0, 288.0, 10.0), entries.map { it.profitLoss })
        assertEquals(LocalDateTime.parse("2024-06-04T09:30"), entries.first().exitDatetime)
        assertSame(positions.first(), entries.first().position)
        assertSame(positions.first(), entries[1].position)
        assertEquals(positions, calendarAnalysisPositions(entries))
        assertEquals(DaySummary(LocalDate(2024, 6, 6), 298.0, 2), dailySummaries(entries)[LocalDate(2024, 6, 6)])
    }

    @Test
    fun partiallyOpenLongAndShortIncludeOnlyRealizedFractionalShares() {
        val long = FifoMatcher.computeClosedPositions(
            listOf(
                tx(1, Action.BUY, "2024-06-03T09:00", 10.0, 1.5, 0.15),
                tx(2, Action.SELL, "2024-06-04T23:59", 12.0, 0.5, 0.05),
            )
        ).single()
        val short = FifoMatcher.computeClosedPositions(
            listOf(
                tx(3, Action.SELL, "2024-06-03T09:00", 20.0, 3.0, 0.3),
                tx(4, Action.BUY, "2024-06-05T00:01", 18.0, 1.5, 0.15),
            )
        ).single()
        val entries = calendarEntries(listOf(short, long))

        assertEquals(listOf(LocalDate(2024, 6, 4), LocalDate(2024, 6, 5)), entries.map { it.date })
        assertEquals(listOf(0.5, 1.5), entries.map { it.shares })
        assertEquals(0.9, entries[0].profitLoss, 1e-9)
        assertEquals(2.7, entries[1].profitLoss, 1e-9)
        listOf(long, short).forEach { position ->
            assertEquals(position.shares, position.exits.sumOf { it.shares }, 1e-9)
            assertEquals(position.profitLoss, position.exits.sumOf { it.profitLoss }, 1e-9)
        }
    }

    @Test
    fun zeroPnlExitStillCountsWithoutCountingItsOpeningDate() {
        val position = FifoMatcher.computeClosedPositions(
            listOf(
                tx(1, Action.BUY, "2024-06-03T09:00", 10.0, 1.0, 0.0),
                tx(2, Action.SELL, "2024-06-04T09:00", 10.0, 1.0, 0.0),
            )
        ).single()

        assertEquals(mapOf(LocalDate(2024, 6, 4) to DaySummary(LocalDate(2024, 6, 4), 0.0, 1)),
            dailySummaries(calendarEntries(listOf(position))))
        assertEquals(listOf(null, 0.0), FifoMatcher.realizedPnLByTransaction(position))
    }

    private fun pos(exit: String, pnl: Double) = ClosedPosition(
        symbol = "X",
        entryDatetime = LocalDateTime.parse("2024-03-01T09:00"),
        exitDatetime = LocalDateTime.parse(exit),
        averageEntryPrice = 10.0, averageExitPrice = 10.0,
        shares = 100.0, fees = 0.0, profitLoss = pnl,
    )

    @Test
    fun emptyInputYieldsEmptyMap() {
        assertEquals(emptyMap(), dailySummaries(emptyList()))
    }

    @Test
    fun multipleTradesSameDaySumAndCount() {
        val summaries = dailySummaries(calendarEntries(
            listOf(pos("2024-03-11T10:00", 100.0), pos("2024-03-11T14:00", -40.0))
        ))
        val day = summaries[LocalDate(2024, 3, 11)]!!
        assertEquals(60.0, day.netPnl)
        assertEquals(2, day.tradeCount)
    }

    @Test
    fun distinctDaysAreSeparateEntries() {
        val summaries = dailySummaries(calendarEntries(
            listOf(pos("2024-03-11T10:00", 100.0), pos("2024-03-12T10:00", 50.0))
        ))
        assertEquals(2, summaries.size)
        assertEquals(100.0, summaries[LocalDate(2024, 3, 11)]!!.netPnl)
        assertEquals(50.0, summaries[LocalDate(2024, 3, 12)]!!.netPnl)
    }

    @Test
    fun zeroPnlTradeStillCounts() {
        val day = dailySummaries(calendarEntries(listOf(pos("2024-03-11T10:00", 0.0))))[LocalDate(2024, 3, 11)]!!
        assertEquals(0.0, day.netPnl)
        assertEquals(1, day.tradeCount)
    }
}
