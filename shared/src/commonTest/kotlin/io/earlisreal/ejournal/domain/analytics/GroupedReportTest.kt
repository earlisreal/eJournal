package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.FifoMatcher
import io.earlisreal.ejournal.domain.model.*
import kotlinx.datetime.*
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.test.*

class GroupedReportTest {
    private fun position(seconds: Int = 60, instant: String = "2026-10-12T13:30:00Z", verified: Boolean = true, swing: Boolean = false): ClosedPosition {
        val start = Instant.parse(instant)
        val end = start + seconds.seconds
        val zone = marketClock(Market.US_STOCKS)
        val entry = start.toLocalDateTime(zone)
        val exit = end.toLocalDateTime(zone)
        val open = Transaction(1, 1, "AAPL", entry, Action.BUY, 100.0, 0.5, 0.1, verifiedExecutionInstant = start.takeIf { verified })
        val close = open.copy(id = 2, datetime = exit, action = Action.SELL, price = 101.0, verifiedExecutionInstant = end.takeIf { verified })
        return ClosedPosition("AAPL", entry, if (swing) LocalDateTime(2026, 11, 15, 10, 0) else exit, 100.0, 101.0, 0.5, 0.2, 0.3, listOf(open, close))
    }

    @Test fun everyEntrySlotAndMarketClockUsesNamedProjection() {
        for (slot in 0..47) {
            val hour = (slot / 2).toString().padStart(2, '0')
            val minute = if (slot % 2 == 0) "00" else "30"
            val p = position(0, "2026-10-12T$hour:$minute:00Z").copy(market = Market.CRYPTO)
            assertEquals("slot:$slot", groupedReport(listOf(p), ReportGrouping.ENTRY_TIME, Segment.ALL).single { it.positions.isNotEmpty() }.key)
        }
        val p = position(0, "2026-10-12T23:59:59Z").copy(market = Market.PH_STOCKS)
        assertEquals("slot:15", groupedReport(listOf(p), ReportGrouping.ENTRY_TIME, Segment.ALL).single { it.positions.isNotEmpty() }.key)
        assertEquals("weekday:1", groupedReport(listOf(p), ReportGrouping.ENTRY_WEEKDAY, Segment.ALL).single { it.positions.isNotEmpty() }.key)
    }

    @Test fun swingBoundariesAndUnknownsStaySeparateInAll() {
        val bounds = listOf(86400 to 1, 259200 to 2, 604800 to 3, 2592000 to 4)
        for ((seconds, expected) in bounds) {
            val p = position(seconds)
            assertEquals("swing:$expected", groupedReport(listOf(p), ReportGrouping.DURATION, Segment.ALL).single { it.positions.isNotEmpty() }.key)
        }
        val crossingMidnight = position(60, "2026-10-13T03:59:30Z")
        assertEquals("swing:0", groupedReport(listOf(crossingMidnight), ReportGrouping.DURATION, Segment.ALL).single { it.positions.isNotEmpty() }.key)
        val dayUnknown = position(verified = false)
        val swingUnknown = crossingMidnight.copy(transactions = crossingMidnight.transactions.map { it.copy(verifiedExecutionInstant = null) })
        val groups = groupedReport(listOf(dayUnknown, swingUnknown), ReportGrouping.DURATION, Segment.ALL)
        assertEquals(setOf("day:unverified", "swing:unverified"), groups.filter { it.positions.isNotEmpty() }.map { it.key }.toSet())
        val negative = position().let { it.copy(transactions = listOf(it.transactions.first().copy(verifiedExecutionInstant = Instant.parse("2026-10-12T14:30:00Z")), it.transactions.last())) }
        assertNull(negative.verifiedHoldSeconds())
    }

    @Test fun metricSortIsStableAndMissingLastEitherWay() {
        val empty = ReportBucket("empty", "Empty", emptyList())
        val first = ReportBucket("first", "First", listOf(position()))
        val second = first.copy(key = "second", label = "Second")
        for (direction in SortDirection.entries) assertEquals(listOf(first, second, empty), sortReportBuckets(listOf(empty, first, second), ReportMetric.AVERAGE, direction))
    }

    @Test fun groupsReconcileAndKeepUnverifiedHistory() {
        val positions = listOf(position(0), position(60), position(verified = false))
        for (group in ReportGrouping.entries.filter { it != ReportGrouping.TAG }) {
            val rows = groupedReport(positions, group, Segment.ALL)
            assertEquals(3, rows.sumOf { it.metrics.tradeCount })
            assertEquals(0.9, rows.sumOf { it.metrics.netPnl }, 1e-9)
        }
        val rows = groupedReport(positions, ReportGrouping.ENTRY_TIME, Segment.ALL)
        assertEquals(49, rows.size)
        assertEquals(2, rows.single { it.key == "slot:19" }.positions.size)
        assertEquals(1, rows.single { it.key == "unverified" }.positions.size)
        assertNull(rows.first().metrics.winRate)
    }

    @Test fun durationBoundariesAreLowerInclusive() {
        val boundaries = listOf(0, 59, 60, 299, 300, 899, 900, 3599, 3600, 14399, 14400)
        val expected = listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5)
        for ((seconds, index) in boundaries.zip(expected)) {
            val row = groupedReport(listOf(position(seconds)), ReportGrouping.DURATION, Segment.DAY).single { it.positions.isNotEmpty() }
            assertEquals("day:$index", row.key, "seconds=$seconds")
        }
    }

    @Test fun entryPriceBandsAreOrderedAndLowerInclusive() {
        val cutoffs = listOf(0.10, 0.25, 0.50, 0.75, 1.00, 2.00, 5.00, 10.00, 20.00)
        val base = position()
        val positions = listOf(base.copy(averageEntryPrice = 0.0001)) +
            cutoffs.flatMap { listOf(base.copy(averageEntryPrice = it - 0.0001), base.copy(averageEntryPrice = it)) }
        val rows = groupedReport(positions, ReportGrouping.ENTRY_PRICE, Segment.ALL)

        assertEquals(listOf("<$0.10", "$0.10–<$0.25", "$0.25–<$0.50", "$0.50–<$0.75", "$0.75–<$1.00",
            "$1–<$2", "$2–<$5", "$5–<$10", "$10–<$20", "$20+"), rows.map { it.label })
        assertEquals(positions, rows.flatMap { it.positions })
        assertEquals(List(9) { 2 } + 1, rows.map { it.metrics.tradeCount })
        assertEquals(rows.map { it.key }, groupedReport(emptyList(), ReportGrouping.ENTRY_PRICE, Segment.DAY).map { it.key })
    }

    @Test fun entryPriceUsesWeightedOpeningFillsAndExistingOutcomeRules() {
        val base = position()
        val open = base.transactions.first().copy(price = 0.25, shares = 1.0, fees = 0.0)
        val close = base.transactions.last().copy(price = 1.0, shares = 4.0, fees = 0.0)
        val scaledLong = FifoMatcher.computeClosedPositions(listOf(open, open.copy(price = 1.0, shares = 3.0), close)).single()
        val short = FifoMatcher.computeClosedPositions(listOf(open.copy(action = Action.SELL, price = 0.875),
            close.copy(action = Action.BUY, price = 0.75, shares = 1.0, fees = 0.25))).single()
        val scratch = short.copy(averageExitPrice = short.averageEntryPrice)
        val breakEven = short.copy(profitLoss = 0.0)
        val priceUnavailable = base.copy(averageEntryPrice = 0.0)
        val positions = listOf(scaledLong, short, scratch, breakEven, priceUnavailable)
        val rows = groupedReport(positions, ReportGrouping.ENTRY_PRICE, Segment.ALL)
        val bucket = rows.single { it.key == "price:4" }

        assertEquals(0.8125, scaledLong.averageEntryPrice)
        assertEquals(TradeDirection.SHORT, short.direction)
        assertEquals(listOf(scaledLong, short, scratch, breakEven), bucket.positions)
        assertEquals(1, bucket.metrics.winCount)
        assertEquals(1, bucket.metrics.lossCount)
        assertEquals(2, bucket.metrics.neutralCount)
        assertEquals(0.5, bucket.metrics.winRate)
        assertEquals(positions.size, rows.sumOf { it.metrics.tradeCount })
        assertEquals(positions.sumOf { it.profitLoss }, rows.sumOf { it.metrics.netPnl }, 1e-9)
        assertEquals("Price unavailable", rows.last().label)
        assertEquals(listOf(priceUnavailable), rows.last().positions)
    }

    @Test fun sourceClockRejectsDstGapsAndFolds() {
        assertNull(unambiguousEasternInstant(LocalDateTime.parse("2026-03-08T02:30")))
        assertNull(unambiguousEasternInstant(LocalDateTime.parse("2026-11-01T01:30")))
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), unambiguousEasternInstant(LocalDateTime.parse("2026-03-08T03:30")))
        assertEquals(3600.0, position(3600, "2026-03-08T06:30:00Z").verifiedHoldSeconds())
    }

    @Test fun dateOnlyQualifiesOnlyForWeekdayAndUnknownSortsLast() {
        val unknown = position(verified = false)
        val dateOnly = unknown.copy(transactions = unknown.transactions.map { it.copy(verifiedMarketDate = LocalDate(2026, 10, 12)) })
        assertEquals("weekday:0", groupedReport(listOf(dateOnly), ReportGrouping.ENTRY_WEEKDAY, Segment.ALL).single { it.positions.isNotEmpty() }.key)
        assertNull(dateOnly.verifiedHoldSeconds())
        for (direction in SortDirection.entries) assertEquals(unknown, sortPositions(listOf(unknown, position(0)), SortColumn.HELD, direction).last())
        assertEquals(0.0, computeMetrics(listOf(unknown, position(0))).avgHoldSeconds)
        assertEquals(1, computeMetrics(listOf(unknown, position(0))).verifiedHoldCount)
    }
}
