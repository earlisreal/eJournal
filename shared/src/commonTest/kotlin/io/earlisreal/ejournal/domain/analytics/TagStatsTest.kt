package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Tag
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagStatsTest {

    private val breakout = Tag(1, "Breakout", "#4CAF50")
    private val oversized = Tag(2, "Oversized", "#F44336")

    private fun pos(pnl: Double, tags: List<Tag> = emptyList()) = ClosedPosition(
        symbol = "X",
        entryDatetime = LocalDateTime.parse("2024-03-01T09:00"),
        exitDatetime = LocalDateTime.parse("2024-03-01T15:00"),
        averageEntryPrice = 10.0, averageExitPrice = 10.0,
        shares = 100.0, fees = 0.0, profitLoss = pnl,
        tags = tags,
    )

    @Test
    fun emptyPositionsYieldNoStats() {
        assertEquals(emptyList<TagStat>(), tagStats(emptyList()))
    }

    @Test
    fun groupsPositionsByTagAndComputesMetricsPerGroup() {
        val stats = tagStats(
            listOf(
                pos(100.0, listOf(breakout)),
                pos(-40.0, listOf(breakout)),
                pos(60.0, listOf(oversized)),
            )
        )
        val breakoutStat = stats.first { it.tag == breakout }
        assertEquals(2, breakoutStat.metrics.tradeCount)
        assertEquals(60.0, breakoutStat.metrics.netPnl)

        val oversizedStat = stats.first { it.tag == oversized }
        assertEquals(1, oversizedStat.metrics.tradeCount)
        assertEquals(60.0, oversizedStat.metrics.netPnl)
    }

    @Test
    fun positionWithMultipleTagsCountsTowardEachTag() {
        val stats = tagStats(listOf(pos(100.0, listOf(breakout, oversized))))
        assertEquals(100.0, stats.first { it.tag == breakout }.metrics.netPnl)
        assertEquals(1, stats.first { it.tag == breakout }.metrics.tradeCount)
        assertEquals(100.0, stats.first { it.tag == oversized }.metrics.netPnl)
    }

    @Test
    fun untaggedPositionsFormATrailingNullGroup() {
        val stats = tagStats(
            listOf(
                pos(100.0, listOf(breakout)),
                pos(-10.0),
            )
        )
        assertNull(stats.last().tag)
        assertEquals(-10.0, stats.last().metrics.netPnl)
        assertEquals(1, stats.last().metrics.tradeCount)
    }

    @Test
    fun taggedGroupsSortedByNetPnlDescendingUntaggedLast() {
        val stats = tagStats(
            listOf(
                pos(10.0, listOf(breakout)),
                pos(50.0, listOf(oversized)),
                pos(-5.0),
            )
        )
        assertEquals(listOf(oversized, breakout, null), stats.map { it.tag })
    }
}
