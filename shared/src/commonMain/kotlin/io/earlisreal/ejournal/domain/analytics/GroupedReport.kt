package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.TradeDirection

enum class ReportGrouping(val label: String) {
    ENTRY_TIME("Entry time"), ENTRY_WEEKDAY("Entry weekday"), DURATION("Holding duration"),
    ENTRY_PRICE("Entry price"), SYMBOL("Symbol"), DIRECTION("Direction"), TAG("Tag");

    fun isAvailableFor(market: Market?): Boolean = this != ENTRY_PRICE || market == Market.US_STOCKS
}

data class ReportBucket(val key: String, val label: String, val positions: List<ClosedPosition>) {
    val metrics: DashboardMetrics = computeMetrics(positions)
    val unverified: Boolean get() = key.endsWith("unverified")
}

enum class ReportMetric(val label: String) {
    COUNT("Count"), NET_PNL("Net P&L"), AVERAGE("Avg P&L"), WIN_RATE("Win rate"),
    WINNER("Avg winner"), LOSER("Avg loser"), PROFIT_FACTOR("Profit factor"),
    WIN_COUNT("Winners"), LOSS_COUNT("Losers"), NEUTRAL_COUNT("Neutral");

    fun value(m: DashboardMetrics): Double? = when (this) {
        COUNT -> m.tradeCount.toDouble()
        NET_PNL -> m.netPnl
        AVERAGE -> m.expectancy
        WIN_RATE -> m.winRate
        WINNER -> m.avgWin
        LOSER -> m.avgLoss
        PROFIT_FACTOR -> m.profitFactor
        WIN_COUNT -> m.winCount.toDouble()
        LOSS_COUNT -> m.lossCount.toDouble()
        NEUTRAL_COUNT -> m.neutralCount.toDouble()
    }
}

fun sortReportBuckets(rows: List<ReportBucket>, metric: ReportMetric?, direction: SortDirection): List<ReportBucket> {
    if (metric == null) return rows
    return rows.sortedWith { a, b ->
        val x = metric.value(a.metrics)
        val y = metric.value(b.metrics)
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            direction == SortDirection.ASC -> x.compareTo(y)
            else -> y.compareTo(x)
        }
    }
}

private val dayBounds = listOf(60.0, 300.0, 900.0, 3600.0, 14400.0)
private val swingBounds = listOf(86400.0, 259200.0, 604800.0, 2592000.0)
private val dayLabels = listOf("<1m", "1–<5m", "5–<15m", "15–<60m", "1–<4h", "≥4h")
private val swingLabels = listOf("<1d", "1–<3d", "3–<7d", "7–<30d", "≥30d")
private val entryPriceBands = listOf(
    0.10 to "<$0.10",
    0.25 to "$0.10–<$0.25",
    0.50 to "$0.25–<$0.50",
    0.75 to "$0.50–<$0.75",
    1.00 to "$0.75–<$1.00",
    2.00 to "$1–<$2",
    5.00 to "$2–<$5",
    10.00 to "$5–<$10",
    20.00 to "$10–<$20",
    Double.POSITIVE_INFINITY to "$20+",
)
private const val UNAVAILABLE_PRICE = "price:unavailable"

internal fun entryPriceBandIndex(price: Double): Int =
    if (price.isFinite() && price > 0.0) entryPriceBands.indexOfFirst { price < it.first } else -1

fun groupedReport(positions: List<ClosedPosition>, grouping: ReportGrouping, segment: Segment): List<ReportBucket> {
    require(grouping != ReportGrouping.TAG)
    val labels = linkedMapOf<String, String>()
    when (grouping) {
        ReportGrouping.ENTRY_WEEKDAY -> listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            .forEachIndexed { i, name -> labels["weekday:$i"] = name }
        ReportGrouping.ENTRY_TIME -> repeat(48) { i ->
            labels["slot:$i"] = "${(i / 2).toString().padStart(2, '0')}:${if (i % 2 == 0) "00" else "30"}"
        }
        ReportGrouping.DURATION -> {
            if (segment != Segment.SWING) dayLabels.forEachIndexed { i, label -> labels["day:$i"] = "Day $label" }
            if (segment != Segment.DAY) swingLabels.forEachIndexed { i, label -> labels["swing:$i"] = "Swing $label" }
        }
        ReportGrouping.ENTRY_PRICE -> entryPriceBands.forEachIndexed { i, (_, label) -> labels["price:$i"] = label }
        ReportGrouping.SYMBOL -> positions.map { it.symbol }.distinct().sorted().forEach { labels["symbol:$it"] = it }
        ReportGrouping.DIRECTION -> TradeDirection.entries.filter { d -> positions.any { it.direction == d } }
            .forEach { labels["direction:${it.name}"] = if (it == TradeDirection.LONG) "Long" else "Short" }
        ReportGrouping.TAG -> Unit
    }
    val members = positions.groupBy { p ->
        when (grouping) {
            ReportGrouping.ENTRY_WEEKDAY -> p.verifiedEntryDate()?.dayOfWeek?.ordinal?.let { "weekday:$it" } ?: "unverified"
            ReportGrouping.ENTRY_TIME -> p.verifiedEntryTime()?.let { "slot:${it.hour * 2 + it.minute / 30}" } ?: "unverified"
            ReportGrouping.DURATION -> {
                val day = classifyTradeType(p) == TradeType.DAY
                val prefix = if (day) "day" else "swing"
                val seconds = p.verifiedHoldSeconds()
                if (seconds == null) "$prefix:unverified" else {
                    val bounds = if (day) dayBounds else swingBounds
                    "$prefix:${bounds.indexOfFirst { seconds < it }.takeIf { it >= 0 } ?: bounds.size}"
                }
            }
            ReportGrouping.ENTRY_PRICE -> {
                val index = entryPriceBandIndex(p.averageEntryPrice)
                if (index >= 0) "price:$index" else UNAVAILABLE_PRICE
            }
            ReportGrouping.SYMBOL -> "symbol:${p.symbol}"
            ReportGrouping.DIRECTION -> "direction:${p.direction.name}"
            ReportGrouping.TAG -> error("Tag uses its existing comparison")
        }
    }
    members.keys.filter { it.endsWith("unverified") }.forEach {
        labels[it] = when (it) {
            "day:unverified" -> "Day · Time unverified"
            "swing:unverified" -> "Swing · Time unverified"
            else -> "Time unverified"
        }
    }
    if (UNAVAILABLE_PRICE in members) labels[UNAVAILABLE_PRICE] = "Price unavailable"
    return labels.map { (key, label) -> ReportBucket(key, label, members[key].orEmpty()) }
}
