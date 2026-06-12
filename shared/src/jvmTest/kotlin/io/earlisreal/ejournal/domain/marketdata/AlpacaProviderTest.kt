package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.CredentialsRepository
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

private class FakeCredentials(var credentials: AlpacaCredentials? = AlpacaCredentials("key-id", "secret")) : CredentialsRepository {
    override fun getAlpacaCredentials(): AlpacaCredentials? = credentials
    override fun setAlpacaCredentials(credentials: AlpacaCredentials) { this.credentials = credentials }
}

class AlpacaProviderTest {

    private fun MockRequestHandleScope.jsonResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun provider(
        credentials: CredentialsRepository = FakeCredentials(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<AlpacaProvider, MockEngine> {
        val engine = MockEngine { request -> handler(request) }
        return AlpacaProvider(HttpClient(engine), credentials) to engine
    }

    @Test
    fun `parses bars converting utc timestamps to eastern time`() = runTest {
        val (provider, _) = provider {
            jsonResponse(
                // 13:30Z on an EDT date == 09:30 New York time
                """{"bars":[{"t":"2026-06-10T13:30:00Z","o":290.0,"h":295.0,"l":288.0,"c":294.0,"v":1500000}],
                    "symbol":"AAPL","next_page_token":null}""",
            )
        }
        val bars = provider.getBars("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))
        assertEquals(
            listOf(Bar("AAPL", Timeframe.ONE_MINUTE, LocalDateTime.parse("2026-06-10T09:30"), 290.0, 295.0, 288.0, 294.0, 1_500_000L)),
            bars,
        )
    }

    @Test
    fun `sends credential headers and timeframe params`() = runTest {
        val (provider, engine) = provider {
            jsonResponse("""{"bars":[],"symbol":"AAPL","next_page_token":null}""")
        }
        provider.getBars("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))

        val request = engine.requestHistory.single()
        assertTrue(request.url.toString().startsWith("https://data.alpaca.markets/v2/stocks/AAPL/bars"))
        assertEquals("key-id", request.headers["APCA-API-KEY-ID"])
        assertEquals("secret", request.headers["APCA-API-SECRET-KEY"])
        assertEquals("1Day", request.url.parameters["timeframe"])
    }

    @Test
    fun `follows next_page_token pagination`() = runTest {
        val (provider, engine) = provider { request ->
            if (request.url.parameters["page_token"] == null) {
                jsonResponse(
                    """{"bars":[{"t":"2026-06-09T04:00:00Z","o":1.0,"h":2.0,"l":0.5,"c":1.5,"v":100}],
                        "symbol":"AAPL","next_page_token":"tok123"}""",
                )
            } else {
                jsonResponse(
                    """{"bars":[{"t":"2026-06-10T04:00:00Z","o":2.0,"h":3.0,"l":1.5,"c":2.5,"v":200}],
                        "symbol":"AAPL","next_page_token":null}""",
                )
            }
        }
        val bars = provider.getBars("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-09"), LocalDate.parse("2026-06-10"))
        assertEquals(2, bars.size)
        assertEquals("tok123", engine.requestHistory[1].url.parameters["page_token"])
    }

    @Test
    fun `unauthorized maps to InvalidKeysException`() = runTest {
        val (provider, _) = provider { jsonResponse("""{"message":"forbidden"}""", HttpStatusCode.Forbidden) }
        assertFailsWith<InvalidKeysException> {
            provider.getBars("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
    }

    @Test
    fun `missing credentials fail as InvalidKeysException without a request`() = runTest {
        val (provider, engine) = provider(credentials = FakeCredentials(credentials = null)) {
            jsonResponse("""{"bars":[]}""")
        }
        assertFailsWith<InvalidKeysException> {
            provider.getBars("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun `bad request maps to SymbolNotFoundException`() = runTest {
        val (provider, _) = provider { jsonResponse("""{"message":"invalid symbol(s): NOPE"}""", HttpStatusCode.BadRequest) }
        assertFailsWith<SymbolNotFoundException> {
            provider.getBars("NOPE", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
    }

    @Test
    fun `server errors map to TransientFetchException`() = runTest {
        val (provider, _) = provider { jsonResponse("""{"message":"oops"}""", HttpStatusCode.BadGateway) }
        assertFailsWith<TransientFetchException> {
            provider.getBars("AAPL", Timeframe.DAILY, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-10"))
        }
    }

    // --- testConnection ---

    @Test
    fun `testConnection returns Connected on success`() = runTest {
        val (provider, engine) = provider { jsonResponse("""{"symbol":"AAPL","trade":{}}""") }
        assertEquals(ConnectionResult.Connected, provider.testConnection())
        assertTrue(engine.requestHistory.single().url.toString().contains("trades/latest"))
    }

    @Test
    fun `testConnection returns InvalidKeys on unauthorized`() = runTest {
        val (provider, _) = provider { jsonResponse("""{"message":"unauthorized"}""", HttpStatusCode.Unauthorized) }
        assertEquals(ConnectionResult.InvalidKeys, provider.testConnection())
    }

    @Test
    fun `testConnection returns InvalidKeys when not configured`() = runTest {
        val (provider, _) = provider(credentials = FakeCredentials(credentials = null)) { jsonResponse("{}") }
        assertEquals(ConnectionResult.InvalidKeys, provider.testConnection())
    }

    @Test
    fun `testConnection returns NetworkError when the request fails`() = runTest {
        val (provider, _) = provider { throw IOException("no route to host") }
        assertTrue(provider.testConnection() is ConnectionResult.NetworkError)
    }
}
