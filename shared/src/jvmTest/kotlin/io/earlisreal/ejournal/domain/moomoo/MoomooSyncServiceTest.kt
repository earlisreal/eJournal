package io.earlisreal.ejournal.domain.moomoo

import io.earlisreal.ejournal.background.BackgroundTaskTracker
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.broker.BrokerSyncOutcome
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.testutil.FakePortfolioRepository
import io.earlisreal.ejournal.testutil.FakePortfolioSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeMoomooSession : MoomooSession {
    var accounts: MoomooResult<List<MoomooAccount>> = MoomooResult.Success(listOf(eligibleAccount()))
    var orders: List<MoomooOrder> = emptyList()
    var executions: List<MoomooExecution> = emptyList()
    var fees: (List<String>) -> MoomooResult<List<MoomooOrderFee>> = { ids ->
        MoomooResult.Success(ids.map { MoomooOrderFee(it, 0.0) })
    }
    var failOrderCall: Int? = null
    var executionFailure: Throwable? = null
    var orderCall = 0
    val orderWindows = mutableListOf<MoomooWindow>()
    val feeBatchSizes = mutableListOf<Int>()
    var closed = false

    override suspend fun getAccounts() = accounts

    override suspend fun getHistoricalOrders(accountId: String, from: LocalDate, to: LocalDate): MoomooResult<List<MoomooOrder>> {
        orderCall++
        orderWindows += MoomooWindow(from, to)
        if (orderCall == failOrderCall) return MoomooResult.Failure("window failed")
        return MoomooResult.Success(orders.filter { order ->
            order.createdAt?.date?.let { it in from..to } == true
        })
    }

    override suspend fun getHistoricalExecutions(accountId: String, from: LocalDate, to: LocalDate): MoomooResult<List<MoomooExecution>> {
        executionFailure?.let { throw it }
        return MoomooResult.Success(executions.filter { execution ->
            execution.executedAt?.date?.let { it in from..to } == true
        })
    }

    override suspend fun getOrderFees(accountId: String, orderIds: List<String>): MoomooResult<List<MoomooOrderFee>> {
        feeBatchSizes += orderIds.size
        return fees(orderIds)
    }

    override fun close() { closed = true }
}

private class FakeMoomooClient(private val session: FakeMoomooSession) : MoomooClient {
    var openCount = 0
    var lastPort: Int? = null
    var openFailure: String? = null
    override suspend fun open(port: Int): MoomooResult<MoomooSession> {
        openCount++
        lastPort = port
        openFailure?.let { return MoomooResult.Failure(it) }
        return MoomooResult.Success(session)
    }
}

private class DeduplicatingMoomooTransactions : TransactionRepository {
    val inserted = mutableListOf<Transaction>()
    private val externalIds = mutableSetOf<String>()

    override suspend fun getByPortfolio(portfolioId: Long) = inserted.filter { it.portfolioId == portfolioId }
    override suspend fun getByPortfolioAndDateRange(portfolioId: Long, from: LocalDateTime, to: LocalDateTime) =
        getByPortfolio(portfolioId)
    override suspend fun insert(transaction: Transaction): Long? {
        transaction.externalId?.let { if (!externalIds.add(it)) return null }
        inserted += transaction
        return inserted.size.toLong()
    }
    override suspend fun delete(id: Long) = Unit
    override suspend fun countByPortfolio(portfolioId: Long) = inserted.count { it.portfolioId == portfolioId }.toLong()
    override suspend fun deleteByPortfolio(portfolioId: Long) { inserted.removeAll { it.portfolioId == portfolioId } }
}

class MoomooSyncServiceTest {
    private val portfolio = Portfolio(1L, "Moomoo", Market.US_STOCKS, Broker.MOOMOO, "unused")
    private val today = LocalDate(2018, 1, 2)

    private suspend fun configuredSettings() = FakePortfolioSettingsRepository().also {
        it.putMoomooConfig(1L, MoomooPortfolioConfig(12345, "1001", "••1001", "Moomoo Financial"))
    }

    private fun service(
        client: MoomooClient,
        settings: FakePortfolioSettingsRepository,
        transactions: TransactionRepository = DeduplicatingMoomooTransactions(),
        portfolios: FakePortfolioRepository = FakePortfolioRepository(listOf(portfolio)),
        tracker: BackgroundTaskTracker = BackgroundTaskTracker(),
        today: LocalDate = this.today,
    ) = MoomooSyncService(
        client,
        transactions,
        tracker,
        portfolios,
        settings,
        today = { today },
        requestGateFactory = { MoomooRequestGate { } },
    )

    @Test
    fun aggregatesExecutionsUsesEarliestTimeAndExactFee() = runTest {
        val session = FakeMoomooSession().apply {
            orders = listOf(order(id = "o1", symbol = "US.AAPL", quantity = 3.0))
            executions = listOf(
                execution("o1", "US.AAPL", 2.0, 11.0, "2018-01-02T09:31:00"),
                execution("o1", "US.AAPL", 1.0, 10.0, "2018-01-02T09:30:00"),
            )
            fees = { MoomooResult.Success(listOf(MoomooOrderFee("o1", 1.23))) }
        }
        val client = FakeMoomooClient(session)
        val repo = DeduplicatingMoomooTransactions()

        val result = service(client, configuredSettings(), repo).syncIncremental(1L)

        assertEquals(BrokerSyncOutcome.Imported(1), result)
        val transaction = repo.inserted.single()
        assertEquals("AAPL", transaction.symbol)
        assertEquals(Action.BUY, transaction.action)
        assertEquals(3.0, transaction.shares)
        assertEquals(32.0 / 3.0, transaction.price)
        assertEquals(1.23, transaction.fees)
        assertEquals(LocalDateTime.parse("2018-01-02T09:30:00"), transaction.datetime)
        assertEquals("moomoo:AAPL:2018-01-02T08:00:00:BUY:3.0", transaction.externalId)
        assertEquals(12345, client.lastPort)
    }

    @Test
    fun executionQuantityMustMatchCanonicalOrderFill() = runTest {
        val session = FakeMoomooSession().apply {
            orders = listOf(order(quantity = 2.0))
            executions = listOf(execution(quantity = 1.0))
        }
        val repo = DeduplicatingMoomooTransactions()

        val result = assertIs<BrokerSyncOutcome.Imported>(
            service(FakeMoomooClient(session), configuredSettings(), repo).syncIncremental(1L),
        )

        assertEquals(0, result.inserted)
        assertEquals(1, result.detail.skipped["malformed rows"])
        assertTrue(repo.inserted.isEmpty())
        assertTrue(session.feeBatchSizes.isEmpty())
    }

    @Test
    fun floatingPointExecutionSumUsesCanonicalOrderFill() = runTest {
        val session = FakeMoomooSession().apply {
            orders = listOf(order(quantity = 0.3))
            executions = listOf(
                execution(quantity = 0.1, price = 10.0),
                execution(quantity = 0.2, price = 20.0),
            )
        }
        val repo = DeduplicatingMoomooTransactions()

        assertEquals(
            1,
            assertIs<BrokerSyncOutcome.Imported>(
                service(FakeMoomooClient(session), configuredSettings(), repo).syncIncremental(1L),
            ).inserted,
        )

        val transaction = repo.inserted.single()
        assertEquals(0.3, transaction.shares)
        assertEquals("moomoo:AAPL:2018-01-02T08:00:00:BUY:0.3", transaction.externalId)
    }

    @Test
    fun detectsMoomooOptionsWithoutRejectingStocksOrEtfs() {
        val skipped = mutableMapOf<String, Int>()
        val mapped = MoomooSyncService.mapOrders(
            orders = listOf(
                order("put", "US.AAPL261218P200000"),
                order("call", "US.JPM260320C267500"),
                order("stock", "US.BRK.B"),
                order("etf", "US.SPY"),
            ),
            executions = listOf(
                execution("stock", "US.BRK.B"),
                execution("etf", "US.SPY"),
            ),
            portfolioId = 1L,
            skipped = skipped,
        )

        assertEquals(listOf("BRK.B", "SPY"), mapped.map { it.symbol })
        assertEquals(2, skipped["options"])
    }

    @Test
    fun missingRequiredFeeFailsWindowWithoutAdvancingPastFailedWindow() = runTest {
        val session = FakeMoomooSession().apply {
            orders = listOf(order())
            executions = listOf(execution())
            fees = { MoomooResult.Success(emptyList()) }
        }
        val settings = configuredSettings()
        val repo = DeduplicatingMoomooTransactions()

        val result = service(FakeMoomooClient(session), settings, repo).syncIncremental(1L)

        assertIs<BrokerSyncOutcome.NetworkError>(result)
        assertTrue(repo.inserted.isEmpty())
        assertEquals("2017-12-27", settings.getString(1L, MoomooSettings.LAST_COMPLETED_DATE))
    }

    @Test
    fun completedWindowsCheckpointAndFailureResumesWithThreeDayOverlap() = runTest {
        val session = FakeMoomooSession().apply { failOrderCall = 2 }
        val settings = configuredSettings()
        val service = service(
            FakeMoomooClient(session),
            settings,
            today = LocalDate(2018, 7, 1),
        )

        assertIs<BrokerSyncOutcome.NetworkError>(service.syncIncremental(1L))
        assertEquals("2017-09-28", settings.getString(1L, MoomooSettings.LAST_COMPLETED_DATE))
        assertEquals(MoomooSettings.source("1001"), settings.getString(1L, MoomooSettings.LAST_SYNCED_SOURCE))

        session.failOrderCall = null
        session.orderCall = 0
        session.orderWindows.clear()
        assertIs<BrokerSyncOutcome.Imported>(service.syncIncremental(1L))
        assertEquals(LocalDate(2017, 9, 25), session.orderWindows.first().from)
        assertEquals(LocalDate(2018, 7, 1), session.orderWindows.last().to)
    }

    @Test
    fun feeRequestsAreBatchedAtFourHundredIds() = runTest {
        val session = FakeMoomooSession().apply {
            orders = (1..401).map { index -> order("o$index", "S$index", 1.0) }
            executions = (1..401).map { index -> execution("o$index", "S$index") }
        }

        service(FakeMoomooClient(session), configuredSettings()).syncIncremental(1L)

        assertEquals(listOf(400, 1), session.feeBatchSizes)
    }

    @Test
    fun reportsUnsupportedOrdersAndMalformedRows() = runTest {
        val orders = listOf(
            order("option", "AAPL250117C00100000"),
            order("combo", "AAPL").copy(isCombo = true),
            order("prediction", "US.EC.ELECTION").copy(isPrediction = true),
            order("market", "AAPL").copy(market = MoomooMarket.OTHER),
            order("side", "AAPL").copy(side = MoomooSide.SELL_SHORT),
            order("zero", "AAPL", 0.0),
            order("", "AAPL"),
        )
        val session = FakeMoomooSession().apply { this.orders = orders }

        val result = assertIs<BrokerSyncOutcome.Imported>(
            service(FakeMoomooClient(session), configuredSettings()).syncIncremental(1L),
        )

        assertEquals(0, result.inserted)
        assertEquals(1, result.detail.skipped["options"])
        assertEquals(1, result.detail.skipped["combo orders"])
        assertEquals(1, result.detail.skipped["prediction contracts"])
        assertEquals(1, result.detail.skipped["other markets"])
        assertEquals(1, result.detail.skipped["unsupported sides"])
        assertEquals(1, result.detail.skipped["zero fills"])
        assertEquals(1, result.detail.skipped["malformed rows"])
    }

    @Test
    fun repeatSyncIsIdempotent() = runTest {
        val session = FakeMoomooSession().apply {
            orders = listOf(order())
            executions = listOf(execution())
        }
        val repo = DeduplicatingMoomooTransactions()
        val service = service(FakeMoomooClient(session), configuredSettings(), repo)

        assertEquals(1, assertIs<BrokerSyncOutcome.Imported>(service.syncIncremental(1L)).inserted)
        assertEquals(0, assertIs<BrokerSyncOutcome.Imported>(service.syncIncremental(1L)).inserted)
        assertEquals(1, repo.inserted.size)
    }

    @Test
    fun accountBindingConflictStopsBeforeConnecting() = runTest {
        val settings = configuredSettings()
        settings.putString(2L, MoomooSettings.ACCOUNT_SOURCE, MoomooSettings.source("1001"))
        val client = FakeMoomooClient(FakeMoomooSession())
        val portfolios = FakePortfolioRepository(
            listOf(portfolio, Portfolio(2L, "Already linked", Market.US_STOCKS, Broker.MOOMOO, "unused-2")),
        )

        val result = service(client, settings, portfolios = portfolios).syncIncremental(1L)

        assertEquals(BrokerSyncOutcome.AccountAlreadyBound("Already linked"), result)
        assertEquals(0, client.openCount)
    }

    @Test
    fun openFailureDoesNotInsertOrAdvanceCursor() = runTest {
        val settings = configuredSettings()
        val repo = DeduplicatingMoomooTransactions()
        val client = FakeMoomooClient(FakeMoomooSession()).apply { openFailure = "OpenD unavailable" }

        val result = service(client, settings, repo).syncIncremental(1L)

        assertIs<BrokerSyncOutcome.NetworkError>(result)
        assertTrue(repo.inserted.isEmpty())
        assertNull(settings.getString(1L, MoomooSettings.LAST_COMPLETED_DATE))
        assertNull(settings.getString(1L, MoomooSettings.LAST_SYNCED_SOURCE))
    }

    @Test
    fun unavailableSelectedAccountDoesNotInsertOrAdvanceCursor() = runTest {
        val settings = configuredSettings()
        val repo = DeduplicatingMoomooTransactions()
        val session = FakeMoomooSession().apply { accounts = MoomooResult.Success(emptyList()) }

        val result = service(FakeMoomooClient(session), settings, repo).syncIncremental(1L)

        assertIs<BrokerSyncOutcome.NetworkError>(result)
        assertTrue(repo.inserted.isEmpty())
        assertNull(settings.getString(1L, MoomooSettings.LAST_COMPLETED_DATE))
        assertNull(settings.getString(1L, MoomooSettings.LAST_SYNCED_SOURCE))
        assertTrue(session.closed)
    }

    @Test
    fun cancellationClosesSessionAndRemovesTask() = runTest {
        val session = FakeMoomooSession().apply {
            executionFailure = CancellationException("cancelled")
        }
        val tracker = BackgroundTaskTracker()

        assertFailsWith<CancellationException> {
            service(FakeMoomooClient(session), configuredSettings(), tracker = tracker).syncIncremental(1L)
        }

        assertTrue(session.closed)
        assertTrue(tracker.tasks.value.none { it.id == MoomooSyncService.TASK_ID })
    }
}

private fun eligibleAccount() = MoomooAccount(
    id = "1001",
    label = "••1001",
    securityFirm = "Moomoo Financial",
    environment = MoomooAccountEnvironment.REAL,
    role = MoomooAccountRole.NORMAL,
    authorizedMarkets = setOf(MoomooMarket.US),
    active = true,
)

private fun order(
    id: String = "o1",
    symbol: String = "AAPL",
    quantity: Double = 1.0,
) = MoomooOrder(
    id = id,
    symbol = symbol,
    side = MoomooSide.BUY,
    createdAt = LocalDateTime.parse("2018-01-02T08:00:00"),
    filledQuantity = quantity,
    market = MoomooMarket.US,
)

private fun execution(
    orderId: String = "o1",
    symbol: String = "AAPL",
    quantity: Double = 1.0,
    price: Double = 10.0,
    at: String = "2018-01-02T09:30:00",
) = MoomooExecution(
    orderId = orderId,
    symbol = symbol,
    side = MoomooSide.BUY,
    quantity = quantity,
    price = price,
    executedAt = LocalDateTime.parse(at),
    market = MoomooMarket.US,
)
