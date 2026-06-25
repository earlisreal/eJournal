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
import kotlinx.datetime.minus
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

    /** A single fill row as the orders-with-pagination endpoint returns it. */
    private fun fill(
        symbol: String = "AAPL",
        side: String = "Buy",
        qty: Int = 100,
        price: Double = 50.0,
        commission: Double = 1.0,
        totalFees: Double = 0.5,
        securityType: String = "Stock",
        tradeDate: String = "2026-06-16T00:00:00",
        execTime: String? = null,
        canceled: Boolean = false,
        tradeId: Long = 987654,
    ): String {
        val exec = execTime?.let { ""","execTime":"$it"""" } ?: ""
        return """{"symbol":"$symbol","securityType":"$securityType","side":"$side","qty":$qty,
            "price":$price,"commission":$commission,"totalFees":$totalFees,
            "tradeDate":"$tradeDate","canceled":$canceled,"tradeId":$tradeId$exec}"""
    }

    /** The paginated response envelope. */
    private fun page(totalRecords: Int, rows: List<String>): String =
        """{"pagination":{"currentLimit":100,"currentOffset":0,"specifiedSymbol":null,
            "totalRecords":$totalRecords},"tradingHistory":[${rows.joinToString(",")}]}"""

    private fun MockRequestHandleScope.json(body: String): HttpResponseData =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun client(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): TradeZeroClientImpl =
        TradeZeroClientImpl(HttpClient(MockEngine { request -> handler(request) }), FakeCredentials())

    private fun HttpRequestData.isOrders() =
        url.encodedPath.contains("/orders-with-pagination/start-date/")

    @Test
    fun `builds a natural-key externalId independent of tradeId`() = runTest {
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") -> json("""{"accounts":[{"account":"ACC1"}]}""")
                request.isOrders() -> json(page(1, listOf(fill())))
                else -> json(page(0, emptyList()))
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
                request.url.encodedPath.endsWith("/accounts") -> json("""{"accounts":[{"account":"ACC1"}]}""")
                request.isOrders() -> json(page(1, listOf(fill())))
                else -> json(page(0, emptyList()))
            }
        }
        val date = LocalDate.parse("2026-06-16")
        val apiTx = assertIs<TradeZeroFetchResult.Success>(client.fetchOrders(7L, date, date)).transactions.single()

        // The equivalent fill as a TradeHistory CSV row.
        val csv = (
            "Account,T/D,S/D,Currency,Type,Side,Symbol,Qty,Price,Exec Time,Comm,SEC,TAF,NSCC,Nasdaq,ECN Remove,ECN Add,Gross Proceeds,Net Proceeds,Clr Broker,Liq,Note\n" +
                "ACC1,06/16/2026,06/17/2026,USD,2,B,AAPL,100,50.0,00:00:00,1.0,0.5,0,0,0,0,0,-5000,-5001,LAMP,,"
            ).encodeToByteArray()
        val csvTx = io.earlisreal.ejournal.domain.parser.TradeZeroCsvParser().parse(csv, portfolioId = 7L).transactions.single()

        assertEquals(apiTx.externalId, csvTx.externalId)
    }

    @Test
    fun `combines tradeDate with execTime into an intraday datetime`() = runTest {
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") -> json("""{"accounts":[{"account":"ACC1"}]}""")
                request.isOrders() ->
                    json(page(1, listOf(fill(tradeDate = "2026-06-17T00:00:00", execTime = "06:11:33", tradeId = 1))))
                else -> json(page(0, emptyList()))
            }
        }

        val date = LocalDate.parse("2026-06-17")
        val result = client.fetchOrders(portfolioId = 7L, from = date, to = date)

        val success = assertIs<TradeZeroFetchResult.Success>(result)
        assertEquals(LocalDateTime.parse("2026-06-17T06:11:33"), success.transactions[0].datetime)
    }

    @Test
    fun `aggregates fills across pages by advancing the offset`() = runTest {
        val requestedOffsets = mutableListOf<Int>()
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") -> json("""{"accounts":[{"account":"ACC1"}]}""")
                request.isOrders() -> {
                    val offset = request.url.parameters["offset"]?.toInt() ?: 0
                    requestedOffsets += offset
                    // totalRecords spans two pages (101 > limit of 100); return a distinct fill per page.
                    val symbol = if (offset == 0) "AAPL" else "MSFT"
                    json(page(totalRecords = 101, rows = listOf(fill(symbol = symbol, tradeId = offset.toLong()))))
                }
                else -> json(page(0, emptyList()))
            }
        }

        val date = LocalDate.parse("2026-06-16")
        val result = client.fetchOrders(portfolioId = 7L, from = date.minus(30), to = date)

        val success = assertIs<TradeZeroFetchResult.Success>(result)
        assertEquals(setOf("AAPL", "MSFT"), success.transactions.map { it.symbol }.toSet())
        assertEquals(listOf(0, 100), requestedOffsets.sorted())
    }

    @Test
    fun `derives startDate and numberOfDays from the requested range`() = runTest {
        var startDateSeg: String? = null
        var numberOfDays: String? = null
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") -> json("""{"accounts":[{"account":"ACC1"}]}""")
                request.isOrders() -> {
                    startDateSeg = request.url.segments.last { it.isNotEmpty() }
                    numberOfDays = request.url.parameters["numberOfDays"]
                    json(page(0, emptyList()))
                }
                else -> json(page(0, emptyList()))
            }
        }

        client.fetchOrders(7L, from = LocalDate.parse("2025-06-23"), to = LocalDate.parse("2026-06-23"))

        assertEquals("2025-06-23", startDateSeg)
        assertEquals("365", numberOfDays)
    }

    @Test
    fun `clamps the window to one year keeping the end date`() = runTest {
        var startDateSeg: String? = null
        var numberOfDays: String? = null
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") -> json("""{"accounts":[{"account":"ACC1"}]}""")
                request.isOrders() -> {
                    startDateSeg = request.url.segments.last { it.isNotEmpty() }
                    numberOfDays = request.url.parameters["numberOfDays"]
                    json(page(0, emptyList()))
                }
                else -> json(page(0, emptyList()))
            }
        }

        // from is well over a year before to: window must start at to-365, not at from.
        client.fetchOrders(7L, from = LocalDate.parse("2024-01-01"), to = LocalDate.parse("2026-06-23"))

        assertEquals("2025-06-23", startDateSeg)
        assertEquals("365", numberOfDays)
    }

    @Test
    fun `empty accounts list yields a clear no-accounts error, not a parse error`() = runTest {
        val client = client { request ->
            when {
                request.url.encodedPath.endsWith("/accounts") -> json("""{"accounts":[]}""")
                else -> json(page(0, emptyList()))
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
                request.isOrders() -> json(page(1, listOf(fill())))
                else -> json(page(0, emptyList()))
            }
        }

        val date = LocalDate.parse("2026-06-16")
        val result = client.fetchOrders(portfolioId = 7L, from = date, to = date)

        val success = assertIs<TradeZeroFetchResult.Success>(result)
        assertEquals(1, success.transactions.size)
        assertEquals(3, accountsCalls)
    }
}

private fun LocalDate.minus(days: Int): LocalDate =
    minus(days, kotlinx.datetime.DateTimeUnit.DAY)
