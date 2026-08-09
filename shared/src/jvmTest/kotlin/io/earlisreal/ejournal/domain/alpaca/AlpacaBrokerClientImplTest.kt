package io.earlisreal.ejournal.domain.alpaca

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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class AlpacaTestCredentials(
    var credentials: AlpacaCredentials? = AlpacaCredentials("key-id", "secret"),
) : CredentialsRepository {
    override fun getAlpacaCredentials(): AlpacaCredentials? = credentials
    override fun setAlpacaCredentials(credentials: AlpacaCredentials) { this.credentials = credentials }
    override fun getTradeZeroCredentials(): TradeZeroCredentials? = null
    override fun setTradeZeroCredentials(credentials: TradeZeroCredentials) {}
}

class AlpacaBrokerClientImplTest {

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun client(
        credentials: AlpacaCredentials = AlpacaCredentials("key-id", "secret"),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<AlpacaBrokerClientImpl, MockEngine> {
        val engine = MockEngine { request -> handler(request) }
        return AlpacaBrokerClientImpl(HttpClient(engine), AlpacaTestCredentials(credentials)) to engine
    }

    private fun fill(
        id: String,
        symbol: String = "AAPL",
        side: String = "buy",
        price: String = "10.25",
        qty: String = "1.5",
        timestamp: String = "2026-06-10T13:30:00Z",
    ): String =
        """{"id":"$id","order_id":"order-$id","symbol":"$symbol","side":"$side","price":$price,"qty":$qty,"transaction_time":"$timestamp","type":"fill"}"""

    private fun account() = """{"id":"acct-live","account_number":"PA1234","status":"ACTIVE"}"""

    @Test
    fun `connection sends auth headers and selects paper host`() = runTest {
        val (client, engine) = client(AlpacaCredentials("key-id", "secret", AlpacaEnvironment.PAPER)) {
            json(account())
        }

        val result = assertIs<AlpacaConnectionResult.Connected>(client.testConnection())
        assertEquals("acct-live", result.account.id)
        val request = engine.requestHistory.single()
        assertTrue(request.url.toString().startsWith("https://paper-api.alpaca.markets/v2/account"))
        assertEquals("key-id", request.headers["APCA-API-KEY-ID"])
        assertEquals("secret", request.headers["APCA-API-SECRET-KEY"])
    }

    @Test
    fun `live credentials use live trading host`() = runTest {
        val (client, engine) = client(AlpacaCredentials("key-id", "secret", AlpacaEnvironment.LIVE)) {
            if (it.url.encodedPath == "/v2/account") json(account()) else json("[]")
        }
        client.fetchFills(7L, after = null, until = null)
        assertTrue(engine.requestHistory.all { it.url.toString().startsWith("https://api.alpaca.markets/") })
    }

    @Test
    fun `maps fractional fills and converts utc to eastern time`() = runTest {
        val (client, _) = client { request ->
            if (request.url.encodedPath == "/v2/account") json(account())
            else json("[${fill("fill-1")}]" )
        }

        val result = assertIs<AlpacaFetchResult.Success>(client.fetchFills(7L, null, null))
        val transaction = result.transactions.single()
        assertEquals(7L, transaction.portfolioId)
        assertEquals("AAPL", transaction.symbol)
        assertEquals(1.5, transaction.shares)
        assertEquals(10.25, transaction.price)
        assertEquals("2026-06-10T09:30", transaction.datetime.toString())
        assertEquals("alpaca:paper:acct-live:fill-1", transaction.externalId)
        assertEquals(0.0, transaction.fees)
    }

    @Test
    fun `uses eastern standard time during winter`() = runTest {
        val (client, _) = client { request ->
            if (request.url.encodedPath == "/v2/account") json(account())
            else json("[${fill("winter", timestamp = "2026-01-15T14:30:00Z")}]" )
        }

        val result = assertIs<AlpacaFetchResult.Success>(client.fetchFills(7L, null, null))
        assertEquals("2026-01-15T09:30", result.transactions.single().datetime.toString())
    }

    @Test
    fun `keeps partial fills from one order as separate transactions`() = runTest {
        val (client, _) = client { request ->
            if (request.url.encodedPath == "/v2/account") json(account())
            else json("[${fill("fill-a", qty = "100", price = "10.00")},${fill("fill-b", qty = "200", price = "10.03")}]" )
        }

        val result = assertIs<AlpacaFetchResult.Success>(client.fetchFills(7L, null, null))
        assertEquals(2, result.transactions.size)
        assertEquals(setOf("alpaca:paper:acct-live:fill-a", "alpaca:paper:acct-live:fill-b"), result.transactions.map { it.externalId }.toSet())
    }

    @Test
    fun `keeps sells and skips crypto and option symbols`() = runTest {
        val (client, _) = client { request ->
            if (request.url.encodedPath == "/v2/account") json(account())
            else json(
                "[${fill("stock", side = "sell")}," +
                    fill("crypto", symbol = "BTC/USD") + "," +
                    fill("option", symbol = "AAPL  260619C00150000") + "]",
            )
        }

        val result = assertIs<AlpacaFetchResult.Success>(client.fetchFills(7L, null, null))
        assertEquals(1, result.transactions.size)
        assertEquals("SELL", result.transactions.single().action.name)
        assertEquals(1, result.skippedCrypto)
        assertEquals(1, result.skippedOptions)
    }

    @Test
    fun `follows bare-array pagination using the final activity id`() = runTest {
        val requests = mutableListOf<String?>()
        val (client, _) = client { request ->
            if (request.url.encodedPath == "/v2/account") return@client json(account())
            val token = request.url.parameters["page_token"]
            requests += token
            val start = when (token) {
                null -> 0
                "fill-99" -> 100
                "fill-199" -> 200
                else -> error("unexpected token $token")
            }
            val count = if (start == 200) 17 else 100
            json((start until start + count).joinToString(prefix = "[", postfix = "]") { fill("fill-$it") })
        }

        val result = assertIs<AlpacaFetchResult.Success>(client.fetchFills(7L, null, null))
        assertEquals(217, result.transactions.size)
        assertEquals(listOf(null, "fill-99", "fill-199"), requests)
    }

    @Test
    fun `retries rate limits before returning fills`() = runTest {
        var activityAttempts = 0
        val (client, _) = client { request ->
            if (request.url.encodedPath == "/v2/account") json(account())
            else if (++activityAttempts < 3) json("{}", HttpStatusCode.TooManyRequests)
            else json("[${fill("fill-1")}]" )
        }

        val result = assertIs<AlpacaFetchResult.Success>(client.fetchFills(7L, null, null))
        assertEquals(1, result.transactions.size)
        assertEquals(3, activityAttempts)
    }

    @Test
    fun `maps unauthorized and malformed responses to safe errors`() = runTest {
        val (unauthorized, _) = client { request ->
            if (request.url.encodedPath == "/v2/account") json("{}", HttpStatusCode.Unauthorized)
            else json("[]")
        }
        assertEquals(AlpacaConnectionResult.InvalidCredentials, unauthorized.testConnection())

        val (malformed, _) = client { request ->
            if (request.url.encodedPath == "/v2/account") json(account())
            else json("not-json")
        }
        val error = assertIs<AlpacaFetchResult.NetworkError>(malformed.fetchFills(7L, null, null))
        assertEquals("Invalid Alpaca activity response", error.message)

        val (network, _) = client { throw java.io.IOException("timeout") }
        assertIs<AlpacaConnectionResult.NetworkError>(network.testConnection())
    }
}
