package io.earlisreal.ejournal.demo

import io.earlisreal.ejournal.domain.marketdata.AlpacaProvider
import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

private val SYMBOLS = listOf(
    "AAPL", "TSLA", "NVDA", "AMD", "MSFT",
    "META", "GOOGL", "AMZN", "NFLX", "UBER",
    "COIN", "PLTR", "SPY", "QQQ", "IWM",
)

private data class Row(
    val datetime: String,
    val symbol: String,
    val action: String,
    val price: Double,
    val shares: Int,
    val fee: Double = 0.50,
)

private data class PendingSell(
    val sellDate: LocalDate,
    val symbol: String,
    val price: Double,
    val shares: Int,
)

suspend fun generateCsv(alpaca: AlpacaProvider, args: Array<String>) {
    val months = argValue(args, "--months", "6").toIntOrNull()?.coerceIn(1, 120)
        ?: run { println("Invalid --months, using 12."); 12 }
    val tradeType = argValue(args, "--type", "mix").lowercase().let {
        if (it in listOf("day", "swing", "mix")) it else { println("Invalid --type, using mix."); "mix" }
    }
    val outPath = argValue(args, "--output", "demo/demo-trades.csv")

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val start = today.minus(DatePeriod(months = months)).let { LocalDate(it.year, it.monthNumber, 1) }
    val end = today.minus(DatePeriod(days = 1))

    // Fetch real daily bars from Alpaca for every symbol
    println("Fetching daily bars from Alpaca (${start} → ${end})...")
    val barsBySymbol = mutableMapOf<String, Map<LocalDate, Bar>>()
    for (symbol in SYMBOLS) {
        print("  $symbol ... ")
        runCatching { alpaca.getBars(symbol, Timeframe.DAILY, start, end) }
            .onSuccess { bars ->
                barsBySymbol[symbol] = bars.associateBy { it.timestamp.date }
                println("${bars.size} bars")
            }
            .onFailure { println("failed: ${it.message}") }
    }

    val availableSymbols = barsBySymbol.keys.toList()
    if (availableSymbols.isEmpty()) {
        println("No data fetched — check Alpaca credentials (~/.ejournal/credentials.json).")
        return
    }

    val rows = mutableListOf<Row>()
    val pending = mutableListOf<PendingSell>()

    for (day in tradingDays(start, end)) {
        // Flush swing sells due today
        val iter = pending.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            if (s.sellDate == day) {
                rows.add(Row(fmt(day, 15, 30), s.symbol, "SELL", s.price, s.shares))
                iter.remove()
            }
        }

        repeat(Random.nextInt(0, 11)) {
            val kind = when (tradeType) {
                "day"   -> "day"
                "swing" -> "swing"
                else    -> if (Random.nextFloat() < 0.25f) "swing" else "day"
            }

            val symbolsToday = availableSymbols.filter { barsBySymbol[it]?.containsKey(day) == true }
            if (symbolsToday.isEmpty()) return@repeat
            val symbol = symbolsToday.random()
            val bar = barsBySymbol[symbol]!![day]!!
            val shares = sharesFor(bar.open)
            val range = bar.high - bar.low

            if (kind == "day") {
                val bh = Random.nextInt(9, 12)
                val bm = Random.nextInt(if (bh == 9) 30 else 0, 60)
                val buyMins = bh * 60 + bm
                val sellMins = Random.nextInt(buyMins + 30, 15 * 60 + 31)
                // Entry in lower 40% of day's range (realistic intraday entry)
                val entryPrice = round2(if (range > 0) bar.low + Random.nextDouble(0.0, 0.4) * range else bar.open)
                // Exit anywhere in the day's range
                val exitPrice = round2(if (range > 0) bar.low + Random.nextDouble(0.0, 1.0) * range else bar.close)
                rows.add(Row(fmt(day, bh, bm), symbol, "BUY", entryPrice, shares))
                rows.add(Row(fmt(day, sellMins / 60, sellMins % 60), symbol, "SELL", exitPrice, shares))
            } else {
                val holdDays = Random.nextInt(2, 11)
                val sellDay = nthTradingDayAfter(day, holdDays)
                if (sellDay > end) return@repeat
                val sellBar = barsBySymbol[symbol]?.get(sellDay) ?: return@repeat
                // Swing: buy at today's close, sell at exit day's close
                rows.add(Row(fmt(day, 15, 30), symbol, "BUY", round2(bar.close), shares))
                pending.add(PendingSell(sellDay, symbol, round2(sellBar.close), shares))
            }
        }
    }

    rows.sortBy { it.datetime }

    File(outPath).also { it.parentFile?.mkdirs() }.bufferedWriter().use { w ->
        w.write("datetime,symbol,action,price,shares,fees\n")
        rows.forEach { r -> w.write("${r.datetime},${r.symbol},${r.action},${r.price},${r.shares},${r.fee}\n") }
    }

    val closed = rows.count { it.action == "SELL" }
    println("\n${rows.size} transactions ($closed closed positions) → $outPath")
}

private fun argValue(args: Array<String>, flag: String, default: String): String {
    val idx = args.indexOf(flag)
    return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else default
}

private fun tradingDays(from: LocalDate, to: LocalDate): List<LocalDate> {
    val days = mutableListOf<LocalDate>()
    var d = from
    while (d <= to) {
        if (d.dayOfWeek.ordinal < 5) days.add(d) // Mon=0..Fri=4
        d = d.plus(DatePeriod(days = 1))
    }
    return days
}

private fun nthTradingDayAfter(d: LocalDate, n: Int): LocalDate {
    var count = 0
    var cur = d.plus(DatePeriod(days = 1))
    while (true) {
        if (cur.dayOfWeek.ordinal < 5 && ++count == n) return cur
        cur = cur.plus(DatePeriod(days = 1))
    }
}

private fun sharesFor(price: Double): Int = maxOf(1, (Random.nextInt(3_000, 20_001) / price).roundToInt())

private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0

private fun fmt(date: LocalDate, h: Int, m: Int) =
    "${date}T${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:00"
