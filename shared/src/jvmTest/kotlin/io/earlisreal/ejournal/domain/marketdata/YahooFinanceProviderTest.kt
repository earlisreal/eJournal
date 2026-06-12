package io.earlisreal.ejournal.domain.marketdata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class YahooFinanceProviderTest {

    private val eastern = TimeZone.of("America/New_York")

    private fun epoch(datetime: String): Long =
        LocalDateTime.parse(datetime).toInstant(eastern).epochSeconds

    private fun chartJson(
        timestamps: List<Long>,
        opens: List<Double?>,
        highs: List<Double?>,
        lows: List<Double?>,
        closes: List<Double?>,
        volumes: List<Long?>,
        granularity: String = "1d",
    ): String {
        fun nums(values: List<Any?>) = values.joinToString(",") { it?.toString() ?: "null" }
        return """
            {"chart":{"result":[{
                "meta":{"symbol":"AAPL","exchangeTimezoneName":"America/New_York","dataGranularity":"$granularity"},
                "timestamp":[${timestamps.joinToString(",")}],
                "indicators":{"quote":[{
                    "open":[${nums(opens)}],"high":[${nums(highs)}],"low":[${nums(lows)}],
                    "close":[${nums(closes)}],"volume":[${nums(volumes)}]
                }]}
            }],"error":null}}
        """.trimIndent()
    }

    private fun MockRequestHandleScope.jsonResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun provider(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<YahooFinanceProvider, MockEngine> {
        val engine = MockEngine { request -> handler(request) }
        return YahooFinanceProvider(HttpClient(engine)) to engine
    }

    @Test
    fun `parses daily bars with exchange-local timestamps`() = runTest {
        val (provider, _) = provider {
            jsonResponse(
                chartJson(
                    timestamps = listOf(epoch("2026-06-09T00:00"), epoch("2026-06-10T00:00")),
                    opens = listOf(290.0, 291.0), highs = listOf(295.0, 296.0),
                    lows = listOf(288.0, 289.0), closes = listOf(294.0, 295.0),
                    volumes = listOf(50_000_000L, 51_000_000L),
                )
            )
        }
        val bars = provider.getBars("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-09"), LocalDate.parse("2026-06-10"))
        assertEquals(2, bars.size)
        assertEquals(LocalDateTime.parse("2026-06-09T00:00"), bars[0].timestamp)
        assertEquals(Bar("AAPL", Timeframe.DAILY, LocalDateTime.parse("2026-06-10T00:00"), 291.0, 296.0, 289.0, 295.0, 51_000_000L), bars[1])
    }

    @Test
    fun `sends explicit period params interval and user agent`() = runTest {
        val (provider, engine) = provider {
            jsonResponse(chartJson(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        }
        provider.getBars("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))

        val request = engine.requestHistory.single()
        assertTrue(request.url.toString().startsWith("https://query1.finance.yahoo.com/v8/finance/chart/AAPL"))
        assertEquals("1d", request.url.parameters["interval"])
        assertEquals(epoch("2026-06-01T00:00").toString(), request.url.parameters["period1"])
        assertEquals(epoch("2026-06-11T00:00").toString(), request.url.parameters["period2"])
        assertTrue(request.headers[HttpHeaders.UserAgent].orEmpty().startsWith("Mozilla"))
    }

    @Test
    fun `skips bars with null quote values`() = runTest {
        val (provider, _) = provider {
            jsonResponse(
                chartJson(
                    timestamps = listOf(epoch("2026-06-10T09:30"), epoch("2026-06-10T09:31"), epoch("2026-06-10T09:32")),
                    opens = listOf(100.0, null, 102.0), highs = listOf(101.0, null, 103.0),
                    lows = listOf(99.0, null, 101.0), closes = listOf(100.5, null, 102.5),
                    volumes = listOf(1000L, null, 1200L),
                    granularity = "1m",
                )
            )
        }
        val bars = provider.getBars("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))
        assertEquals(2, bars.size)
        assertEquals(listOf(100.0, 102.0), bars.map { it.open })
    }

    @Test
    fun `drops trailing zero-volume placeholder bar`() = runTest {
        val (provider, _) = provider {
            jsonResponse(
                chartJson(
                    timestamps = listOf(epoch("2026-06-10T09:30"), epoch("2026-06-10T09:31")),
                    opens = listOf(100.0, 100.5), highs = listOf(101.0, 100.5),
                    lows = listOf(99.0, 100.5), closes = listOf(100.5, 100.5),
                    volumes = listOf(1000L, 0L),
                    granularity = "1m",
                )
            )
        }
        val bars = provider.getBars("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))
        assertEquals(1, bars.size)
    }

    @Test
    fun `chunks one-minute ranges into at most 8 days per request`() = runTest {
        val (provider, engine) = provider {
            jsonResponse(chartJson(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), granularity = "1m"))
        }
        // 20 days → 3 chunks (8 + 8 + 4)
        provider.getBars("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-05-22"), LocalDate.parse("2026-06-10"))
        assertEquals(3, engine.requestHistory.size)
    }

    @Test
    fun `throws when returned granularity does not match request`() = runTest {
        val (provider, _) = provider {
            jsonResponse(
                chartJson(
                    timestamps = listOf(epoch("2026-06-10T00:00")),
                    opens = listOf(100.0), highs = listOf(101.0), lows = listOf(99.0),
                    closes = listOf(100.5), volumes = listOf(1000L),
                    granularity = "3mo",
                )
            )
        }
        assertFailsWith<TransientFetchException> {
            provider.getBars("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
    }

    @Test
    fun `maps not-found error to SymbolNotFoundException`() = runTest {
        val (provider, _) = provider {
            jsonResponse(
                """{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}""",
                HttpStatusCode.NotFound,
            )
        }
        assertFailsWith<SymbolNotFoundException> {
            provider.getBars("NOPE", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
    }

    @Test
    fun `maps server errors to TransientFetchException`() = runTest {
        val (provider, _) = provider { jsonResponse("""{"oops":true}""", HttpStatusCode.InternalServerError) }
        assertFailsWith<TransientFetchException> {
            provider.getBars("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
    }
}
