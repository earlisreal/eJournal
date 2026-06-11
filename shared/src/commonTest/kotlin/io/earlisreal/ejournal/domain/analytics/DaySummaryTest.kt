package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DaySummaryTest {

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
        val summaries = dailySummaries(
            listOf(pos("2024-03-11T10:00", 100.0), pos("2024-03-11T14:00", -40.0))
        )
        val day = summaries[LocalDate(2024, 3, 11)]!!
        assertEquals(60.0, day.netPnl)
        assertEquals(2, day.tradeCount)
    }

    @Test
    fun distinctDaysAreSeparateEntries() {
        val summaries = dailySummaries(
            listOf(pos("2024-03-11T10:00", 100.0), pos("2024-03-12T10:00", 50.0))
        )
        assertEquals(2, summaries.size)
        assertEquals(100.0, summaries[LocalDate(2024, 3, 11)]!!.netPnl)
        assertEquals(50.0, summaries[LocalDate(2024, 3, 12)]!!.netPnl)
    }

    @Test
    fun zeroPnlTradeStillCounts() {
        val day = dailySummaries(listOf(pos("2024-03-11T10:00", 0.0)))[LocalDate(2024, 3, 11)]!!
        assertEquals(0.0, day.netPnl)
        assertEquals(1, day.tradeCount)
    }
}
