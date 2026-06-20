package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EquityCurveTest {

    private fun pos(pnl: Double, exit: String) = ClosedPosition(
        symbol = "X",
        entryDatetime = LocalDateTime.parse(exit),
        exitDatetime = LocalDateTime.parse(exit),
        averageEntryPrice = 10.0, averageExitPrice = 10.0,
        shares = 100.0, fees = 0.0, profitLoss = pnl,
    )

    @Test
    fun emptyPositionsYieldEmptyCurve() {
        assertTrue(equityCurve(emptyList()).isEmpty())
    }

    @Test
    fun cumulativeRunningSumOrderedByExitRegardlessOfInputOrder() {
        val curve = equityCurve(
            listOf(
                pos(50.0, "2024-03-03T10:00"),
                pos(-20.0, "2024-03-01T10:00"),
                pos(30.0, "2024-03-02T10:00"),
            )
        )
        assertEquals(3, curve.size)
        assertEquals(listOf(-20.0, 10.0, 60.0), curve.map { it.cumulative })
        assertEquals(LocalDateTime.parse("2024-03-01T10:00"), curve.first().datetime)
        assertEquals(LocalDateTime.parse("2024-03-03T10:00"), curve.last().datetime)
    }
}
