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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed class ConnectionResult {
    data object Connected : ConnectionResult()
    data object InvalidKeys : ConnectionResult()
    data class NetworkError(val message: String) : ConnectionResult()
}

/**
 * Keyed provider for Alpaca's data API. Reads credentials per request so keys saved in
 * Settings take effect immediately. Uses the SIP (consolidated tape) feed for full
 * extended-hours coverage — requires an Alpaca data subscription that includes SIP.
 */
class AlpacaProvider(
    private val client: HttpClient,
    private val credentialsRepository: CredentialsRepository,
    private val now: () -> Instant = { Clock.System.now() },
) : MarketDataProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getBars(symbol: String, timeframe: Timeframe, from: LocalDate, to: LocalDate): List<Bar> {
        val credentials = credentialsRepository.getAlpacaCredentials()
            ?: throw InvalidKeysException("Alpaca keys are not configured")
        val timeframeParam = when (timeframe) {
            Timeframe.DAILY -> "1Day"
            Timeframe.ONE_MINUTE -> "1Min"
        }

        val start = from.atStartOfDayIn(EASTERN)
        // Free-tier SIP rejects ranges whose end is within the last ~15 min ("subscription does
        // not permit querying recent SIP data"), so clamp end to 16 min ago. Harmless for the
        // historical trades we chart; the future end (tomorrow 00:00 ET) for today is what triggers it.
        val end = minOf(to.plus(DatePeriod(days = 1)).atStartOfDayIn(EASTERN), now() - SIP_RECENT_DELAY)
        if (start >= end) return emptyList()

        val bars = mutableListOf<Bar>()
        var pageToken: String? = null
        do {
            val response = request(credentials, "$BASE_URL/v2/stocks/$symbol/bars") {
                parameter("timeframe", timeframeParam)
                parameter("start", start.toString())
                parameter("end", end.toString())
                parameter("limit", PAGE_LIMIT)
                parameter("adjustment", "raw")
                // SIP (consolidated tape), not IEX: IEX is a single low-volume venue with almost
                // no early pre-market prints for thin names, so 04:00–09:30 ET bars were missing.
                // SIP covers the full extended-hours session. No extended_hours param — the bars
                // endpoint rejects it (HTTP 400) and includes pre/post-market bars by default.
                parameter("feed", "sip")
                pageToken?.let { parameter("page_token", it) }
            }
            throwOnError(response, symbol)

            val page = json.decodeFromString<BarsResponse>(response.bodyAsText())
            page.bars.orEmpty().mapTo(bars) { it.toDomain(symbol, timeframe) }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return bars
    }

    /** Lightest authenticated data call — validates keys against the exact API we fetch from. */
    suspend fun testConnection(): ConnectionResult {
        val credentials = credentialsRepository.getAlpacaCredentials() ?: return ConnectionResult.InvalidKeys
        val response = runCatching {
            request(credentials, "$BASE_URL/v2/stocks/AAPL/trades/latest") {
                parameter("feed", "iex")
            }
        }.getOrElse { return ConnectionResult.NetworkError(it.message ?: "Request failed") }
        return when {
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden ->
                ConnectionResult.InvalidKeys
            response.status.value >= 400 ->
                ConnectionResult.NetworkError("Alpaca returned ${response.status}")
            else -> ConnectionResult.Connected
        }
    }

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
                println("[Alpaca] ${response.status} for $symbol: ${response.bodyAsText().take(400)}")
                throw InvalidKeysException("Alpaca rejected the configured keys")
            }
            response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.UnprocessableEntity ->
                throw SymbolNotFoundException(symbol)
            response.status.value >= 400 -> {
                val body = response.bodyAsText()
                println("[Alpaca] ${response.status} for $symbol: ${body.take(400)}")
                throw TransientFetchException("Alpaca returned ${response.status} for $symbol: ${body.take(200)}")
            }
        }
    }

    @Serializable
    private data class BarsResponse(
        val bars: List<AlpacaBar>? = null,
        @SerialName("next_page_token") val nextPageToken: String? = null,
    )

    @Serializable
    private data class AlpacaBar(
        val t: String,
        val o: Double,
        val h: Double,
        val l: Double,
        val c: Double,
        val v: Long,
    ) {
        fun toDomain(symbol: String, timeframe: Timeframe) = Bar(
            symbol = symbol,
            timeframe = timeframe,
            timestamp = Instant.parse(t).toLocalDateTime(EASTERN),
            open = o, high = h, low = l, close = c,
            volume = v,
        )
    }

    companion object {
        private const val BASE_URL = "https://data.alpaca.markets"
        private const val PAGE_LIMIT = 10_000
        private val EASTERN = TimeZone.of("America/New_York")

        // Free-tier SIP can't query the most recent 15 min; 16 gives a safety margin.
        private val SIP_RECENT_DELAY = 16.minutes
    }
}
