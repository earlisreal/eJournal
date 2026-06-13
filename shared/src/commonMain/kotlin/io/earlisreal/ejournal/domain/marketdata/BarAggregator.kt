package io.earlisreal.ejournal.domain.marketdata

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus

data class VwapPoint(val timestamp: LocalDateTime, val value: Double)

data class AggregatedChart(
    val bars: List<Bar>,
    val vwap: List<VwapPoint>,
)

object BarAggregator {

    fun aggregate(bars: List<Bar>, targetTimeframe: ChartTimeframe): AggregatedChart = when (targetTimeframe) {
        ChartTimeframe.ONE_MIN     -> fromMinute(bars)
        ChartTimeframe.FIVE_MIN    -> aggregateMinutes(bars, 5)
        ChartTimeframe.FIFTEEN_MIN -> aggregateMinutes(bars, 15)
        ChartTimeframe.DAILY       -> AggregatedChart(bars, emptyList())
        ChartTimeframe.WEEKLY      -> AggregatedChart(aggregateWeekly(bars), emptyList())
    }

    private fun fromMinute(bars: List<Bar>): AggregatedChart {
        val vwap = mutableListOf<VwapPoint>()
        var runningTpv = 0.0
        var runningVol = 0.0
        var prevDate: LocalDate? = null
        for (bar in bars) {
            if (bar.timestamp.date != prevDate) {
                runningTpv = 0.0
                runningVol = 0.0
                prevDate = bar.timestamp.date
            }
            val tp = (bar.high + bar.low + bar.close) / 3.0
            runningTpv += tp * bar.volume
            runningVol += bar.volume
            if (runningVol > 0.0) vwap.add(VwapPoint(bar.timestamp, runningTpv / runningVol))
        }
        return AggregatedChart(bars, vwap)
    }

    private fun aggregateMinutes(bars: List<Bar>, intervalMinutes: Int): AggregatedChart {
        data class BucketKey(val date: LocalDate, val bucketStartMinute: Int)
        data class BucketAcc(
            val timestamp: LocalDateTime,
            val symbol: String,
            val open: Double,
            var high: Double,
            var low: Double,
            var close: Double,
            var volume: Long,
        )

        val buckets = LinkedHashMap<BucketKey, BucketAcc>()
        var runningTpv = 0.0
        var runningVol = 0.0
        var prevDate: LocalDate? = null
        val vwap = mutableListOf<VwapPoint>()

        for (bar in bars) {
            val date = bar.timestamp.date
            if (date != prevDate) {
                runningTpv = 0.0
                runningVol = 0.0
                prevDate = date
            }
            val minuteOfDay = bar.timestamp.hour * 60 + bar.timestamp.minute
            val bucketStart = minuteOfDay - (minuteOfDay % intervalMinutes)
            val key = BucketKey(date, bucketStart)
            val tp = (bar.high + bar.low + bar.close) / 3.0
            runningTpv += tp * bar.volume
            runningVol += bar.volume

            val existing = buckets[key]
            if (existing == null) {
                buckets[key] = BucketAcc(
                    timestamp = LocalDateTime(date, LocalTime(bucketStart / 60, bucketStart % 60)),
                    symbol = bar.symbol,
                    open = bar.open, high = bar.high, low = bar.low,
                    close = bar.close, volume = bar.volume,
                )
            } else {
                existing.high = maxOf(existing.high, bar.high)
                existing.low  = minOf(existing.low,  bar.low)
                existing.close  = bar.close
                existing.volume += bar.volume
            }

            if (runningVol > 0.0) {
                val vwapValue = runningTpv / runningVol
                val ts = buckets[key]!!.timestamp
                val last = vwap.lastOrNull()
                if (last != null && last.timestamp == ts) vwap[vwap.lastIndex] = VwapPoint(ts, vwapValue)
                else vwap.add(VwapPoint(ts, vwapValue))
            }
        }

        val aggregatedBars = buckets.values.map { acc ->
            Bar(acc.symbol, Timeframe.ONE_MINUTE, acc.timestamp, acc.open, acc.high, acc.low, acc.close, acc.volume)
        }
        return AggregatedChart(aggregatedBars, vwap)
    }

    private fun aggregateWeekly(bars: List<Bar>): List<Bar> {
        data class WeekKey(val monday: LocalDate)
        val weeks = LinkedHashMap<WeekKey, MutableList<Bar>>()
        for (bar in bars) {
            val date = bar.timestamp.date
            val monday = date.minus(DatePeriod(days = date.dayOfWeek.ordinal))
            weeks.getOrPut(WeekKey(monday)) { mutableListOf() }.add(bar)
        }
        return weeks.values.map { weekBars ->
            val first = weekBars.first()
            Bar(
                symbol    = first.symbol,
                timeframe = Timeframe.DAILY,
                timestamp = first.timestamp,
                open      = first.open,
                high      = weekBars.maxOf { it.high },
                low       = weekBars.minOf { it.low },
                close     = weekBars.last().close,
                volume    = weekBars.sumOf { it.volume },
            )
        }
    }
}
