package io.earlisreal.ejournal.domain.marketdata

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Keyless provider backed by Yahoo's undocumented v8 chart API. Quirks this encodes:
 * requires a browser-like User-Agent; `range` silently coarsens granularity so requests
 * always use explicit period1/period2 and verify the returned granularity; its 1-min data
 * is capped at 8 days per request (chunked here) and only the trailing ~30 days. In this app
 * Yahoo serves daily bars only — all 1-min fetches route to Alpaca (Yahoo has no extended-hours
 * coverage), so 1-min always needs Alpaca keys regardless of the trade's age.
 */
class YahooFinanceProvider(private val client: HttpClient) : MarketDataProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getBars(symbol: String, timeframe: Timeframe, from: LocalDate, to: LocalDate): List<Bar> =
        chunk(timeframe, from, to).flatMap { (chunkFrom, chunkTo) ->
            fetchChunk(symbol, timeframe, chunkFrom, chunkTo)
        }

    private fun chunk(timeframe: Timeframe, from: LocalDate, to: LocalDate): List<Pair<LocalDate, LocalDate>> {
        if (timeframe == Timeframe.DAILY) return listOf(from to to)
        val chunks = mutableListOf<Pair<LocalDate, LocalDate>>()
        var start = from
        while (start <= to) {
            val end = minOf(start.plus(DatePeriod(days = MAX_INTRADAY_DAYS_PER_REQUEST - 1)), to)
            chunks.add(start to end)
            start = end.plus(DatePeriod(days = 1))
        }
        return chunks
    }

    private suspend fun fetchChunk(symbol: String, timeframe: Timeframe, from: LocalDate, to: LocalDate): List<Bar> {
        val interval = when (timeframe) {
            Timeframe.DAILY -> "1d"
            Timeframe.ONE_MINUTE -> "1m"
        }
        val response = runCatching {
            client.get("$BASE_URL/$symbol") {
                header(HttpHeaders.UserAgent, USER_AGENT)
                parameter("interval", interval)
                parameter("period1", from.atStartOfDayIn(EASTERN).epochSeconds)
                parameter("period2", to.plus(DatePeriod(days = 1)).atStartOfDayIn(EASTERN).epochSeconds)
            }
        }.getOrElse { throw TransientFetchException("Yahoo request failed for $symbol", it) }

        return parse(symbol, timeframe, interval, response)
    }

    private suspend fun parse(symbol: String, timeframe: Timeframe, interval: String, response: HttpResponse): List<Bar> {
        val body = response.bodyAsText()
        val chart = runCatching { json.decodeFromString<ChartResponse>(body).chart }.getOrNull()

        if (response.status == HttpStatusCode.NotFound) throw SymbolNotFoundException(symbol)
        // Yahoo returns 400 "Data doesn't exist" when the requested range has no bars yet
        // (e.g. requesting today's daily bar before market close). Treat as empty, not an error.
        if (response.status == HttpStatusCode.BadRequest &&
            chart?.error?.description?.contains("Data doesn't exist") == true) {
            return emptyList()
        }
        if (response.status.value >= 400 || chart == null) {
            throw TransientFetchException("Yahoo returned ${response.status} for $symbol: ${chart?.error?.description ?: body.take(200)}")
        }
        chart.error?.let { throw TransientFetchException("Yahoo error for $symbol: ${it.description}") }

        val result = chart.result?.firstOrNull() ?: return emptyList()
        result.meta.dataGranularity?.let { granularity ->
            if (granularity != interval) {
                throw TransientFetchException("Yahoo returned $granularity bars for a $interval request ($symbol)")
            }
        }

        val timestamps = result.timestamp ?: return emptyList()
        val quote = result.indicators.quote.firstOrNull() ?: return emptyList()
        val zone = TimeZone.of(result.meta.exchangeTimezoneName)

        val bars = timestamps.indices.mapNotNull { i ->
            val raw = Instant.fromEpochSeconds(timestamps[i]).toLocalDateTime(zone)
            // A daily bar is identified by its date. Yahoo stamps the most-recent (in-progress /
            // just-closed) day at the last-trade time (e.g. 16:00:01) rather than the session open,
            // so drop the intraday time — otherwise the same date fetched while live and again once
            // finalised (09:30) lands as two rows under the (symbol, timeframe, timestamp) key.
            val timestamp = if (timeframe == Timeframe.DAILY) LocalDateTime(raw.date, LocalTime(0, 0)) else raw
            Bar(
                symbol = symbol,
                timeframe = timeframe,
                timestamp = timestamp,
                open = quote.open?.getOrNull(i) ?: return@mapNotNull null,
                high = quote.high?.getOrNull(i) ?: return@mapNotNull null,
                low = quote.low?.getOrNull(i) ?: return@mapNotNull null,
                close = quote.close?.getOrNull(i) ?: return@mapNotNull null,
                volume = quote.volume?.getOrNull(i) ?: return@mapNotNull null,
            )
        }
        // The in-progress minute arrives as a zero-volume placeholder at the tail.
        return if (bars.size > 1 && bars.last().volume == 0L) bars.dropLast(1) else bars
    }

    @Serializable
    private data class ChartResponse(val chart: Chart)

    @Serializable
    private data class Chart(val result: List<ChartResult>? = null, val error: ChartError? = null)

    @Serializable
    private data class ChartError(val code: String? = null, val description: String? = null)

    @Serializable
    private data class ChartResult(val meta: Meta, val timestamp: List<Long>? = null, val indicators: Indicators)

    @Serializable
    private data class Meta(val exchangeTimezoneName: String, val dataGranularity: String? = null)

    @Serializable
    private data class Indicators(val quote: List<Quote>)

    @Serializable
    private data class Quote(
        val open: List<Double?>? = null,
        val high: List<Double?>? = null,
        val low: List<Double?>? = null,
        val close: List<Double?>? = null,
        val volume: List<Long?>? = null,
    )

    companion object {
        private const val BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart"
        private const val USER_AGENT = "Mozilla/5.0 (compatible; eJournal)"
        private const val MAX_INTRADAY_DAYS_PER_REQUEST = 8
        private val EASTERN = TimeZone.of("America/New_York")
    }
}
