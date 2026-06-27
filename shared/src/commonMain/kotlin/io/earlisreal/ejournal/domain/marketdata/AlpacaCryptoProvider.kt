package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Keyed provider for Alpaca's crypto data API (`/v1beta3/crypto/us/bars`). Differs from the stock
 * [AlpacaProvider] in the ways crypto differs: the pair is passed as a `symbols` query param (not in
 * the path), the response keys its bars by symbol (`{"bars":{"BTC/USD":[...]}}`), timestamps are
 * UTC (crypto has no exchange/extended-hours session and trades 24/7, so there is no SIP feed and no
 * recent-data clamp), and volume is fractional.
 *
 * Symbols are stored bare ("BTC") but Alpaca needs the `BASE/USD` pair, so a `/USD` quote is appended
 * for the request and the bars are stored back under the bare symbol to match `position.symbol`.
 */
class AlpacaCryptoProvider(
    private val client: HttpClient,
    private val credentialsRepository: CredentialsRepository,
) : MarketDataProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getBars(symbol: String, timeframe: Timeframe, from: LocalDate, to: LocalDate): List<Bar> {
        val credentials = credentialsRepository.getAlpacaCredentials()
            ?: throw InvalidKeysException("Alpaca keys are not configured")
        val timeframeParam = when (timeframe) {
            Timeframe.DAILY -> "1Day"
            Timeframe.ONE_MINUTE -> "1Min"
        }
        val pair = toPair(symbol)
        val start = from.atStartOfDayIn(UTC)
        val end = to.plus(DatePeriod(days = 1)).atStartOfDayIn(UTC)
        if (start >= end) return emptyList()

        val bars = mutableListOf<Bar>()
        var pageToken: String? = null
        do {
            val response = request(credentials, "$BASE_URL/v1beta3/crypto/us/bars") {
                parameter("symbols", pair)
                parameter("timeframe", timeframeParam)
                parameter("start", start.toString())
                parameter("end", end.toString())
                parameter("limit", PAGE_LIMIT)
                pageToken?.let { parameter("page_token", it) }
            }
            throwOnError(response, symbol)

            val page = json.decodeFromString<BarsResponse>(response.bodyAsText())
            page.bars[pair].orEmpty().mapTo(bars) { it.toDomain(symbol, timeframe) }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return bars
    }

    private fun toPair(symbol: String): String = if ("/" in symbol) symbol else "${symbol.uppercase()}/USD"

    private suspend fun request(
        credentials: AlpacaCredentials,
        url: String,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = client.get(url) {
        header("APCA-API-KEY-ID", credentials.keyId)
        header("APCA-API-SECRET-KEY", credentials.secretKey)
        configure()
    }

    private suspend fun throwOnError(response: HttpResponse, symbol: String) {
        when {
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden -> {
                println("[AlpacaCrypto] ${response.status} for $symbol: ${response.bodyAsText().take(400)}")
                throw InvalidKeysException("Alpaca rejected the configured keys")
            }
            response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.UnprocessableEntity ->
                throw SymbolNotFoundException(symbol)
            response.status.value >= 400 -> {
                val body = response.bodyAsText()
                println("[AlpacaCrypto] ${response.status} for $symbol: ${body.take(400)}")
                throw TransientFetchException("Alpaca returned ${response.status} for $symbol: ${body.take(200)}")
            }
        }
    }

    @Serializable
    private data class BarsResponse(
        val bars: Map<String, List<CryptoBar>> = emptyMap(),
        @SerialName("next_page_token") val nextPageToken: String? = null,
    )

    @Serializable
    private data class CryptoBar(
        val t: String,
        val o: Double,
        val h: Double,
        val l: Double,
        val c: Double,
        val v: Double,
    ) {
        fun toDomain(symbol: String, timeframe: Timeframe): Bar {
            val raw = Instant.parse(t).toLocalDateTime(UTC)
            // A daily bar is identified by its date; drop any intraday time so the same calendar
            // day can never land as two rows under the (symbol, market, timeframe, timestamp) key.
            val timestamp = if (timeframe == Timeframe.DAILY) LocalDateTime(raw.date, LocalTime(0, 0)) else raw
            return Bar(
                symbol = symbol,
                timeframe = timeframe,
                timestamp = timestamp,
                open = o, high = h, low = l, close = c,
                volume = v.roundToLong(),
            )
        }
    }

    companion object {
        private const val BASE_URL = "https://data.alpaca.markets"
        private const val PAGE_LIMIT = 10_000
        private val UTC = TimeZone.UTC
    }
}
