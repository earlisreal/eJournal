package io.earlisreal.ejournal.domain.marketdata

import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.BarCoverage
import io.earlisreal.ejournal.data.repository.CredentialsRepository
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
    var failures = ArrayDeque<Exception>()
    override suspend fun getBars(symbol: String, timeframe: Timeframe, from: LocalDate, to: LocalDate): List<Bar> {
        attempts++
        failures.removeFirstOrNull()?.let { throw it }
        calls.add(BarRange(symbol, timeframe, from, to))
        return listOf(Bar(symbol, timeframe, from.atTime9_30(), 1.0, 2.0, 0.5, 1.5, 100L))
    }
    private fun LocalDate.atTime9_30() = LocalDateTime.parse("${this}T09:30")
}

private class FakeCreds(var creds: AlpacaCredentials? = null) : CredentialsRepository {
    override fun getAlpacaCredentials(): AlpacaCredentials? = creds
    override fun setAlpacaCredentials(credentials: AlpacaCredentials) { creds = credentials }
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
    fun `fetches and stores one-minute bars for a recent day trade via yahoo`() = runTest {
        val bars = FakeBars()
        val yahoo = FakeProvider()
        val result = service(bars = bars, yahoo = yahoo).sync()

        assertEquals(listOf(BarRange("AAPL", Timeframe.ONE_MINUTE, LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-10"))), yahoo.calls)
        assertEquals(1, bars.stored.size)
        assertEquals(SyncResult(fetchedSymbols = 1, failedSymbols = emptyList(), keysRejected = false, needsKeys = false), result)
    }

    @Test
    fun `skips fetching when coverage is complete`() = runTest {
        val bars = FakeBars()
        bars.coverage["AAPL" to Timeframe.ONE_MINUTE] =
            BarCoverage(LocalDateTime.parse("2026-06-10T04:00"), LocalDateTime.parse("2026-06-10T19:59"))
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
    fun `rejected keys abort alpaca fetches but yahoo fetches continue`() = runTest {
        val yahoo = FakeProvider()
        val alpaca = FakeProvider().apply { failures.addLast(InvalidKeysException("rejected")) }
        // Two symbols needing Alpaca + one needing Yahoo
        val result = service(
            transactions = mapOf(1L to oldDayTrade(symbol = "AAPL") + oldDayTrade(symbol = "TSLA") + recentDayTrade(symbol = "NVDA")),
            yahoo = yahoo,
            alpaca = alpaca,
            creds = FakeCreds(AlpacaCredentials("id", "secret")),
        ).sync()

        assertTrue(result.keysRejected)
        assertEquals(1, alpaca.attempts) // first call threw, second symbol never attempted
        assertEquals(listOf("NVDA"), yahoo.calls.map { it.symbol })
    }

    @Test
    fun `transient failure retries once and succeeds`() = runTest {
        val yahoo = FakeProvider().apply { failures.addLast(TransientFetchException("blip")) }
        val result = service(yahoo = yahoo).sync()

        assertEquals(1, yahoo.calls.size)
        assertTrue(result.failedSymbols.isEmpty())
    }

    @Test
    fun `repeated transient failure marks the symbol failed but others continue`() = runTest {
        val yahoo = FakeProvider().apply {
            failures.addLast(TransientFetchException("down"))
            failures.addLast(TransientFetchException("still down"))
        }
        val result = service(
            transactions = mapOf(1L to recentDayTrade(symbol = "AAPL") + recentDayTrade(symbol = "TSLA")),
            yahoo = yahoo,
        ).sync()

        assertEquals(listOf("AAPL"), result.failedSymbols)
        assertEquals(listOf("TSLA"), yahoo.calls.map { it.symbol })
    }

    @Test
    fun `unknown symbol is skipped without failing the run`() = runTest {
        val yahoo = FakeProvider().apply { failures.addLast(SymbolNotFoundException("AAPL")) }
        val result = service(
            transactions = mapOf(1L to recentDayTrade(symbol = "AAPL") + recentDayTrade(symbol = "TSLA")),
            yahoo = yahoo,
        ).sync()

        assertTrue(result.failedSymbols.isEmpty())
        assertEquals(listOf("TSLA"), yahoo.calls.map { it.symbol })
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
