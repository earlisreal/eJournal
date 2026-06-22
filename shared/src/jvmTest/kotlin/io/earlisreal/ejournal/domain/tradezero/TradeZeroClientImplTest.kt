package io.earlisreal.ejournal.domain.tradezero

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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeCredentials(
    private val tradeZero: TradeZeroCredentials? = TradeZeroCredentials("key-id", "secret"),
) : CredentialsRepository {
    override fun getAlpacaCredentials(): AlpacaCredentials? = null
    override fun setAlpacaCredentials(credentials: AlpacaCredentials) {}
    override fun getTradeZeroCredentials(): TradeZeroCredentials? = tradeZero
    override fun setTradeZeroCredentials(credentials: TradeZeroCredentials) {}
}

class TradeZeroClientImplTest {

    private fun MockRequestHandleScope.json(body: String): HttpResponseData =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): TradeZeroClientImpl =
        TradeZeroClientImpl(HttpClient(MockEngine { request -> handler(request) }), FakeCredentials())

    @Test
    fun `builds a natural-key externalId independent of tradeId`() = runTest {
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") ->
                    json("""{"accounts":[{"account":"ACC1"}]}""")
                request.url.encodedPath.contains("/orders/start-date/") ->
                    json(
                        """{"orders":[{"symbol":"AAPL","securityType":"Stock","side":"Buy","qty":100,
                            "price":50.0,"commission":1.0,"totalFees":0.5,
                            "tradeDate":"2026-06-16T00:00:00","canceled":false,"tradeId":987654}]}""",
                    )
                else -> json("""{"orders":[]}""")
            }
        }

        val date = LocalDate.parse("2026-06-16")
        val result = client.fetchOrders(portfolioId = 7L, from = date, to = date)

        val success = assertIs<TradeZeroFetchResult.Success>(result)
        assertEquals(1, success.transactions.size)
        assertEquals("tz:AAPL:2026-06-16T00:00:00:BUY:100.0#0", success.transactions[0].externalId)
    }

    @Test
    fun `the same fill from the API and the CSV import share an externalId`() = runTest {
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") ->
                    json("""{"accounts":[{"account":"ACC1"}]}""")
                request.url.encodedPath.contains("/orders/start-date/") ->
                    json(
                        """{"orders":[{"symbol":"AAPL","securityType":"Stock","side":"Buy","qty":100,
                            "price":50.0,"commission":1.0,"totalFees":0.5,
                            "tradeDate":"2026-06-16T00:00:00","canceled":false,"tradeId":987654}]}""",
                    )
                else -> json("""{"orders":[]}""")
            }
        }
        val date = LocalDate.parse("2026-06-16")
        val apiTx = assertIs<TradeZeroFetchResult.Success>(client.fetchOrders(7L, date, date)).transactions.single()

        // The equivalent fill as a TradeHistory CSV row.
        val csv = (
            "Account,T/D,S/D,Currency,Type,Side,Symbol,Qty,Price,Exec Time,Comm,SEC,TAF,NSCC,Nasdaq,ECN Remove,ECN Add,Gross Proceeds,Net Proceeds,Clr Broker,Liq,Note\n" +
                "ACC1,06/16/2026,06/17/2026,USD,2,B,AAPL,100,50.0,00:00:00,1.0,0.5,0,0,0,0,0,-5000,-5001,LAMP,,"
            ).encodeToByteArray()
        val csvTx = io.earlisreal.ejournal.domain.parser.TradeZeroCsvParser().parse(csv, portfolioId = 7L).single()

        assertEquals(apiTx.externalId, csvTx.externalId)
    }

    @Test
    fun `combines tradeDate with execTime into an intraday datetime`() = runTest {
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") ->
                    json("""{"accounts":[{"account":"ACC1"}]}""")
                request.url.encodedPath.contains("/orders/start-date/") ->
                    json(
                        """{"orders":[{"symbol":"AAPL","securityType":"Stock","side":"Buy","qty":100,
                            "price":50.0,"commission":1.0,"totalFees":0.5,
                            "tradeDate":"2026-06-17T00:00:00","execTime":"06:11:33","canceled":false,"tradeId":1}]}""",
                    )
                else -> json("""{"orders":[]}""")
            }
        }

        val date = LocalDate.parse("2026-06-17")
        val result = client.fetchOrders(portfolioId = 7L, from = date, to = date)

        val success = assertIs<TradeZeroFetchResult.Success>(result)
        assertEquals(LocalDateTime.parse("2026-06-17T06:11:33"), success.transactions[0].datetime)
    }

    @Test
    fun `empty accounts list yields a clear no-accounts error, not a parse error`() = runTest {
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") -> json("""{"accounts":[]}""")
                else -> json("""{"orders":[]}""")
            }
        }

        val date = LocalDate.parse("2026-06-16")
        val result = client.fetchOrders(portfolioId = 7L, from = date, to = date)

        val error = assertIs<TradeZeroFetchResult.NetworkError>(result)
        assertTrue(error.message.contains("no accounts", ignoreCase = true), "was: ${error.message}")
        assertFalse(error.message.contains("parse", ignoreCase = true), "was: ${error.message}")
    }

    @Test
    fun `retries accounts lookup, then succeeds when a later attempt returns an account`() = runTest {
        var accountsCalls = 0
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") -> {
                    accountsCalls++
                    if (accountsCalls < 3) json("""{"accounts":[]}""")
                    else json("""{"accounts":[{"account":"ACC1"}]}""")
                }
                request.url.encodedPath.contains("/orders/start-date/") ->
                    json(
                        """{"orders":[{"symbol":"AAPL","securityType":"Stock","side":"Buy","qty":100,
                            "price":50.0,"commission":1.0,"totalFees":0.5,
                            "tradeDate":"2026-06-16T00:00:00","canceled":false,"tradeId":987654}]}""",
                    )
                else -> json("""{"orders":[]}""")
            }
        }

        val date = LocalDate.parse("2026-06-16")
        val result = client.fetchOrders(portfolioId = 7L, from = date, to = date)

        val success = assertIs<TradeZeroFetchResult.Success>(result)
        assertEquals(1, success.transactions.size)
        assertEquals(3, accountsCalls)
    }
}
