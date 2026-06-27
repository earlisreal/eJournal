package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.TradeZeroCredentials
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeCryptoCredentials(
    var credentials: AlpacaCredentials? = AlpacaCredentials("key-id", "secret"),
) : CredentialsRepository {
    override fun getAlpacaCredentials(): AlpacaCredentials? = credentials
    override fun setAlpacaCredentials(credentials: AlpacaCredentials) { this.credentials = credentials }
    override fun getTradeZeroCredentials(): TradeZeroCredentials? = null
    override fun setTradeZeroCredentials(credentials: TradeZeroCredentials) {}
}

class AlpacaCryptoProviderTest {

    private fun MockRequestHandleScope.jsonResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun provider(
        credentials: CredentialsRepository = FakeCryptoCredentials(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<AlpacaCryptoProvider, MockEngine> {
        val engine = MockEngine { request -> handler(request) }
        return AlpacaCryptoProvider(HttpClient(engine), credentials) to engine
    }

    @Test
    fun `requests the BASE-USD pair, stores bars under the bare symbol, keeps utc timestamps`() = runTest {
        val (provider, engine) = provider {
            jsonResponse(
                """{"bars":{"BTC/USD":[
                    {"t":"2026-06-10T13:30:00Z","o":290.0,"h":295.0,"l":288.0,"c":294.0,"v":12.0,"n":5,"vw":292.0}
                ]},"next_page_token":null}""",
            )
        }
        val bars = provider.getBars("BTC", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))

        val request = engine.requestHistory.single()
        assertTrue(request.url.toString().startsWith("https://data.alpaca.markets/v1beta3/crypto/us/bars"))
        assertEquals("BTC/USD", request.url.parameters["symbols"])
        assertEquals("1Min", request.url.parameters["timeframe"])
        assertEquals("key-id", request.headers["APCA-API-KEY-ID"])
        assertEquals("secret", request.headers["APCA-API-SECRET-KEY"])
        // UTC is kept verbatim (no Eastern conversion like stocks), and the bar is stored under "BTC".
        assertEquals(
            listOf(Bar("BTC", Timeframe.ONE_MINUTE, LocalDateTime.parse("2026-06-10T13:30"), 290.0, 295.0, 288.0, 294.0, 12L)),
            bars,
        )
    }

    @Test
    fun `mixed-case symbols are uppercased in the requested pair`() = runTest {
        val (provider, engine) = provider { jsonResponse("""{"bars":{},"next_page_token":null}""") }
        provider.getBars("Dash", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        assertEquals("DASH/USD", engine.requestHistory.single().url.parameters["symbols"])
    }

    @Test
    fun `daily bars are normalized to midnight so a calendar day maps to one row`() = runTest {
        // Alpaca stamps 1Day crypto bars at 00:00:00Z already; normalization guards the same
        // duplicate-row bug the stock daily path guards against if that ever changes.
        val (provider, _) = provider {
            jsonResponse(
                """{"bars":{"ETH/USD":[
                    {"t":"2026-06-10T00:00:00Z","o":1.0,"h":2.0,"l":0.5,"c":1.5,"v":3.0}
                ]},"next_page_token":null}""",
            )
        }
        val bars = provider.getBars("ETH", Timeframe.DAILY, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))
        assertEquals(LocalDateTime.parse("2026-06-10T00:00"), bars.single().timestamp)
    }

    @Test
    fun `fractional volume is rounded to a whole number`() = runTest {
        val (provider, _) = provider {
            jsonResponse(
                """{"bars":{"BTC/USD":[
                    {"t":"2026-06-10T00:00:00Z","o":1.0,"h":2.0,"l":0.5,"c":1.5,"v":1500.7}
                ]},"next_page_token":null}""",
            )
        }
        val bars = provider.getBars("BTC", Timeframe.DAILY, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))
        assertEquals(1501L, bars.single().volume)
    }

    @Test
    fun `follows next_page_token pagination`() = runTest {
        val (provider, engine) = provider { request ->
            if (request.url.parameters["page_token"] == null) {
                jsonResponse(
                    """{"bars":{"BTC/USD":[
                        {"t":"2026-06-09T00:00:00Z","o":1.0,"h":2.0,"l":0.5,"c":1.5,"v":1.0}
                    ]},"next_page_token":"tok123"}""",
                )
            } else {
                jsonResponse(
                    """{"bars":{"BTC/USD":[
                        {"t":"2026-06-10T00:00:00Z","o":2.0,"h":3.0,"l":1.5,"c":2.5,"v":2.0}
                    ]},"next_page_token":null}""",
                )
            }
        }
        val bars = provider.getBars("BTC", Timeframe.DAILY, LocalDate.parse("2026-06-09"), LocalDate.parse("2026-06-10"))
        assertEquals(2, bars.size)
        assertEquals("tok123", engine.requestHistory[1].url.parameters["page_token"])
    }

    @Test
    fun `empty bars map for the symbol yields no bars`() = runTest {
        val (provider, _) = provider { jsonResponse("""{"bars":{},"next_page_token":null}""") }
        val bars = provider.getBars("BTC", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        assertTrue(bars.isEmpty())
    }

    @Test
    fun `unauthorized maps to InvalidKeysException`() = runTest {
        val (provider, _) = provider { jsonResponse("""{"message":"forbidden"}""", HttpStatusCode.Forbidden) }
        assertFailsWith<InvalidKeysException> {
            provider.getBars("BTC", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
    }

    @Test
    fun `missing credentials fail as InvalidKeysException without a request`() = runTest {
        val (provider, engine) = provider(credentials = FakeCryptoCredentials(credentials = null)) {
            jsonResponse("""{"bars":{}}""")
        }
        assertFailsWith<InvalidKeysException> {
            provider.getBars("BTC", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun `bad request maps to SymbolNotFoundException`() = runTest {
        val (provider, _) = provider { jsonResponse("""{"message":"invalid symbol(s): NOPE/USD"}""", HttpStatusCode.BadRequest) }
        assertFailsWith<SymbolNotFoundException> {
            provider.getBars("NOPE", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
    }

    @Test
    fun `server errors map to TransientFetchException`() = runTest {
        val (provider, _) = provider { jsonResponse("""{"message":"oops"}""", HttpStatusCode.BadGateway) }
        assertFailsWith<TransientFetchException> {
            provider.getBars("BTC", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
    }

    @Test
    fun `network failure surfaces as an exception`() = runTest {
        val (provider, _) = provider { throw IOException("no route to host") }
        assertFailsWith<Exception> {
            provider.getBars("BTC", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
    }
}
