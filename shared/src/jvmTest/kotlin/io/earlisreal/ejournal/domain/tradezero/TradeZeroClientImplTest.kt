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
import kotlin.test.assertIs

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
    fun `maps tradeId to namespaced externalId`() = runTest {
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
        assertEquals("tz:987654", success.transactions[0].externalId)
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
}
