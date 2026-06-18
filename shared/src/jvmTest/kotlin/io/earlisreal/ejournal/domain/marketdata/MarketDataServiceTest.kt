package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.BarCoverage
import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.TradeZeroCredentials
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val TODAY = LocalDate.parse("2026-06-12")

private class FakePortfolios(private val portfolios: List<Portfolio>) : PortfolioRepository {
    override suspend fun getAll(): List<Portfolio> = portfolios
    override suspend fun getById(id: Long): Portfolio? = portfolios.firstOrNull { it.id == id }
    override suspend fun insert(name: String, market: Market): Long = error("unused")
    override suspend fun update(id: Long, name: String, market: Market) = error("unused")
    override suspend fun delete(id: Long) = error("unused")
}

private class FakeTransactions(private val bySymbolPortfolio: Map<Long, List<Transaction>>) : TransactionRepository {
    override suspend fun getByPortfolio(portfolioId: Long): List<Transaction> = bySymbolPortfolio[portfolioId].orEmpty()
    override suspend fun getByPortfolioAndDateRange(portfolioId: Long, from: LocalDateTime, to: LocalDateTime) = error("unused")
    override suspend fun insert(transaction: Transaction): Long = error("unused")
    override suspend fun delete(id: Long) = error("unused")
    override suspend fun countByPortfolio(portfolioId: Long): Long = error("unused")
    override suspend fun deleteByPortfolio(portfolioId: Long) = error("unused")
}

private class FakeBars : MarketDataRepository {
    val stored = mutableListOf<Bar>()
    val coverage = mutableMapOf<Pair<String, Timeframe>, BarCoverage>()
    override suspend fun upsertBars(bars: List<Bar>) { stored.addAll(bars) }
    override suspend fun getCoverage(symbol: String, timeframe: Timeframe): BarCoverage? = coverage[symbol to timeframe]
    override suspend fun getBars(symbol: String, timeframe: Timeframe, from: LocalDateTime, to: LocalDateTime) = error("unused")
}

private class FakeProvider : MarketDataProvider {
    val calls = mutableListOf<BarRange>()
    var attempts = 0
    val failures = ArrayDeque<Exception>()
    // Per-symbol failures take priority over the global queue.
    val symbolFailures = mutableMapOf<String, ArrayDeque<Exception>>()
    // Per-symbol+timeframe failures take priority over symbolFailures.
    val symbolTimeframeFailures = mutableMapOf<Pair<String, Timeframe>, ArrayDeque<Exception>>()
    override suspend fun getBars(symbol: String, timeframe: Timeframe, from: LocalDate, to: LocalDate): List<Bar> {
        attempts++
        symbolTimeframeFailures[symbol to timeframe]?.removeFirstOrNull()?.let { throw it }
        symbolFailures[symbol]?.removeFirstOrNull()?.let { throw it }
        failures.removeFirstOrNull()?.let { throw it }
        calls.add(BarRange(symbol, timeframe, from, to))
        return listOf(Bar(symbol, timeframe, from.atTime9_30(), 1.0, 2.0, 0.5, 1.5, 100L))
    }
    private fun LocalDate.atTime9_30() = LocalDateTime.parse("${this}T09:30")
}

private class FakeCreds(var creds: AlpacaCredentials? = null) : CredentialsRepository {
    override fun getAlpacaCredentials(): AlpacaCredentials? = creds
    override fun setAlpacaCredentials(credentials: AlpacaCredentials) { creds = credentials }
    override fun getTradeZeroCredentials(): TradeZeroCredentials? = null
    override fun setTradeZeroCredentials(credentials: TradeZeroCredentials) {}
}

class MarketDataServiceTest {

    private fun usPortfolio(id: Long = 1L) = Portfolio(id = id, name = "US", market = Market.US_STOCKS)

    private fun tx(portfolioId: Long, symbol: String, action: Action, datetime: String) = Transaction(
        id = 0L, portfolioId = portfolioId, symbol = symbol,
        datetime = LocalDateTime.parse(datetime), action = action,
        price = 100.0, shares = 10.0, fees = 1.0,
    )

    /** A recent same-day round trip: needs 1-min bars inside the Yahoo window. */
    private fun recentDayTrade(portfolioId: Long = 1L, symbol: String = "AAPL") = listOf(
        tx(portfolioId, symbol, Action.BUY, "2026-06-10T09:31"),
        tx(portfolioId, symbol, Action.SELL, "2026-06-10T10:15"),
    )

    /** A same-day round trip months ago: 1-min bars only Alpaca can serve. */
    private fun oldDayTrade(portfolioId: Long = 1L, symbol: String = "AAPL") = listOf(
        tx(portfolioId, symbol, Action.BUY, "2026-01-05T09:31"),
        tx(portfolioId, symbol, Action.SELL, "2026-01-05T10:15"),
    )

    private fun service(
        portfolios: List<Portfolio> = listOf(usPortfolio()),
        transactions: Map<Long, List<Transaction>> = mapOf(1L to recentDayTrade()),
        bars: FakeBars = FakeBars(),
        yahoo: FakeProvider = FakeProvider(),
        alpaca: FakeProvider = FakeProvider(),
        creds: FakeCreds = FakeCreds(),
    ) = MarketDataService(
        portfolioRepository = FakePortfolios(portfolios),
        transactionRepository = FakeTransactions(transactions),
        marketDataRepository = bars,
        yahooProvider = yahoo,
        alpacaProvider = alpaca,
        credentialsRepository = creds,
        todayProvider = { TODAY },
    )

    @Test
    fun `fetches and stores one-minute bars for a day trade via alpaca`() = runTest {
        val bars = FakeBars()
        val yahoo = FakeProvider()
        val alpaca = FakeProvider()
        val result = service(bars = bars, yahoo = yahoo, alpaca = alpaca, creds = FakeCreds(AlpacaCredentials("id", "secret"))).sync()

        assertTrue(alpaca.calls.any { it == BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-09"), LocalDate.parse("2026-06-11")) })
        assertTrue(yahoo.calls.any { it.symbol == "AAPL" && it.timeframe == Timeframe.DAILY })
        assertTrue(yahoo.calls.none { it.timeframe == Timeframe.ONE_MINUTE })
        assertEquals(2, bars.stored.size)
        assertEquals(SyncResult(fetchedSymbols = 1, failedSymbols = emptyList(), keysRejected = false, needsKeys = false), result)
    }

    @Test
    fun `skips fetching when coverage is complete`() = runTest {
        val bars = FakeBars()
        bars.coverage["AAPL" to Timeframe.ONE_MINUTE] =
            BarCoverage(LocalDateTime.parse("2026-06-09T04:00"), LocalDateTime.parse("2026-06-11T19:59"))
        bars.coverage["AAPL" to Timeframe.DAILY] =
            BarCoverage(LocalDateTime.parse("2026-04-11T00:00"), LocalDateTime.parse("2026-06-12T23:59"))
        val yahoo = FakeProvider()
        val result = service(bars = bars, yahoo = yahoo).sync()

        assertTrue(yahoo.calls.isEmpty())
        assertEquals(0, result.fetchedSymbols)
    }

    @Test
    fun `old day trade without keys is reported as needing keys and alpaca is not called`() = runTest {
        val alpaca = FakeProvider()
        val result = service(transactions = mapOf(1L to oldDayTrade()), alpaca = alpaca).sync()

        assertTrue(alpaca.calls.isEmpty())
        assertTrue(result.needsKeys)
    }

    @Test
    fun `old day trade with keys fetches via alpaca`() = runTest {
        val alpaca = FakeProvider()
        val result = service(
            transactions = mapOf(1L to oldDayTrade()),
            alpaca = alpaca,
            creds = FakeCreds(AlpacaCredentials("id", "secret")),
        ).sync()

        assertEquals(1, alpaca.calls.size)
        assertTrue(!result.needsKeys)
    }

    @Test
    fun `rejected keys abort alpaca fetches but yahoo daily fetches continue`() = runTest {
        val yahoo = FakeProvider()
        val alpaca = FakeProvider().apply { failures.addLast(InvalidKeysException("rejected")) }
        // All three symbols need Alpaca for 1-min; daily routes to Yahoo for all
        val result = service(
            transactions = mapOf(1L to oldDayTrade(symbol = "AAPL") + oldDayTrade(symbol = "TSLA") + recentDayTrade(symbol = "NVDA")),
            yahoo = yahoo,
            alpaca = alpaca,
            creds = FakeCreds(AlpacaCredentials("id", "secret")),
        ).sync()

        assertTrue(result.keysRejected)
        // Daily bars for all symbols still route to Yahoo regardless of key status
        assertTrue(yahoo.calls.any { it.symbol == "AAPL" && it.timeframe == Timeframe.DAILY })
        assertTrue(yahoo.calls.any { it.symbol == "TSLA" && it.timeframe == Timeframe.DAILY })
        assertTrue(yahoo.calls.any { it.symbol == "NVDA" && it.timeframe == Timeframe.DAILY })
        // 1-min never routes to Yahoo
        assertTrue(yahoo.calls.none { it.timeframe == Timeframe.ONE_MINUTE })
    }

    @Test
    fun `transient failure retries once and succeeds`() = runTest {
        val yahoo = FakeProvider().apply { failures.addLast(TransientFetchException("blip")) }
        val alpaca = FakeProvider()
        val result = service(yahoo = yahoo, alpaca = alpaca, creds = FakeCreds(AlpacaCredentials("id", "secret"))).sync()

        assertEquals(1, yahoo.calls.size)   // daily: retry succeeds
        assertEquals(1, alpaca.calls.size)  // 1-min: first try succeeds
        assertTrue(result.failedSymbols.isEmpty())
    }

    @Test
    fun `repeated transient failure marks the symbol failed but others continue`() = runTest {
        // Fail both ranges (ONE_MINUTE + DAILY) for AAPL so neither succeeds.
        val yahoo = FakeProvider().apply {
            symbolTimeframeFailures["AAPL" to Timeframe.ONE_MINUTE] = ArrayDeque(listOf(
                TransientFetchException("down"), TransientFetchException("still down"),
            ))
            symbolTimeframeFailures["AAPL" to Timeframe.DAILY] = ArrayDeque(listOf(
                TransientFetchException("down"), TransientFetchException("still down"),
            ))
        }
        val result = service(
            transactions = mapOf(1L to recentDayTrade(symbol = "AAPL") + recentDayTrade(symbol = "TSLA")),
            yahoo = yahoo,
        ).sync()

        assertEquals(listOf("AAPL"), result.failedSymbols)
        assertTrue(yahoo.calls.none { it.symbol == "AAPL" })
        assertTrue(yahoo.calls.any { it.symbol == "TSLA" })
    }

    @Test
    fun `unknown symbol is skipped without failing the run`() = runTest {
        // Both ranges (ONE_MINUTE + DAILY) for AAPL must get SymbolNotFound.
        val yahoo = FakeProvider().apply {
            symbolFailures["AAPL"] = ArrayDeque(listOf(
                SymbolNotFoundException("AAPL"), SymbolNotFoundException("AAPL"),
            ))
        }
        val result = service(
            transactions = mapOf(1L to recentDayTrade(symbol = "AAPL") + recentDayTrade(symbol = "TSLA")),
            yahoo = yahoo,
        ).sync()

        assertTrue(result.failedSymbols.isEmpty())
        assertTrue(yahoo.calls.none { it.symbol == "AAPL" })
        assertTrue(yahoo.calls.any { it.symbol == "TSLA" })
    }

    @Test
    fun `transient failure on one range does not prevent other ranges for the same symbol from being fetched`() = runTest {
        // ONE_MINUTE is processed first; failing it permanently must not block DAILY.
        val yahoo = FakeProvider().apply {
            symbolTimeframeFailures["AAPL" to Timeframe.ONE_MINUTE] = ArrayDeque(listOf(
                TransientFetchException("down"),
                TransientFetchException("still down"),
            ))
        }
        service(yahoo = yahoo).sync()

        assertTrue(yahoo.calls.any { it.symbol == "AAPL" && it.timeframe == Timeframe.DAILY })
    }

    @Test
    fun `non-US portfolios are not synced`() = runTest {
        val yahoo = FakeProvider()
        val result = service(
            portfolios = listOf(Portfolio(id = 1L, name = "PH", market = Market.PH_STOCKS)),
            transactions = mapOf(1L to recentDayTrade()),
            yahoo = yahoo,
        ).sync()

        assertTrue(yahoo.calls.isEmpty())
        assertEquals(0, result.fetchedSymbols)
    }
}
